package com.runiverse.running_service.presentation.recordcard.response;

import com.runiverse.running_service.application.recordcard.common.RouteArtConverter;
import com.runiverse.running_service.application.recordcard.query.detail.GetRecordCardDetailResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 8-1 기록 카드 상세 — api-spec 「기록 카드 공통 객체」. 경로는 위경도가 아닌 선 아트(0~1)다
public record RecordCardDetailResponse(
        UUID recordCardId,
        Author author,
        boolean isMine,
        String visibility,
        String activityType,
        String memo,
        Record record,
        Music baseMusic,
        List<Image> images,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static RecordCardDetailResponse from(GetRecordCardDetailResult result) {
        GetRecordCardDetailResult.Record record = result.record();
        return new RecordCardDetailResponse(
                result.recordCardId(),
                new Author(result.author().userId(), result.author().nickname(), result.author().profileImageUrl()),
                result.mine(),
                result.visibility().name(),
                result.activityType().name(),
                result.memo(),
                new Record(record.runningRecordId(), record.startedAt(), record.totalDistanceMeters(),
                        record.totalDurationSeconds(), record.averagePaceSecondsPerKm(), routeArt(record.routeArt())),
                Music.from(result.baseMusic()),
                result.images().stream()
                        .map(image -> new Image(image.recordCardImageId(), image.imageUrl(), image.caption(),
                                image.thumbnail(), Music.from(image.music())))
                        .toList(),
                result.createdAt(),
                result.updatedAt());
    }

    // 점마다 [x, y] 두 칸 배열로 내린다 — 6-1 routes처럼 키 이름을 반복하지 않는다
    private static List<List<double[]>> routeArt(List<List<RouteArtConverter.Point>> segments) {
        return segments.stream()
                .map(segment -> segment.stream()
                        .map(point -> new double[]{point.x(), point.y()})
                        .toList())
                .toList();
    }

    public record Author(UUID userId, String nickname, String profileImageUrl) {

    }

    public record Record(
            Long runningRecordId,
            LocalDateTime startedAt,
            int totalDistanceMeters,
            int totalDurationSeconds,
            int averagePaceSecondsPerKm,
            List<List<double[]>> routeArt
    ) {

    }

    public record Music(String status, String musicUrl) {

        static Music from(GetRecordCardDetailResult.Music music) {
            return new Music(music.status().name(), music.musicUrl());
        }
    }

    public record Image(
            UUID recordCardImageId,
            String imageUrl,
            String caption,
            boolean isThumbnail,
            Music music
    ) {

    }
}
