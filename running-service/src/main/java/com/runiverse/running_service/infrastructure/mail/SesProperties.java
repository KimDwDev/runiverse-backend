package com.runiverse.running_service.infrastructure.mail;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "mail.ses")
@Validated
public record SesProperties(
    @NotBlank String region,
    @NotBlank String from
) {
}
