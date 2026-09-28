package com.runiverse.running_service.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "app")
@Validated
public record TimeZoneProperties(
        @NotNull ZoneId timeZone
) {

}
