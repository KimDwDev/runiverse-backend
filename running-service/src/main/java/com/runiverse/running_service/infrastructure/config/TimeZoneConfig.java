package com.runiverse.running_service.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

// 저장 시각의 기준을 실행 환경과 무관하게 고정한다
@Configuration
public class TimeZoneConfig {

    private final ZoneId zoneId;

    // 오타는 GMT로 삼켜지므로 ZoneId로 검증한다
    public TimeZoneConfig(@Value("${APP_TIME_ZONE}") String timeZone) {
        this.zoneId = ZoneId.of(timeZone);
    }

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
    }
}
