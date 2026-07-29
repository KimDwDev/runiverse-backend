package com.runiverse.running_service.infrastructure.security.jwt.config;

import com.runiverse.running_service.infrastructure.security.jwt.JwtProperties;
import com.runiverse.running_service.infrastructure.security.jwt.key.JwtSecretKeyFactory;
import com.runiverse.running_service.infrastructure.security.jwt.validator.AudienceValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtDecoderConfig {
    @Bean
    public JwtDecoder accessTokenDecoder(JwtProperties properties) {
        return decoder(properties.accessToken().secret(), properties);
    }
    @Bean
    public JwtDecoder refreshTokenDecoder(JwtProperties properties) {
        return decoder(properties.refreshToken().secret(), properties);
    }
    private JwtDecoder decoder(String secret, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(JwtSecretKeyFactory.create(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(properties.issuer()),
                new AudienceValidator(properties.audience())
        ));
        return decoder;
    }
}
