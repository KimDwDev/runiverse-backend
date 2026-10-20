package com.runiverse.running_service.application.user.command.backgroundimage;

import com.runiverse.running_service.application.user.common.ImageContentType;
import com.runiverse.running_service.domain.user.vo.BackgroundImageKey;

import java.util.UUID;

public final class BackgroundImageKeyPolicy {

    private static final String KEY_PREFIX = "backgrounds";

    private BackgroundImageKeyPolicy() {
    }

    public static BackgroundImageKey create(UUID userId, UUID imageId, ImageContentType contentType) {
        return new BackgroundImageKey("%s%s.%s".formatted(prefixOf(userId), imageId, contentType.getExtension()));
    }

    public static boolean isOwnedBy(String key, UUID userId) {
        return key.startsWith(prefixOf(userId));
    }

    public static String prefixOf(UUID userId) {
        return "%s/%s/".formatted(KEY_PREFIX, userId);
    }
}
