package com.runiverse.running_service.unit_test.infrastructure.persistence.running;

import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRecordPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 파라미터 묶기·결과 해석만 검증한다 — 숨긴 방·주인 조건은 E2E가 실제 DB로 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("기록 카드용 내 기록 확인 어댑터 단위 테스트")
class RunningRecordOwnershipTest {

    private static final long RECORD_ID = 501L;
    private static final UUID USER_ID = UUID.fromString("01a0f2d4-6180-7000-8000-446655440015");

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Long> countQuery;

    @InjectMocks
    private RunningRecordPersistenceAdapter adapter;

    @Test
    @DisplayName("기록 ID와 사용자 ID를 묶어 묻고, 한 건이라도 있으면 참이다")
    void ownedWhenCounted() {
        // given
        givenCount(1L);

        // when
        boolean owned = adapter.existsOwnedBy(new RunningRecordId(RECORD_ID), new UserId(USER_ID));

        // then
        assertThat(owned).isTrue();
        verify(countQuery).setParameter("runningRecordId", RECORD_ID);
        verify(countQuery).setParameter("userId", USER_ID);
    }

    @Test
    @DisplayName("없거나·남의 것이거나·숨긴 방이라 세어지지 않으면 거짓이다")
    void notOwnedWhenZero() {
        // given
        givenCount(0L);

        // when & then
        assertThat(adapter.existsOwnedBy(new RunningRecordId(RECORD_ID), new UserId(USER_ID))).isFalse();
    }

    private void givenCount(long count) {
        given(entityManager.createQuery(anyString(), eq(Long.class))).willReturn(countQuery);
        given(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).willReturn(countQuery);
        given(countQuery.getSingleResult()).willReturn(count);
    }
}
