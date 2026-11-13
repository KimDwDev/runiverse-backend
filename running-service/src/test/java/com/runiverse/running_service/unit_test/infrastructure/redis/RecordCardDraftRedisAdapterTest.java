package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.DraftImage;
import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft.UploadType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.infrastructure.redis.recordcard.RecordCardDraftRedisAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 초안 Redis 어댑터 단위 테스트")
class RecordCardDraftRedisAdapterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID DRAFT_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400d1");
    private static final Duration TTL = Duration.ofMinutes(30);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RecordCardDraftRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        adapter = new RecordCardDraftRedisAdapter(redisTemplate, JSON);
    }

    @Test
    @DisplayName("초안 ID 키에 유효 시간을 걸어 저장한다")
    void saveWithKeyAndTtl() {
        // when
        adapter.save(draft(), TTL);

        // then
        verify(valueOperations).set(eq("record-card:draft:" + DRAFT_ID), anyString(), eq(TTL));
    }

    @Test
    @DisplayName("저장한 JSON을 다시 읽으면 같은 초안이다 — 확정(9-2)이 그대로 읽어 쓴다")
    void savedJsonRoundTrips() {
        // given
        RecordCardDraft draft = draft();

        // when
        adapter.save(draft, TTL);

        // then
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(anyString(), json.capture(), any(Duration.class));
        assertThat(JSON.readValue(json.getValue(), RecordCardDraft.class)).isEqualTo(draft);
    }

    @Test
    @DisplayName("Redis 저장에 실패하면 삼키지 않고 던진다 — 저장 못 한 초안으로 업로드 URL을 주면 안 된다")
    void propagateFailure() {
        // given
        willThrow(new RedisConnectionFailureException("down"))
                .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

        // when & then
        assertThatThrownBy(() -> adapter.save(draft(), TTL))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    private static RecordCardDraft draft() {
        UUID cardId = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");
        UUID userId = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");
        return new RecordCardDraft(DRAFT_ID, RecordCardDraft.DraftType.CREATE, cardId, userId, 501L, RecordCardActivityType.TRAVEL,
                "바람이 좋았다", RecordCardVisibility.FRIENDS, List.of(
                new DraftImage(UUID.fromString("01a0f2d4-6180-7000-8000-44665544012d"), "k/1/image.jpg",
                        "image/jpeg", 204800L, "노을", UploadType.SINGLE, null, null, false),
                new DraftImage(UUID.fromString("01a0f2d4-6180-7000-8000-44665544013e"), "k/2/image.png",
                        "image/png", 20_971_521L, null, UploadType.MULTIPART, "s3-upload-id", 3, false)),
                1, false);
    }
}
