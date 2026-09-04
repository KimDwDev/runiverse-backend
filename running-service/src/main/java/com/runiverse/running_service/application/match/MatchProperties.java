package com.runiverse.running_service.application.match;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "match")
@Validated
public record MatchProperties(
        // 모집 마감 = start_at - 이 값. 컬럼에 저장하지 않는다 —
        // 오프셋을 바꾸는 건 곧 정책을 바꾸는 것이라 진행 중인 방만 옛 마감을 유지할 이유가 없다
        @NotNull Duration closeOffset
) {

}
