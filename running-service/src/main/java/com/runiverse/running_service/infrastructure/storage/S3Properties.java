package com.runiverse.running_service.infrastructure.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "storage.s3")
@Validated
public record S3Properties(
        @NotBlank String region,
        @NotBlank String bucket,
        @NotNull Duration presignedUrlTtl,
        String accessKeyId,
        String secretAccessKey
) {

}
