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

    // 앱은 이 주기의 몇 배 동안 새 값이 없으면 공지를 내린다 — 주기를 늘리면 앱의 기다림도 함께 늘려야 한다.
    // fixedDelay라 한 바퀴가 길어져도 실행이 밀려 쌓이지 않는다
    @Scheduled(fixedDelayString = "${app-stream.home-notice-interval}")
    public void broadcastOnSchedule() {
        // 하나가 실패해도 다른 하나는 보낸다 — 실패한 값은 다음 주기에 다시 계산한다
        try {
            broadcastWaitingCountUsecase.broadcast();
        } catch (RuntimeException e) {
            log.error("[매칭] 대기 인원 조회 실패: 처리하지 못한 예외", e);
        }
        try {
            broadcastRunningActivityUsecase.broadcast();
        } catch (RuntimeException e) {
            log.error("[러닝] 러닝 현황 조회 실패: 처리하지 못한 예외", e);
        }
    }
}
