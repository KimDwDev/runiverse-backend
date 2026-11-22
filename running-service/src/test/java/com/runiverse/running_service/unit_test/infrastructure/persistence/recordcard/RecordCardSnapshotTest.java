package com.runiverse.running_service.unit_test.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardSnapshotReason;
import com.runiverse.running_service.domain.recordcard.RecordCard;
import com.runiverse.running_service.domain.recordcard.RecordCardImage;
import com.runiverse.running_service.domain.recordcard.vo.MusicStatus;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardVisibility;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardHistoryJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardPersistenceAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

// EntityManager를 목으로 대체하므로 옮기는 값만 검증한다 — 테이블·CHECK·jsonb 저장은 E2E가 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드 이력 저장·삭제 어댑터 단위 테스트")
class RecordCardSnapshotTest {

    private static final UUID CARD_ID = UUID.fromString("01a1ee0f-4fee-7741-a7b1-7eeb86bb369f");
    private static final UUID USER_ID = UUID.fromString("01a1e874-91e8-73b4-9680-d7f377cbd009");
    private static final UUID IMAGE_A = UUID.fromString("01a1ee0f-4fee-7d58-a7b2-925ef4d37f36");
    private static final UUID IMAGE_B = UUID.fromString("01a1ee0f-5029-7488-9b2d-c7b5d4580997");
    private static final long RECORD_ID = 55L;
    private static final String KEY_A = "record-cards/u/c/a/IMG_1.jpg";
    private static final String KEY_B = "record-cards/u/c/b/IMG_2.jpg";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 20, 9, 0);

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private RecordCardPersistenceAdapter adapter;

    @Test
    @DisplayName("바뀌기 직전 카드 값·사진 전체(순서·음악 포함)·원래 만든 시각을 이력 한 줄로 남긴다")
    void snapshotsCard() {
        // given
        RecordCardJpaEntity cardEntity = mock(RecordCardJpaEntity.class);
        given(cardEntity.getCreatedAt()).willReturn(CREATED_AT);
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(cardEntity);

        // when
        adapter.snapshot(card(), RecordCardSnapshotReason.UPDATE);

        // then
        ArgumentCaptor<RecordCardHistoryJpaEntity> captor = ArgumentCaptor.forClass(RecordCardHistoryJpaEntity.class);
        verify(entityManager).persist(captor.capture());
        RecordCardHistoryJpaEntity history = captor.getValue();
        assertThat(history.getRecordCardId()).isEqualTo(CARD_ID);
        assertThat(history.getSnapshotReason()).isEqualTo(RecordCardSnapshotReason.UPDATE);
        assertThat(history.getUserId()).isEqualTo(USER_ID);
        assertThat(history.getRunningRecordId()).isEqualTo(RECORD_ID);
        assertThat(history.getVisibility()).isEqualTo(RecordCardVisibility.FRIENDS);
        assertThat(history.getActivityType()).isEqualTo(RecordCardActivityType.TRAVEL);
        assertThat(history.getMemo()).isEqualTo("한강 한 바퀴");
        assertThat(history.getThumbnailImageKey()).isEqualTo(KEY_B);
        assertThat(history.getBaseMusicStatus()).isEqualTo(MusicStatus.COMPLETED);
        assertThat(history.getBaseMusicKey()).isEqualTo("audio/base.wav");
        assertThat(history.getCardCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(history.getImages()).containsExactly(
                image(IMAGE_A, KEY_A, "노을", 0, "COMPLETED", "audio/a.wav"),
                image(IMAGE_B, KEY_B, null, 1, "GENERATING", null));
    }

    @Test
    @DisplayName("잠근 뒤 다시 읽은 카드가 없으면 이력을 남기지 않고 실패한다")
    void failsWithoutCard() {
        // given
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> adapter.snapshot(card(), RecordCardSnapshotReason.DELETE))
                .isInstanceOf(IllegalStateException.class);
        verify(entityManager, never()).persist(any());
    }

    @Test
    @DisplayName("삭제는 잠근 뒤 읽은 카드 엔티티를 지운다 — 사진 행은 DB CASCADE가 지운다")
    void deletesCardEntity() {
        // given
        RecordCardJpaEntity cardEntity = mock(RecordCardJpaEntity.class);
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(cardEntity);

        // when
        adapter.delete(new RecordCardId(CARD_ID));

        // then
        verify(entityManager).remove(cardEntity);
    }

    @Test
    @DisplayName("지울 카드가 없으면 아무것도 지우지 않고 실패한다")
    void deleteFailsWithoutCard() {
        // given
        given(entityManager.find(RecordCardJpaEntity.class, CARD_ID)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> adapter.delete(new RecordCardId(CARD_ID))).isInstanceOf(IllegalStateException.class);
        verify(entityManager, never()).remove(any());
    }

    private static RecordCard card() {
        return RecordCard.restore(CARD_ID)
                .runningRecordId(RECORD_ID)
                .userId(USER_ID)
                .visibility(RecordCardVisibility.FRIENDS)
                .activityType(RecordCardActivityType.TRAVEL)
                .baseMusicStatus(MusicStatus.COMPLETED)
                .baseMusicKey("audio/base.wav")
                .memo("한강 한 바퀴")
                .displayOrder(1)
                .thumbnailImageKey(KEY_B)
                .images(List.of(
                        RecordCardImage.restore(IMAGE_A, KEY_A, "노을", "audio/a.wav", MusicStatus.COMPLETED, 0),
                        RecordCardImage.restore(IMAGE_B, KEY_B, null, null, MusicStatus.GENERATING, 1)))
                .build();
    }

    // null 값을 담아야 해서 Map.of 대신 HashMap을 쓴다
    private static Map<String, Object> image(UUID id, String key, String caption, int sortOrder, String status,
                                             String musicKey) {
        Map<String, Object> map = new HashMap<>();
        map.put("recordCardImageId", id.toString());
        map.put("imageKey", key);
        map.put("caption", caption);
        map.put("sortOrder", sortOrder);
        map.put("musicStatus", status);
        map.put("musicKey", musicKey);
        return map;
    }
}
