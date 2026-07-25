package com.runiverse.running_service.infrastructure.redis;

public enum RedisKey {
    USER("user");

    private static final String DELIMITER = ":";
    private final String prefix;
    RedisKey(String prefix) {
        this.prefix = prefix;
    }
    public String of(String ...segments) {
        return prefix + DELIMITER + String.join(DELIMITER, segments);
    }
}
