package com.runiverse.running_service.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

// 저장 시각의 기준을 실행 환경과 무관하게 고정한다
@Configuration
@RequiredArgsConstructor
public class TimeZoneConfig {

    // 오타는 GMT로 삼켜지므로 ZoneId로 받는다 — 바인딩 단계에서 없는 지역이면 기동이 멈춘다
    private final TimeZoneProperties properties;

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(properties.timeZone()));
    }
}
