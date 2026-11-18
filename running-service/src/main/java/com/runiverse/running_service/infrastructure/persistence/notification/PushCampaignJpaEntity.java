package com.runiverse.running_service.infrastructure.persistence.notification;

import com.runiverse.running_service.domain.notification.campaign.vo.PushCampaignStatus;
import com.runiverse.running_service.domain.notification.campaign.vo.PushCampaignType;
import com.runiverse.running_service.infrastructure.persistence.common.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

// 도메인 테이블이 아니라 운영 테이블이다 — 행은 운영이 SQL로 넣고, 앱은 발송 때 status만 한 번 바꾼다.
// 그 갱신은 조건부 UPDATE라 updated_at을 두지 않는다
@Getter
@Entity
@Table(name = "push_campaigns")
// 광고성 값을 열면 동의 없이 광고가 나가는 길이 생긴다 — 동의 확인을 발송 경로에 갖춘 뒤 연다
@Check(name = "ck_push_campaign_type", constraints = "type in ('NOTICE')")
@Check(name = "ck_push_campaign_status", constraints = "status in ('DRAFT', 'COMPLETED')")
@Check(name = "ck_push_campaign_title_length", constraints = "char_length(title) between 1 and 100")
@Check(name = "ck_push_campaign_body_length", constraints = "char_length(body) between 1 and 500")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushCampaignJpaEntity extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_campaign_id")
    private Long pushCampaignId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PushCampaignType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PushCampaignStatus status;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;
}
