package com.runiverse.running_service.application.recordcard.common;

import com.runiverse.running_service.application.common.ImageContentType;

import java.util.UUID;

// 기록 카드 S3 key 규칙 — 사진·음악 모두 작성자 폴더(record-cards/{userId}/) 아래에 둔다.
// key는 서버만 만든다 — 앱이 key를 보내는 경로가 없어 소유자 대조가 필요 없다(초안이 key를 들고 있다)
public final class RecordCardStorageKeys {

    private static final String ROOT = "record-cards";

    private RecordCardStorageKeys() {
    }

    // 끝의 /까지 포함해야 userId 뒤에 다른 문자가 붙은 key가 섞이지 않는다 — 탈퇴 시 지우는 범위도 이 폴더다
    public static String prefixOf(UUID userId) {
        return "%s/%s/".formatted(ROOT, userId);
    }

    public static String imageKey(UUID userId, UUID imageId, ImageContentType contentType) {
        return "%simages/%s.%s".formatted(prefixOf(userId), imageId, contentType.getExtension());
    }
}
