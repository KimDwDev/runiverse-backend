package com.runiverse.running_service.infrastructure.redis.running;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

// 러닝 방 채널의 대기 줄이 꽉 차면 메시지를 버린다(F7, D-66).
// 진행·콤보는 다음 배치가 덮어써 하나를 잃어도 화면이 잠깐 늦을 뿐이다. 받는 스레드가 대신 처리하면(CallerRuns)
// 받기가 밀려 Redis가 구독 버퍼 한도를 넘긴 구독자를 끊고, 그 서버는 모든 방 메시지를 한꺼번에 잃는다.
// 넘칠 때는 초당 수백 건이라 건마다 로그를 남기면 로그가 먼저 무너진다 — 모아서 남기고, 정확한 총량은 카운터가 센다
@Slf4j
public class RunningRoomMessageDropHandler implements RejectedExecutionHandler {

    public static final String DROPPED = "runiverse.running.room.dropped";
    private static final long LOG_INTERVAL_MILLIS = 10_000;
    private final int queueCapacity;
    private final Counter droppedCounter;
    private final LongSupplier clock;
    // 지난 로그 이후 버린 수와 마지막으로 남긴 시각 — 인스턴스 메모리다. 재시작하면 사라지지만 총량은 카운터에 남는다
    private final AtomicLong droppedSinceLog = new AtomicLong();
    private final AtomicLong lastLoggedAt;

    public RunningRoomMessageDropHandler(int queueCapacity, MeterRegistry meterRegistry) {
        this(queueCapacity, meterRegistry, System::currentTimeMillis);
    }

    // 시각을 바꿔 끼우는 것은 테스트뿐이다
    public RunningRoomMessageDropHandler(int queueCapacity, MeterRegistry meterRegistry, LongSupplier clock) {
        this.queueCapacity = queueCapacity;
        this.droppedCounter = meterRegistry.counter(DROPPED);
        this.clock = clock;
        // 첫 번째로 버린 순간 바로 한 줄 남긴다 — 넘치기 시작한 시각이 보여야 한다
        this.lastLoggedAt = new AtomicLong(clock.getAsLong() - LOG_INTERVAL_MILLIS);
    }

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor pool) {
        droppedCounter.increment();
        droppedSinceLog.incrementAndGet();
        if (pool.isShutdown()) {
            return;   // 서버가 꺼지는 중이다 — 남은 메시지를 버리는 것이 정상이다
        }
        long now = clock.getAsLong();
        long last = lastLoggedAt.get();
        // 여러 스레드가 동시에 와도 한 스레드만 남긴다
        if (now - last >= LOG_INTERVAL_MILLIS && lastLoggedAt.compareAndSet(last, now)) {
            log.warn("[러닝] 방 채널 메시지 버림: 대기 줄 가득 참 - droppedCount={}, queueCapacity={}",
                    droppedSinceLog.getAndSet(0), queueCapacity);
        }
    }
}
