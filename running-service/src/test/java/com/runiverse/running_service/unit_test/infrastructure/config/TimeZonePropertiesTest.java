package com.runiverse.running_service.unit_test.infrastructure.config;

import com.runiverse.running_service.infrastructure.config.TimeZoneProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("앱 타임존 설정 단위 테스트")
class TimeZonePropertiesTest {

    @Test
    @DisplayName("지역 이름을 ZoneId로 바인딩한다")
    void bindsZoneId() {
        // when
        TimeZoneProperties properties = bind("Asia/Seoul");

        // then
        assertThat(properties.timeZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("없는 지역 이름이면 바인딩에서 실패한다")
    void rejectsUnknownZone() {
        // when & then -> TimeZone.getTimeZone은 오타를 GMT로 삼키므로 여기서 막아야 한다
        assertThatThrownBy(() -> bind("Asia/Seul"))
                .isInstanceOf(BindException.class);
    }

    private static TimeZoneProperties bind(String timeZone) {
        return new Binder(new MapConfigurationPropertySource(Map.of("app.time-zone", timeZone)))
                .bind("app", TimeZoneProperties.class)
                .get();
    }
}
