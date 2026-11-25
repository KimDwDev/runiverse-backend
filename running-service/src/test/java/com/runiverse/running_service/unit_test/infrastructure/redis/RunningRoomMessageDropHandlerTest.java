package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.runiverse.running_service.infrastructure.redis.running.RunningRoomMessageDropHandler;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.support.LogCapture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("러닝 방 채널 메시지 버림 단위 테스트")
class RunningRoomMessageDropHandlerTest {

    private static final int QUEUE_CAPACITY = 1;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AtomicLong now = new AtomicLong(1_000_000);

    // 스레드 하나를 붙잡아 두고 대기 줄 하나를 채운다 — 그다음부터는 넘친다
    private ThreadPoolTaskExecutor saturatedExecutor(CountDownLatch release) throws InterruptedException {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setRejectedExecutionHandler(
                new RunningRoomMessageDropHandler(QUEUE_CAPACITY, registry, now::get));
        executor.initialize();
        CountDownLatch started = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            awaitQuietly(release);
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        executor.execute(() -> { });   // 대기 줄을 채운다
        return executor;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private double dropped() {
        return registry.get(RunningRoomMessageDropHandler.DROPPED).counter().count();
    }

    @Test
    @DisplayName("대기 줄이 꽉 차면 던지지 않고 버리며 버린 수를 센다")
    void dropsWithoutThrowing() throws InterruptedException {
        // given
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolTaskExecutor executor = saturatedExecutor(release);

        // when & then -> 던지면 Redis 수신 스레드로 올라가 구독이 흔들린다
        assertThatCode(() -> {
            executor.execute(() -> { });
            executor.execute(() -> { });
            executor.execute(() -> { });
        }).doesNotThrowAnyException();
        assertThat(dropped()).isEqualTo(3.0);
        release.countDown();
        executor.shutdown();
    }

    @Test
    @DisplayName("처음 버릴 때 바로 한 줄, 그 뒤로는 10초에 한 줄만 모아서 남긴다")
    void logsAggregatedOncePerInterval() throws InterruptedException {
        // given
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolTaskExecutor executor = saturatedExecutor(release);
        LogCapture log = LogCapture.of(RunningRoomMessageDropHandler.class);

        // when -> 첫 번째로 버림, 9초 동안 4건 더, 10초가 지나 1건
        executor.execute(() -> { });
        now.addAndGet(1_000);
        for (int i = 0; i < 4; i++) {
            executor.execute(() -> { });
        }
        now.addAndGet(9_000);
        executor.execute(() -> { });

        // then -> 건마다 남기면 넘칠 때 초당 수백 줄이 된다
        assertThat(log.messages(Level.WARN)).containsExactly(
                "[러닝] 방 채널 메시지 버림: 대기 줄 가득 참 - droppedCount=1, queueCapacity=1",
                "[러닝] 방 채널 메시지 버림: 대기 줄 가득 참 - droppedCount=5, queueCapacity=1");
        // 총량은 로그와 상관없이 카운터에 다 남는다
        assertThat(dropped()).isEqualTo(6.0);
        release.countDown();
        executor.shutdown();
        log.stop();
    }
}
