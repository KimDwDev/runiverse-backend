package com.runiverse.running_service.application.scheduling.command.schedule;

import com.runiverse.running_service.domain.scheduling.ScheduledJob;

// 끝나지 않은 작업을 같은 예약 행으로 다시 걸었다 — 새로 만든 예약처럼 커밋 뒤 타이머를 건다
public record ScheduledJobRescheduledEvent(ScheduledJob job) {

}
