package com.runiverse.running_service.application.recordcard.port.out;

import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;

import java.util.List;
import java.util.UUID;

// 9-1이 받은 작성 내용 — 확정(9-2) 전까지 Redis에만 둔다. DB에는 사진까지 확인된 카드만 들어간다
public record RecordCardDraft(
        UUID recordCardDraftId,
        UUID userId,                    // 초안을 만든 사람 — 다른 사람이 확정하면 404
        Long runningRecordId,
        RecordCardActivityType activityType,
        String memo,
        RecordCardVisibility visibility,
        List<DraftImage> images,        // 캐러셀 순서대로
        Integer thumbnailIndex,         // 사진이 없으면 null
        Long confirmedRecordCardId      // 확정 전에는 null — 확정 뒤 재시도에 같은 카드를 돌려준다
) {

    public RecordCardDraft {
        images = List.copyOf(images);
    }

    // 올릴 사진 하나 — 확정 때 S3 실물을 여기 적힌 형식·크기와 대조한다
    public record DraftImage(
            String imageKey,
            String contentType,         // 소문자 정규값 — 서명에 넣은 값과 같다
            long fileSizeBytes,
            String caption,             // 설명이 없으면 null
            UploadType uploadType,
            String multipartUploadId,   // MULTIPART일 때만 — S3가 준 UploadId, 앱에는 주지 않는다
            Integer partCount,          // MULTIPART일 때만 — 조각 크기 설정이 바뀌어도 발급한 개수로 확인한다
            boolean merged              // 조각을 합쳤는지 — 다른 사진 때문에 확정이 실패해 다시 부를 때 다시 합치지 않는다
    ) {

    }

    public enum UploadType {
        SINGLE,
        MULTIPART
    }
}
