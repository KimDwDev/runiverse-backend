package com.runiverse.running_service.unit_test.infrastructure.persistence.recordcard;

import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.infrastructure.persistence.recordcard.RecordCardPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 파라미터 묶기·결과 해석만 검증한다 — JPQL은 E2E가 실제 DB로 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록에 카드가 있는지 어댑터 단위 테스트")
class RecordCardExistsByRunningRecordTest {

    private static final long RECORD_ID = 501L;

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Long> query;

    @InjectMocks
    private RecordCardPersistenceAdapter adapter;

    @Test
    @DisplayName("기록 ID로 세어 한 건이라도 있으면 참이다")
    void existsWhenCounted() {
        // given
        givenCount(1L);

        // when & then
        assertThat(adapter.existsByRunningRecord(new RunningRecordId(RECORD_ID))).isTrue();
        verify(query).setParameter("runningRecordId", RECORD_ID);
    }

    @Test
    @DisplayName("카드가 없으면 거짓이다 — 지운 카드도 행이 없다")
    void notExistsWhenZero() {
        // given
        givenCount(0L);

        // when & then
        assertThat(adapter.existsByRunningRecord(new RunningRecordId(RECORD_ID))).isFalse();
    }

    private void givenCount(long count) {
        given(entityManager.createQuery(anyString(), eq(Long.class))).willReturn(query);
        given(query.setParameter(anyString(), any())).willReturn(query);
        given(query.getSingleResult()).willReturn(count);
    }
}
