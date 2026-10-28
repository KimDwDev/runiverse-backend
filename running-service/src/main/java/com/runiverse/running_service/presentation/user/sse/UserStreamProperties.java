package com.runiverse.running_service.presentation.user.sse;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "user-stream")
@Validated
public record UserStreamProperties(
        @NotNull Duration timeout
) {

}
