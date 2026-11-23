package com.runiverse.running_service.application.recordcard.query.detail;

import com.runiverse.running_service.application.recordcard.common.RouteArtConverter;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 8-1 기록 카드 상세 — URL은 모두 presigned GET으로 바꾼 값이다
public record GetRecordCardDetailResult(
        UUID recordCardId,
        Author author,
        boolean mine,
        RecordCardVisibility visibility,
        RecordCardActivityType activityType,
        String memo,
        Record record,
        Music baseMusic,
        List<Image> images,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public record Author(UUID userId, String nickname, String profileImageUrl) {

    }

    public record Record(
            Long runningRecordId,
            LocalDateTime startedAt,
            int totalDistanceMeters,
            int totalDurationSeconds,
            int averagePaceSecondsPerKm,
            List<List<RouteArtConverter.Point>> routeArt
    ) {

    }

    // 음악 한 곡 — COMPLETED일 때만 URL이 있다
    public record Music(MusicStatus status, String musicUrl) {

    }

    public record Image(
            UUID recordCardImageId,
            String imageUrl,
            String caption,
            boolean thumbnail,
            Music music
    ) {

    }
}
