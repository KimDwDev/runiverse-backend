package com.runiverse.running_service.infrastructure.oauth.google.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleUserResponse(
        // 구글 계정마다 고정된 식별자다 (카카오의 id에 해당)
        String sub,
        String email,
        @JsonProperty("email_verified") Boolean emailVerified
) {
}
