package com.runiverse.running_service.infrastructure.redis.running;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "running-room-listener")
@Validated
public record RunningRoomListenerProperties(
        // 러닝 방 채널 메시지를 동시에 처리하는 스레드 수 — 같은 서버의 방 사람(최대 4명)에게 WS로 보내는 짧은 일이다
        @NotNull @Positive Integer threads,
        // 스레드가 모두 바쁠 때 기다리는 메시지 수 — 끝내기 몰림 같은 순간 몰림을 몇 초 흡수한다
        @NotNull @Positive Integer queueCapacity
) {

}
