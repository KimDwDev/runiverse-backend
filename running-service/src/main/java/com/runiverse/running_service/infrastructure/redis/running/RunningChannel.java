package com.runiverse.running_service.infrastructure.redis.running;

public final class RunningChannel {

    // 모든 인스턴스가 상시 구독 하는 고정 채널
    public static final String SUPERSEDE = "running:supersede";

    private RunningChannel() {
    }
}
