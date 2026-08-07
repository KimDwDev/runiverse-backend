package com.runiverse.running_service.infrastructure.oauth.google.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken
) {
}
