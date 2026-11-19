package com.runiverse.running_service.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// 다른 서비스의 이벤트 구독 설정 — 구독을 켰을 때만 만든다(로컬·E2E는 브로커에 닿지 않아 끈다).
// 접속 값은 빈 기본값이라(끈 환경도 떠야 해서) 켰는데 비어 있으면 여기서 기동을 막는다 — 조용히 구독하지 않는 일을 막는다.
// 처리 실패: DB 오류는 오프셋을 커밋하지 않고 끝없이 다시 받는다(일시적이다). 업무 예외는 다시 받아도 같아 건너뛴다
@Slf4j
@Configuration
@ConditionalOnBooleanProperty("kafka.consumer.enabled")
public class KafkaConsumerConfig {

    private static final long INITIAL_INTERVAL_MS = 1_000;
    private static final double MULTIPLIER = 2.0;
    // max.poll.interval.ms(기본 5분)보다 짧아야 한다 — 기다리는 동안 그룹에서 빠지지 않게
    private static final long MAX_INTERVAL_MS = 60_000;

    public KafkaConsumerConfig(KafkaProperties kafkaProperties) {
        requireConnection(kafkaProperties);
    }

    // Spring Boot가 이 빈을 리스너 컨테이너에 건다
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        // 횟수 제한 없이 1초부터 두 배씩, 최대 60초 간격으로 다시 받는다 — 완료 결과를 버리지 않는다
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_INTERVAL_MS, MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, e) -> log.error("[공통] Kafka 메시지 처리 건너뜀: 다시 받아도 실패하는 예외 - topic={}, "
                                + "partition={}, offset={}, key={}",
                        record.topic(), record.partition(), record.offset(), record.key(), e),
                backOff);
        // 업무 규칙에 걸린 예외는 다시 받아도 같다 — 계속 다시 받으면 그 파티션이 멈춘다
        handler.addNotRetryableExceptions(
                com.runiverse.running_service.domain.common.exception.BusinessException.class,
                com.runiverse.running_service.application.common.exception.BusinessException.class);
        return handler;
    }

    private static void requireConnection(KafkaProperties properties) {
        List<String> missing = new ArrayList<>();
        if (properties.getBootstrapServers().stream().allMatch(String::isBlank)) {
            missing.add("KAFKA_BOOTSTRAP_SERVERS");
        }
        String jaasConfig = properties.getProperties().getOrDefault("sasl.jaas.config", "");
        if (jaasConfig.contains("username=\"\"")) {
            missing.add("KAFKA_SASL_USERNAME");
        }
        if (jaasConfig.contains("password=\"\"")) {
            missing.add("KAFKA_SASL_PASSWORD");
        }
        String caLocation = properties.getProperties().getOrDefault("ssl.truststore.location", "");
        if (caLocation.isBlank() || !Files.isReadable(Path.of(caLocation))) {
            missing.add("KAFKA_SSL_CA_LOCATION");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("kafka.consumer.enabled=true인데 Kafka 접속 값이 없거나 읽을 수 없습니다: " + missing);
        }
    }
}
