package com.runiverse.running_service.application.match.common;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "match")
@Validated
public record MatchProperties(
        // 모집 마감 = start_at - 이 값. 컬럼에 저장하지 않는다 —
        // 오프셋을 바꾸는 건 곧 정책을 바꾸는 것이라 진행 중인 방만 옛 마감을 유지할 이유가 없다
        @NotNull Duration closeOffset,
        // 시작 통지 = start_at - 이 값. 정각에 보내면 클라의 발사 시점과 겹쳐 늘 늦게 도착한다 —
        // 클라가 타이머를 걸 여유만 주면 되므로 WS 연결 시간까지 덮을 필요는 없다
        @NotNull Duration readyOffset,
        // 페이스 차이가 이 값 이내면 동급으로 보고 leave_count로 순위를 가른다.
        // 후보 자격(±30초)은 Pace.isCloseTo가 판정한다 — 이건 그 안에서의 동점 처리다
        @NotNull @Positive Integer paceTieToleranceSecondsPerKm,
        // 제재 대상 이탈 후 재신청이 막히는 기간. Redis 키의 TTL로 쓴다
        @NotNull Duration cooldown
) {

}
