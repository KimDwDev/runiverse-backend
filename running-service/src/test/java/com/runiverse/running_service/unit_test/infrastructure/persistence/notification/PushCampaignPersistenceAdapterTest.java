package com.runiverse.running_service.unit_test.infrastructure.persistence.notification;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.campaign.PushCampaign;
import com.runiverse.running_service.domain.notification.campaign.exception.PushCampaignBodyRequiredException;
import com.runiverse.running_service.domain.notification.campaign.vo.PushCampaignStatus;
import com.runiverse.running_service.infrastructure.persistence.notification.PushCampaignJpaEntity;
import com.runiverse.running_service.infrastructure.persistence.notification.PushCampaignPersistenceAdapter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// EntityManager를 목으로 대체하므로 어댑터의 분기·변환만 검증한다 —
// JPQL 자체는 실행되지 않는다(실제 DB 동작은 E2E가 본다)
@ExtendWith(MockitoExtension.class)
@DisplayName("푸시 캠페인 저장 어댑터 단위 테스트")
class PushCampaignPersistenceAdapterTest {

    private static final long CAMPAIGN_ID = 7L;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query updateQuery;

    @Mock
    private TypedQuery<UUID> recipientQuery;

    @InjectMocks
    private PushCampaignPersistenceAdapter adapter;

    @Test
    @DisplayName("보내기 전 행 하나를 바꿨으면 true다")
    void completeReturnsTrueWhenOneRowChanged() {
        // given
        givenUpdatedRows(1);

        // when & then -> DRAFT일 때만 바꾸도록 조건을 건다
        assertThat(adapter.complete(CAMPAIGN_ID)).isTrue();
        verify(updateQuery).setParameter("completed", PushCampaignStatus.COMPLETED);
        verify(updateQuery).setParameter("draft", PushCampaignStatus.DRAFT);
        verify(updateQuery).setParameter("pushCampaignId", CAMPAIGN_ID);
    }

    @Test
    @DisplayName("바꾼 행이 없으면 false다 — 없는 캠페인이거나 이미 보냈다")
    void completeReturnsFalseWhenNoRowChanged() {
        // given
        givenUpdatedRows(0);

        // when & then
        assertThat(adapter.complete(CAMPAIGN_ID)).isFalse();
    }

    @Test
    @DisplayName("보낸 상태 행 하나를 되돌렸으면 true다")
    void revertReturnsTrueWhenOneRowChanged() {
        // given
        givenUpdatedRows(1);

        // when & then -> COMPLETED일 때만 DRAFT로 되돌리도록 조건을 건다
        assertThat(adapter.revert(CAMPAIGN_ID)).isTrue();
        verify(updateQuery).setParameter("draft", PushCampaignStatus.DRAFT);
        verify(updateQuery).setParameter("completed", PushCampaignStatus.COMPLETED);
        verify(updateQuery).setParameter("pushCampaignId", CAMPAIGN_ID);
    }

    @Test
    @DisplayName("되돌린 행이 없으면 false다 — 없는 캠페인이거나 보낸 상태가 아니다")
    void revertReturnsFalseWhenNoRowChanged() {
        // given
        givenUpdatedRows(0);

        // when & then
        assertThat(adapter.revert(CAMPAIGN_ID)).isFalse();
    }

    @Test
    @DisplayName("행을 도메인으로 바꿔 문구를 돌려준다")
    void loadsCampaign() {
        // given
        given(entityManager.find(PushCampaignJpaEntity.class, CAMPAIGN_ID))
                .willReturn(entity("서비스 점검 안내", "11월 20일 02시부터 30분간 점검해요"));

        // when
        Optional<PushCampaign> campaign = adapter.load(CAMPAIGN_ID);

        // then
        assertThat(campaign).hasValueSatisfying(loaded -> {
            assertThat(loaded.getTitle()).isEqualTo("서비스 점검 안내");
            assertThat(loaded.getBody()).isEqualTo("11월 20일 02시부터 30분간 점검해요");
        });
    }

    @Test
    @DisplayName("행이 없으면 비어 있다")
    void emptyWhenNoCampaign() {
        // when & then
        assertThat(adapter.load(CAMPAIGN_ID)).isEmpty();
    }

    @Test
    @DisplayName("운영이 공백만 넣은 문구는 도메인으로 바꾸다 거절한다")
    void rejectsBlankTextFromDatabase() {
        // given -> 길이 CHECK는 공백 문구를 통과시킨다
        given(entityManager.find(PushCampaignJpaEntity.class, CAMPAIGN_ID)).willReturn(entity("제목", "   "));

        // when & then
        assertThatThrownBy(() -> adapter.load(CAMPAIGN_ID)).isInstanceOf(PushCampaignBodyRequiredException.class);
    }

    @Test
    @DisplayName("캠페인의 받는 사람 ID를 UserId로 돌려준다")
    void loadsRecipientIds() {
        // given
        UUID chulsu = UuidCreator.getTimeOrderedEpoch();
        UUID younghee = UuidCreator.getTimeOrderedEpoch();
        given(entityManager.createQuery(anyString(), eq(UUID.class))).willReturn(recipientQuery);
        given(recipientQuery.setParameter(anyString(), any())).willReturn(recipientQuery);
        given(recipientQuery.getResultList()).willReturn(List.of(chulsu, younghee));

        // when
        List<UserId> recipientIds = adapter.loadRecipientIds(CAMPAIGN_ID);

        // then
        verify(recipientQuery).setParameter("pushCampaignId", CAMPAIGN_ID);
        assertThat(recipientIds).containsExactly(new UserId(chulsu), new UserId(younghee));
    }

    private void givenUpdatedRows(int rows) {
        given(entityManager.createQuery(anyString())).willReturn(updateQuery);
        given(updateQuery.setParameter(anyString(), any())).willReturn(updateQuery);
        given(updateQuery.executeUpdate()).willReturn(rows);
    }

    // 행은 운영 SQL이 넣어 엔티티에 생성 메서드가 없다 — DB에서 읽어 온 행을 흉내 내려면 값을 심어야 한다
    private static PushCampaignJpaEntity entity(String title, String body) {
        PushCampaignJpaEntity entity = BeanUtils.instantiateClass(PushCampaignJpaEntity.class);
        ReflectionTestUtils.setField(entity, "pushCampaignId", CAMPAIGN_ID);
        ReflectionTestUtils.setField(entity, "status", PushCampaignStatus.DRAFT);
        ReflectionTestUtils.setField(entity, "title", title);
        ReflectionTestUtils.setField(entity, "body", body);
        return entity;
    }
}
