package com.runiverse.running_service.unit_test.infrastructure.persistence.running;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardMusicRequest.RunningSummary;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.infrastructure.persistence.running.RunningRecordPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 파라미터 묶기·결과 해석만 검증한다 — JPQL 자체는 E2E가 실제 DB로 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("음악 생성 요청용 기록 요약 어댑터 단위 테스트")
class RunningSummaryLoadTest {

    private static final long RECORD_ID = 501L;

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Tuple> query;

    @InjectMocks
    private RunningRecordPersistenceAdapter adapter;

    @Test
    @DisplayName("기록 ID로 읽은 여덟 값을 순서대로 요약에 옮긴다 — 비어 있는 값은 null 그대로다")
    void mapsRow() {
        // given
        LocalDateTime startAt = LocalDateTime.of(2026, 10, 19, 6, 30);
        Tuple row = row(5000, 1800, 360, null, startAt, 61, new BigDecimal("15.0"), null);
        givenRows(List.of(row));

        // when
        Optional<RunningSummary> summary = adapter.load(new RunningRecordId(RECORD_ID));

        // then
        assertThat(summary).contains(new RunningSummary(5000, 1800, 360, null, startAt, 61,
                new BigDecimal("15.0"), null));
        verify(query).setParameter("runningRecordId", RECORD_ID);
    }

    @Test
    @DisplayName("행이 없으면 비어 있다")
    void emptyWhenMissing() {
        // given
        givenRows(List.of());

        // when & then
        assertThat(adapter.load(new RunningRecordId(RECORD_ID))).isEmpty();
    }

    private void givenRows(List<Tuple> rows) {
        given(entityManager.createQuery(anyString(), eq(Tuple.class))).willReturn(query);
        given(query.setParameter("runningRecordId", RECORD_ID)).willReturn(query);
        given(query.getResultList()).willReturn(rows);
    }

    private static Tuple row(Integer distance, Integer duration, Integer pace, Integer cadence, LocalDateTime startAt,
                             Integer weatherCode, BigDecimal temperature, Integer elevationGain) {
        Tuple row = mock(Tuple.class);
        given(row.get(0, Integer.class)).willReturn(distance);
        given(row.get(1, Integer.class)).willReturn(duration);
        given(row.get(2, Integer.class)).willReturn(pace);
        given(row.get(3, Integer.class)).willReturn(cadence);
        given(row.get(4, LocalDateTime.class)).willReturn(startAt);
        given(row.get(5, Integer.class)).willReturn(weatherCode);
        given(row.get(6, BigDecimal.class)).willReturn(temperature);
        given(row.get(7, Integer.class)).willReturn(elevationGain);
        return row;
    }
}
