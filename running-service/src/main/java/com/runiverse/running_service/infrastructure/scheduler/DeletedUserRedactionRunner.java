package com.runiverse.running_service.infrastructure.scheduler;

import com.runiverse.running_service.application.user.port.in.RedactDeletedUsersUsecase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeletedUserRedactionRunner {

    private final RedactDeletedUsersUsecase redactDeletedUsersUsecase;

    // TimeZoneConfig가 기본값을 바꾸기 전에 트리거가 만들어질 수 있어 존을 명시한다
    @Scheduled(cron = "${account-deletion.redaction-cron}", zone = "${APP_TIME_ZONE}")
    public void redactOnSchedule() {
        try {
            redactDeletedUsersUsecase.redactAfterRetention();
        } catch (RuntimeException e) {
            // 배치가 죽어도 앱은 계속 돈다 — 못 지운 건은 다음 실행에 다시 걸린다
            log.error("탈퇴 기록 신원 정보 제거 배치 실패", e);
        }
    }
}
