package com.runiverse.running_service.application.running.command.forcefinish;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "running-force-finish")
@Validated
public record ForceFinishProperties(
        // 뛰던 사람이 남았을 때 강제 종료 예약을 다시 거는 간격
        @NotNull Duration retryInterval,
        // 강제 종료 시각부터 다시 걸기를 멈출 때까지 — 트랙 보관 기간(running-track.ttl)에 맞춘다.
        // 그 뒤로는 트랙이 없어 상태만 확정되므로 계속 실패하는 것은 다시 해도 풀리지 않는 오류다
        @NotNull Duration retryWindow
) {

}
