package com.runiverse.running_service.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final int PUSH_THREADS = 4;
    private static final int PUSH_QUEUE_CAPACITY = 100;
    private static final int PUSH_SHUTDOWN_WAIT_SECONDS = 10;
    private static final int PUSH_CAMPAIGN_THREADS = 1;
    private static final int PUSH_CAMPAIGN_SHUTDOWN_WAIT_SECONDS = 10;
    private static final int FORCE_FINISH_THREADS = 4;
    private static final int FORCE_FINISH_QUEUE_CAPACITY = 100;
    private static final int FORCE_FINISH_SHUTDOWN_WAIT_SECONDS = 20;

    // FCM 응답을 요청·예약 스레드가 기다리지 않게 푸시는 이 스레드들이 보낸다.
    // 발송이 DB·Redis를 읽으므로 그 둘보다 먼저 닫혀야 종료 때 대기 줄을 마저 보낼 수 있다.
    // 이름으로만 쓰이게 둔다 — 실행기를 모두 주입받는 JPA 설정이 이 풀에 매이면 JPA가 먼저 닫힌다
    @Bean(defaultCandidate = false)
    @DependsOn({"entityManagerFactory", "redisConnectionFactory"})
    public ThreadPoolTaskExecutor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(PUSH_THREADS);
        executor.setMaxPoolSize(PUSH_THREADS);
        executor.setQueueCapacity(PUSH_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("push-");
        // 넘치면 버린다 — 호출한 스레드가 대신 보내면 요청·예약 스레드가 다시 FCM을 기다린다
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                log.warn("[기기] 푸시 발송 건너뜀: 종료 중");
                return;
            }
            log.warn("[기기] 푸시 발송 건너뜀: 발송 대기 줄 가득 참 - queueCapacity={}", PUSH_QUEUE_CAPACITY);
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(PUSH_SHUTDOWN_WAIT_SECONDS);
        return executor;
    }

    // 대기방 채팅 푸시는 따로 보낸다 — 채팅이 몰려도 매칭 확정·출발 알림의 대기 줄을 밀어내지 않는다.
    // 종료 순서와 이름으로만 쓰는 이유는 pushExecutor와 같다
    @Bean(defaultCandidate = false)
    @DependsOn({"entityManagerFactory", "redisConnectionFactory"})
    public ThreadPoolTaskExecutor chatPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor() {
            // 컨텍스트가 닫히기 시작하면 바로 끊고 남은 채팅 푸시는 버린다 —
            // 기다리면 Redis가 멈춘 뒤에 돌아 남은 건마다 오류를 낸다
            @Override
            protected void initiateEarlyShutdown() {
                getThreadPoolExecutor().shutdownNow();
            }
        };
        executor.setCorePoolSize(PUSH_THREADS);
        executor.setMaxPoolSize(PUSH_THREADS);
        executor.setQueueCapacity(PUSH_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("chat-push-");
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                log.warn("[기기] 채팅 푸시 발송 건너뜀: 종료 중");
                return;
            }
            log.warn("[기기] 채팅 푸시 발송 건너뜀: 발송 대기 줄 가득 참 - queueCapacity={}", PUSH_QUEUE_CAPACITY);
        });
        // 이 값이 false여야 위의 조기 종료가 불린다
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(PUSH_SHUTDOWN_WAIT_SECONDS);
        return executor;
    }

    // 공지 캠페인은 받는 사람을 한 명씩 이 스레드가 보낸다 — 수천 명을 기다리는 동안 다른 푸시를 막지 않게 따로 둔다.
    // 종료 순서와 이름으로만 쓰는 이유는 pushExecutor와 같다
    @Bean(defaultCandidate = false)
    @DependsOn({"entityManagerFactory", "redisConnectionFactory"})
    public ThreadPoolTaskExecutor pushCampaignExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor() {
            // 컨텍스트가 닫히기 시작하면 바로 끊는다 — 기본 동작은 끊지 않고 진행 중인 발송을 기다리다,
            // Redis가 멈춘 뒤에야 끊어 남은 사람마다 오류를 낸다.
            // 대기 줄에서 꺼낸 작업은 취소해 발송 작업이 DRAFT로 되돌리게 한다 — 종료 단계라 DB는 아직 열려 있다
            @Override
            protected void initiateEarlyShutdown() {
                for (Runnable remaining : getThreadPoolExecutor().shutdownNow()) {
                    cancelRemainingTask(remaining);
                }
            }
        };
        executor.setCorePoolSize(PUSH_CAMPAIGN_THREADS);
        executor.setMaxPoolSize(PUSH_CAMPAIGN_THREADS);
        // 대기 줄에 한도를 두지 않는다 — 운영이 부를 때만 들어오는 일감이라 쌓일 일이 없고, 거절은 서버가 꺼질 때만 생긴다
        executor.setThreadNamePrefix("push-campaign-");
        // 끝날 때까지 기다리지 않는다 — 수천 명이면 몇 분이 걸려 기다려도 다 보내지 못하고, 배포가 그 전에 컨테이너를 죽인다.
        // 이 값이 false여야 위의 조기 종료가 불린다. 끊긴 발송은 남은 인원을 남기고 멈춘다
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(PUSH_CAMPAIGN_SHUTDOWN_WAIT_SECONDS);
        return executor;
    }

    // 강제 종료에서 뛰던 사람을 한 명씩 끝낸다(D-83) — 봉인(S3·날씨)을 예약 스레드가 기다리지 않게 하고,
    // 예약 트랜잭션이 커밋된 뒤 깨끗한 스레드에서 판정·확정 트랜잭션을 연다.
    // 방 하나는 최대 4명이라 스레드 수가 곧 동시에 끝내는 러너 수의 상한이다. 종료 순서와 이름으로만 쓰는 이유는 pushExecutor와 같다
    @Bean(defaultCandidate = false)
    @DependsOn({"entityManagerFactory", "redisConnectionFactory"})
    public ThreadPoolTaskExecutor forceFinishExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(FORCE_FINISH_THREADS);
        executor.setMaxPoolSize(FORCE_FINISH_THREADS);
        executor.setQueueCapacity(FORCE_FINISH_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("force-finish-");
        // 넘치면 버린다 — 강제 종료 예약이 1분 뒤로 다시 걸려 있어 다음 알람이 다시 넣는다
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                log.warn("[러닝] 강제 종료 러너 처리 건너뜀: 종료 중");
                return;
            }
            log.warn("[러닝] 강제 종료 러너 처리 건너뜀: 대기 줄 가득 참 - queueCapacity={}",
                    FORCE_FINISH_QUEUE_CAPACITY);
        });
        // 배포 때 진행 중인 끝내기는 마치고 내린다 — 못 마친 러너는 다음 알람이 다른 인스턴스에서 다시 한다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(FORCE_FINISH_SHUTDOWN_WAIT_SECONDS);
        return executor;
    }
}
