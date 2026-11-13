package com.runiverse.running_service.unit_test.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardByRunningRecordPort.ExistingRecordCard;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 파라미터 묶기·결과 해석만 검증한다 — JPQL은 E2E가 실제 DB로 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록으로 카드 찾기 어댑터 단위 테스트")
class RecordCardFindByRunningRecordTest {

    private static final long RECORD_ID = 501L;
    private static final UUID CARD_ID = UUID.fromString("01a0f2d4-6180-7000-8000-4466554400c8");

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Tuple> query;

    @InjectMocks
    private RecordCardPersistenceAdapter adapter;

    @Test
    @DisplayName("카드가 없으면 비어 있다")
    void emptyWhenNoCard() {
        // given
        givenRows(Stream.empty());

        // when & then
        assertThat(adapter.findByRunningRecord(new RunningRecordId(RECORD_ID))).isEmpty();
        verify(query).setParameter("runningRecordId", RECORD_ID);
    }

    @Test
    @DisplayName("살아 있는 카드면 ID와 함께 지우지 않음으로 돌려준다")
    void liveCard() {
        // given
        givenRows(Stream.of(row(null)));

        // when & then
        assertThat(adapter.findByRunningRecord(new RunningRecordId(RECORD_ID)))
                .contains(new ExistingRecordCard(new RecordCardId(CARD_ID), false));
    }

    @Test
    @DisplayName("지운 카드면 ID와 함께 지움으로 돌려준다 — 되살릴 때 이 ID를 쓴다")
    void deletedCard() {
        // given
        givenRows(Stream.of(row(LocalDateTime.of(2026, 11, 13, 9, 0))));

        // when & then
        assertThat(adapter.findByRunningRecord(new RunningRecordId(RECORD_ID)))
                .contains(new ExistingRecordCard(new RecordCardId(CARD_ID), true));
    }

    private void givenRows(Stream<Tuple> rows) {
        given(entityManager.createQuery(anyString(), eq(Tuple.class))).willReturn(query);
        given(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).willReturn(query);
        given(query.getResultStream()).willReturn(rows);
    }

    private static Tuple row(LocalDateTime deletedAt) {
        Tuple tuple = mock(Tuple.class);
        given(tuple.get("recordCardId", UUID.class)).willReturn(CARD_ID);
        given(tuple.get("deletedAt", LocalDateTime.class)).willReturn(deletedAt);
        return tuple;
    }
}
