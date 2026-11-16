package com.runiverse.running_service.unit_test.infrastructure.kafka;

import com.runiverse.running_service.infrastructure.kafka.KafkaNamingProperties;
import com.runiverse.running_service.infrastructure.kafka.KafkaNamingProperties.Env;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Kafka 이름 환경 접두어 설정 단위 테스트")
class KafkaNamingPropertiesTest {

    @ParameterizedTest
    @CsvSource({"dev, DEV", "DEV, DEV", "test, TEST", "Prod, PROD", "dev., DEV", "p-r-o-d, PROD"})
    @DisplayName("KAFKA_ENV는 대소문자·구두점을 가리지 않고 환경으로 바인딩된다 — 결과는 늘 세 환경 중 하나다")
    void bindsIgnoringCase(String raw, Env expected) {
        // when
        KafkaNamingProperties properties = bind(raw);

        // then
        assertThat(properties.env()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deploy", "local", "staging", "develop", "test1"})
    @DisplayName("dev·test·prod가 아니면 바인딩이 실패해 기동하지 않는다")
    void rejectsUnknownEnv(String raw) {
        // when & then -> 환경 이름이 틀리면 다른 환경 토픽으로 새지 않게 아예 뜨지 않는다
        assertThatThrownBy(() -> bind(raw)).isInstanceOf(BindException.class);
    }

    @Test
    @DisplayName("KAFKA_ENV가 없으면 검증에 걸린다")
    void requiresEnv() {
        // given
        KafkaNamingProperties properties = new KafkaNamingProperties(null);

        // when & then
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).hasSize(1);
        }
    }

    @ParameterizedTest
    @CsvSource({"DEV, dev.record-card.music.requested", "TEST, test.record-card.music.requested",
            "PROD, prod.record-card.music.requested"})
    @DisplayName("토픽 이름 앞에 소문자 환경과 점을 붙인다")
    void prefixesTopic(Env env, String expected) {
        // when & then -> 9-5 이름 규칙: 소문자, 구분자는 . 과 -
        assertThat(new KafkaNamingProperties(env).topic("record-card.music.requested")).isEqualTo(expected);
    }

    private static KafkaNamingProperties bind(String env) {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("kafka.env", env)));
        return binder.bindOrCreate("kafka", KafkaNamingProperties.class);
    }
}
