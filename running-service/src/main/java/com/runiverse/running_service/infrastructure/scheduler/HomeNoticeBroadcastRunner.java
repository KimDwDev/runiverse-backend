package com.runiverse.running_service.infrastructure.scheduler;

import com.runiverse.running_service.application.match.port.in.BroadcastWaitingCountUsecase;
import com.runiverse.running_service.application.running.port.in.BroadcastRunningActivityUsecase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomeNoticeBroadcastRunner {

    private final BroadcastWaitingCountUsecase broadcastWaitingCountUsecase;
    private final BroadcastRunningActivityUsecase broadcastRunningActivityUsecase;

    // fixedDelay라 한 바퀴가 길어져도 실행이 밀려 쌓이지 않는다
    @Scheduled(fixedDelayString = "${app-stream.home-waiting-interval}")
    public void broadcastWaitingCount() {
        // 실패한 값은 다음 주기에 다시 계산한다
        try {
            broadcastWaitingCountUsecase.broadcast();
        } catch (RuntimeException e) {
            log.error("[매칭] 대기 인원 조회 실패: 처리하지 못한 예외", e);
        }
    }

    @Scheduled(fixedDelayString = "${app-stream.home-activity-interval}")
    public void broadcastRunningActivity() {
        try {
            broadcastRunningActivityUsecase.broadcast();
        } catch (RuntimeException e) {
            log.error("[러닝] 러닝 현황 조회 실패: 처리하지 못한 예외", e);
        }
    }
}
