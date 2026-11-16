package com.runiverse.running_service.infrastructure.persistence.campaign;

import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;
import java.util.UUID;

// 운영이 INSERT … SELECT로 고른 받는 사람 — 앱은 읽기만 한다. 캠페인별 명단 조회는 복합 PK가 덮는다
@Getter
@Entity
@Table(name = "push_campaign_recipients")
@IdClass(PushCampaignRecipientJpaEntity.Pk.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushCampaignRecipientJpaEntity extends BaseCreatedAtEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "push_campaign_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_push_campaign_recipient_campaign"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PushCampaignJpaEntity campaign;

    // 논리 참조다 — 탈퇴해도 명단에서 빠지지 않는다
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // @IdClass의 필드명은 엔티티의 @Id 필드명과 같고, 타입은 대상 엔티티의 PK 타입이다
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {

        private Long campaign;
        private UUID userId;
    }
}
