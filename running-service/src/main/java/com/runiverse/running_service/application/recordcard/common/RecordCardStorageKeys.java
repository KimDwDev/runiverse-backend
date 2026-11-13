package com.runiverse.running_service.application.recordcard.common;

import com.runiverse.running_service.application.common.ImageContentType;

import java.util.UUID;

// 기록 카드 S3 key 규칙 — 작성자 폴더 아래 카드마다, 사진마다 폴더를 둔다:
// record-cards/{userId}/{recordCardId}/{recordCardImageId}/image.{확장자}
// key는 서버만 만든다 — 앱이 key를 보내는 경로가 없어 소유자 대조가 필요 없다(초안이 key를 들고 있다)
public final class RecordCardStorageKeys {

    private static final String ROOT = "record-cards";
    // 사진 폴더 안의 파일 이름 — 사진 ID가 폴더라 이름으로 가를 필요가 없다
    private static final String IMAGE_FILE_NAME = "image";

    private RecordCardStorageKeys() {
    }

    // 끝의 /까지 포함해야 userId 뒤에 다른 문자가 붙은 key가 섞이지 않는다 — 탈퇴 시 지우는 범위도 이 폴더다
    public static String prefixOf(UUID userId) {
        return "%s/%s/".formatted(ROOT, userId);
    }

    public static String imageKey(UUID userId, UUID recordCardId, UUID recordCardImageId,
                                  ImageContentType contentType) {
        return "%s%s/%s/%s.%s".formatted(prefixOf(userId), recordCardId, recordCardImageId,
                IMAGE_FILE_NAME, contentType.getExtension());
    }
}
