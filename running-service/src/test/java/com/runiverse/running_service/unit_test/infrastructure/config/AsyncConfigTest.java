package com.runiverse.running_service.unit_test.infrastructure.config;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.infrastructure.config.AsyncConfig;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("푸시 발송 스레드 풀 단위 테스트")
class AsyncConfigTest {

    private static final int THREADS = 4;
    private static final int QUEUE_CAPACITY = 100;

    private final CountDownLatch release = new CountDownLatch(1);
    private ThreadPoolTaskExecutor executor;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        log = LogCapture.of(AsyncConfig.class);
        executor = new AsyncConfig().pushExecutor();
        executor.initialize();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        executor.shutdown();
        log.stop();
    }

    @Test
    @DisplayName("스레드와 대기 줄이 다 차면 던지지도 대신 보내지도 않고 버린 뒤 WARN을 남긴다")
    void dropsWhenQueueIsFull() throws InterruptedException {
        // given -> 스레드 4개가 FCM 응답을 기다리고 대기 줄 100개가 찼다
        CountDownLatch started = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; i++) {
            executor.execute(() -> {
                started.countDown();
                awaitRelease();
            });
        }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        for (int i = 0; i < QUEUE_CAPACITY; i++) {
            executor.execute(this::awaitRelease);
        }
        AtomicBoolean ran = new AtomicBoolean(false);

        // when & then -> 던지면 커밋 뒤 리스너를 부른 요청·예약 스레드로 예외가 올라간다
        assertThatCode(() -> executor.execute(() -> ran.set(true))).doesNotThrowAnyException();
        assertThat(log.messages(Level.WARN)).singleElement().asString()
                .contains("[기기] 푸시 발송 건너뜀: 발송 대기 줄 가득 참");
        release.countDown();
        executor.shutdown();
        assertThat(ran).isFalse();
    }

    @Test
    @DisplayName("종료가 시작된 뒤 들어온 발송은 대기 줄이 차서가 아니라 종료 중이라 버렸다고 남긴다")
    void dropsAfterShutdownWithReason() {
        // given -> 종료 순서상 예약 스레드가 풀보다 늦게 닫혀 그사이 리마인더가 들어올 수 있다
        executor.shutdown();

        // when
        executor.execute(() -> { });

        // then
        assertThat(log.messages(Level.WARN)).singleElement().asString()
                .contains("[기기] 푸시 발송 건너뜀: 종료 중");
    }

    @Test
    @DisplayName("컨텍스트가 닫히기 시작하면 진행 중인 공지 발송을 Redis가 멈추기 전에 끊는다")
    void campaignExecutorInterruptsBeforeRedisStops() throws InterruptedException {
        // given -> 수천 명을 보내는 캠페인이 아직 돌고 있다. Redis 연결은 Lettuce처럼 단계 0에서 멈춘다
        AtomicBoolean redisStopped = new AtomicBoolean(false);
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("pushCampaignExecutor", ThreadPoolTaskExecutor.class,
                () -> new AsyncConfig().pushCampaignExecutor());
        context.registerBean("redisConnectionFactory", SmartLifecycle.class,
                () -> new StoppingAtPhaseZero(redisStopped));
        context.refresh();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean interruptedBeforeRedisStopped = new AtomicBoolean(false);
        context.getBean(ThreadPoolTaskExecutor.class).execute(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interruptedBeforeRedisStopped.set(!redisStopped.get());
                interrupted.countDown();
            }
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        // when
        context.close();

        // then -> 기다리다 늦게 끊으면 Redis가 멈춘 뒤 남은 사람마다 발송을 시도한다
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(interruptedBeforeRedisStopped).isTrue();
    }

    // Lettuce 연결 팩토리처럼 실행기보다 늦은 단계 0에서 멈춘다
    private static final class StoppingAtPhaseZero implements SmartLifecycle {

        private final AtomicBoolean stopped;
        private volatile boolean running;

        private StoppingAtPhaseZero(AtomicBoolean stopped) {
            this.stopped = stopped;
        }

        @Override
        public void start() {
            running = true;
        }

        @Override
        public void stop() {
            stopped.set(true);
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return 0;
        }
    }

    private void awaitRelease() {
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
