package com.runiverse.running_service.infrastructure.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OauthRestClientConfig {
    @Bean
    public RestClient kakaoRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
