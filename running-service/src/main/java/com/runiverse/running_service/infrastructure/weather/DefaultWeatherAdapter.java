package com.runiverse.running_service.infrastructure.weather;

import com.runiverse.running_service.application.running.port.out.LoadWeatherPort;
import com.runiverse.running_service.application.running.port.out.Weather;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 아직 외부 날씨 API를 붙이지 않았다. 명세가 요구하는 폴백 경로만 먼저 만든다 —
// "조회에 실패해도 종료 처리를 막지 않고 기본값을 넣는다"(feature-spec §2).
// 실제 조회를 붙일 때 이 클래스를 대체하면 포트와 호출자는 그대로 둘 수 있다
@Component
@RequiredArgsConstructor
public class DefaultWeatherAdapter implements LoadWeatherPort {

    private final WeatherProperties properties;

    @Override
    public Weather load(double latitude, double longitude, LocalDateTime at) {
        return new Weather(properties.defaultCode(), properties.defaultTemperature());
    }
}
