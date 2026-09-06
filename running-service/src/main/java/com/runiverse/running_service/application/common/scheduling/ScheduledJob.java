package com.runiverse.running_service.application.common.scheduling;

import java.time.LocalDateTime;

// 대상은 타입마다 가리키는 테이블이 달라 문자열로 둔다 — MATCH_CLOSE면 running_room_id다
public record ScheduledJob(Long scheduledJobId, ScheduledJobType type,
                           String targetId, LocalDateTime executeAt) {

    public Long targetIdAsLong() {
        return Long.valueOf(targetId);
    }
}
