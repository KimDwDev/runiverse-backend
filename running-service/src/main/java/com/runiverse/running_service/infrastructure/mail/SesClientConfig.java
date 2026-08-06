package com.runiverse.running_service.infrastructure.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.time.Duration;

@Configuration
public class SesClientConfig {
    @Bean
    public SesV2Client sesV2Client(SesProperties properties) {
        return SesV2Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5)))
                .build();
    }
}
