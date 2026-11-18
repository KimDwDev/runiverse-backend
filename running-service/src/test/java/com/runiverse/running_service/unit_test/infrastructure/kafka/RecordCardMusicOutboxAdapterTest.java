package com.runiverse.running_service.unit_test.infrastructure.kafka;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.Photo;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.infrastructure.kafka.KafkaNamingProperties;
import com.runiverse.running_service.infrastructure.kafka.KafkaNamingProperties.Env;
import com.runiverse.running_service.infrastructure.kafka.outbox.OutboxEventJpaEntity;
import com.runiverse.running_service.infrastructure.kafka.outbox.OutboxEventJpaEntity.AggregateType;
import com.runiverse.running_service.infrastructure.kafka.outbox.RecordCardMusicOutboxAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 음악 요청 아웃박스 어댑터 단위 테스트")
class RecordCardMusicOutboxAdapterTest {

    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440000");
    private static final UUID IMAGE_ID = UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d");

    @Mock
    private EntityManager entityManager;

    private RecordCardMusicOutboxAdapter adapter;
    private TimeZone original;

    @BeforeEach
    void setUp() {
        // 운영은 DefaultTimeZoneInitializer가 APP_TIME_ZONE(Asia/Seoul)으로 맞춘다 — 테스트 JVM 시간대에 기대지 않는다
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        adapter = new RecordCardMusicOutboxAdapter(entityManager, new KafkaNamingProperties(Env.TEST));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(original);
    }

    @Test
    @DisplayName("환경이 붙은 토픽·카드 ID key·UUIDv7 이벤트 ID로 아웃박스 한 줄을 쓴다")
    void writesOutboxRow() {
        // when
        adapter.request(request(summary(null, null), List.of()));

        // then
        OutboxEventJpaEntity event = persisted();
        assertThat(event.getTopic()).isEqualTo("test.record-card.music.requested");
        assertThat(event.getAggregateType()).isEqualTo(AggregateType.RECORD_CARD);
        assertThat(event.getAggregateId()).isEqualTo(CARD_ID.toString());
        assertThat(event.getOutboxEventId().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("본문은 9-5 요청 이벤트 모양 그대로다 — snake_case 키를 정한 순서로 담는다")
    void payloadShape() {
        // when
        adapter.request(request(summary(168, 22),
                List.of(new Photo(IMAGE_ID, "record-cards/u/c/i/image.jpg", "바닷가에서 찍은 사진"))));

        // then
        Map<String, Object> payload = persisted().getPayload();
        assertThat(payload).containsExactly(
                Map.entry("user_id", USER_ID.toString()),
                Map.entry("record_card_id", CARD_ID.toString()),
                Map.entry("memo", "힘들었지만 끝까지 달려서 뿌듯했다"),
                Map.entry("activity_type", "TRAVEL"),
                Map.entry("running_summary", payload.get("running_summary")),
                Map.entry("photos", payload.get("photos")));
        assertThat(asMap(payload.get("running_summary"))).containsExactly(
                Map.entry("total_distance", 5000),
                Map.entry("total_duration", 1800),
                Map.entry("avg_pace", 360),
                Map.entry("avg_cadence", 168),
                Map.entry("start_at", "2026-10-19T06:30:00+09:00"),
                Map.entry("weather_code", 61),
                Map.entry("temperature", new BigDecimal("15.0")),
                Map.entry("total_elevation_gain", 22));
        assertThat(asList(payload.get("photos"))).singleElement().satisfies(photo -> assertThat(photo).containsExactly(
                Map.entry("record_card_image_id", IMAGE_ID.toString()),
                Map.entry("image_key", "record-cards/u/c/i/image.jpg"),
                Map.entry("caption", "바닷가에서 찍은 사진")));
    }

    @Test
    @DisplayName("값이 없는 필드도 키를 빼지 않고 null로 둔다")
    void keepsNullKeys() {
        // when
        adapter.request(request(summary(null, null), List.of(new Photo(IMAGE_ID, "k", null))));

        // then
        Map<String, Object> payload = persisted().getPayload();
        assertThat(asMap(payload.get("running_summary")))
                .containsEntry("avg_cadence", null)
                .containsEntry("total_elevation_gain", null);
        assertThat(asList(payload.get("photos")).getFirst()).containsEntry("caption", null);
    }

    @Test
    @DisplayName("사진이 없으면 photos는 빈 배열이다")
    void emptyPhotos() {
        // when
        adapter.request(request(summary(null, null), List.of()));

        // then
        assertThat(asList(persisted().getPayload().get("photos"))).isEmpty();
    }

    @Test
    @DisplayName("시작 시각은 초 아래를 버리고, 초가 0이어도 :00을 쓴 +09:00 형식이다")
    void startAtFormat() {
        // given - DB에는 마이크로초까지 있다
        RunningSummary summary = new RunningSummary(5000, 1800, 360, null,
                LocalDateTime.of(2026, 10, 19, 6, 30, 0, 123_456_000), 61, new BigDecimal("15.0"), null);

        // when
        adapter.request(request(summary, List.of()));

        // then
        assertThat(asMap(persisted().getPayload().get("running_summary")))
                .containsEntry("start_at", "2026-10-19T06:30:00+09:00");
    }

    @Test
    @DisplayName("요청마다 이벤트 ID가 다르다 — 같은 카드여도 받는 쪽이 이벤트를 구분한다")
    void eventIdPerRequest() {
        // when
        adapter.request(request(summary(null, null), List.of()));
        adapter.request(request(summary(null, null), List.of()));

        // then
        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(entityManager, org.mockito.Mockito.times(2)).persist(captor.capture());
        List<OutboxEventJpaEntity> events = captor.getAllValues();
        assertThat(events.get(0).getOutboxEventId()).isNotEqualTo(events.get(1).getOutboxEventId());
        assertThat(events).extracting(OutboxEventJpaEntity::getAggregateId).containsOnly(CARD_ID.toString());
    }

    private OutboxEventJpaEntity persisted() {
        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(entityManager).persist(captor.capture());
        return captor.getValue();
    }

    private static RecordCardMusicRequest request(RunningSummary summary, List<Photo> photos) {
        return new RecordCardMusicRequest(USER_ID, CARD_ID, "힘들었지만 끝까지 달려서 뿌듯했다",
                RecordCardActivityType.TRAVEL, summary, photos);
    }

    private static RunningSummary summary(Integer avgCadence, Integer totalElevationGain) {
        return new RunningSummary(5000, 1800, 360, avgCadence, LocalDateTime.of(2026, 10, 19, 6, 30), 61,
                new BigDecimal("15.0"), totalElevationGain);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
