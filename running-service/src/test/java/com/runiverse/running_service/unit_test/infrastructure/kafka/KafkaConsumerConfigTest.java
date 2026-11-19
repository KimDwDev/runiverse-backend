package com.runiverse.running_service.unit_test.infrastructure.kafka;

import com.runiverse.running_service.application.recordcard.exception.RunningRecordNotFoundException;
import com.runiverse.running_service.domain.recordcard.exception.InvalidMusicResultException;
import com.runiverse.running_service.infrastructure.kafka.KafkaConsumerConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("Kafka 구독 설정 단위 테스트")
class KafkaConsumerConfigTest {

    private static final String JAAS = "org.apache.kafka.common.security.scram.ScramLoginModule required "
            + "username=\"%s\" password=\"%s\";";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("접속 값이 모두 있고 CA 파일을 읽을 수 있으면 만들어진다")
    void createsWithConnection() throws IOException {
        // given
        KafkaProperties properties = properties("12.16.16.198:9092", "dev.running-service", "secret", caFile());

        // when & then
        assertThatCode(() -> new KafkaConsumerConfig(properties)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("켰는데 접속 값이 비어 있으면 빠진 변수 이름을 모두 알리며 기동을 막는다 — 비밀번호 값은 싣지 않는다")
    void rejectsMissingConnection() {
        // given
        KafkaProperties properties = properties("", "", "", "");

        // when & then
        assertThatThrownBy(() -> new KafkaConsumerConfig(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAFKA_BOOTSTRAP_SERVERS")
                .hasMessageContaining("KAFKA_SASL_USERNAME")
                .hasMessageContaining("KAFKA_SASL_PASSWORD")
                .hasMessageContaining("KAFKA_SSL_CA_LOCATION");
    }

    @Test
    @DisplayName("CA 경로가 있어도 파일이 없으면 기동을 막는다 — 마운트를 빠뜨린 경우")
    void rejectsUnreadableCa() {
        // given
        KafkaProperties properties = properties("12.16.16.198:9092", "dev.running-service", "secret",
                tempDir.resolve("missing-ca.pem").toString());

        // when & then
        assertThatThrownBy(() -> new KafkaConsumerConfig(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAFKA_SSL_CA_LOCATION")
                .hasMessageNotContaining("KAFKA_BOOTSTRAP_SERVERS")
                .hasMessageNotContaining("secret");
    }

    @Test
    @DisplayName("업무 예외는 다시 받지 않고 바로 건너뛴다 — domain·application 둘 다")
    void skipsBusinessExceptions() throws IOException {
        // given
        DefaultErrorHandler handler = handler();
        ConsumerRecord<?, ?> record = new ConsumerRecord<>("dev.record-card.music.generated", 0, 3L, "key", "{}");

        // when & then — true면 복구(건너뜀) 처리가 끝났다는 뜻이다
        assertThat(handler.handleOne(new InvalidMusicResultException(), record, mock(Consumer.class),
                runningContainer())).isTrue();
        assertThat(handler.handleOne(new RunningRecordNotFoundException(), record, mock(Consumer.class),
                runningContainer())).isTrue();
    }

    @Test
    @DisplayName("DB 오류는 건너뛰지 않고 다시 받는다")
    void retriesDatabaseErrors() throws IOException {
        // given
        DefaultErrorHandler handler = handler();
        ConsumerRecord<?, ?> record = new ConsumerRecord<>("dev.record-card.music.generated", 0, 3L, "key", "{}");

        // when & then — false면 다시 받도록 되돌렸다는 뜻이다(첫 간격 1초를 기다린다)
        assertThat(handler.handleOne(new DataAccessResourceFailureException("db down"), record, mock(Consumer.class),
                runningContainer())).isFalse();
    }

    private DefaultErrorHandler handler() throws IOException {
        KafkaConsumerConfig config = new KafkaConsumerConfig(
                properties("12.16.16.198:9092", "dev.running-service", "secret", caFile()));
        return (DefaultErrorHandler) config.kafkaErrorHandler();
    }

    private static MessageListenerContainer runningContainer() {
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        given(container.isRunning()).willReturn(true);
        return container;
    }

    private String caFile() throws IOException {
        Path ca = tempDir.resolve("kafka-ca.pem");
        Files.writeString(ca, "-----BEGIN CERTIFICATE-----\n-----END CERTIFICATE-----\n");
        return ca.toString();
    }

    private static KafkaProperties properties(String bootstrap, String username, String password, String caLocation) {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of(bootstrap));
        properties.getProperties().put("sasl.jaas.config", JAAS.formatted(username, password));
        properties.getProperties().put("ssl.truststore.location", caLocation);
        return properties;
    }
}
