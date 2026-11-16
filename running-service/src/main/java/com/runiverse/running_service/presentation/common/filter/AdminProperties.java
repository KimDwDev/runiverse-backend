package com.runiverse.running_service.presentation.common.filter;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "admin")
@Validated
public record AdminProperties(
        // 관리자 API(/admin/**) 전체를 여는 키 — 비면 서버가 뜨지 않는다
        @NotBlank String key
) {

}
