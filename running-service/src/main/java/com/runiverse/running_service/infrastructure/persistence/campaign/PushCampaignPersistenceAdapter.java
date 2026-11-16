package com.runiverse.running_service.infrastructure.persistence.campaign;

import com.runiverse.running_service.application.campaign.port.out.CompletePushCampaignPort;
import com.runiverse.running_service.application.campaign.port.out.LoadPushCampaignPort;
import com.runiverse.running_service.application.campaign.port.out.LoadPushCampaignRecipientsPort;
import com.runiverse.running_service.domain.campaign.PushCampaign;
import com.runiverse.running_service.domain.campaign.vo.PushCampaignStatus;
import com.runiverse.running_service.domain.common.vo.UserId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PushCampaignPersistenceAdapter implements CompletePushCampaignPort, LoadPushCampaignPort,
        LoadPushCampaignRecipientsPort {

    private final EntityManager entityManager;

    // 읽고 고치지 않고 조건을 건 UPDATE 한 번으로 바꾼다 — 동시에 불려도 행 잠금이 하나만 1행을 받게 한다
    @Override
    public boolean complete(long pushCampaignId) {
        int updated = entityManager.createQuery("""
                        UPDATE PushCampaignJpaEntity c
                        SET c.status = :completed
                        WHERE c.pushCampaignId = :pushCampaignId AND c.status = :draft
                        """)
                .setParameter("completed", PushCampaignStatus.COMPLETED)
                .setParameter("pushCampaignId", pushCampaignId)
                .setParameter("draft", PushCampaignStatus.DRAFT)
                .executeUpdate();
        return updated == 1;
    }

    @Override
    public Optional<PushCampaign> load(long pushCampaignId) {
        return Optional.ofNullable(entityManager.find(PushCampaignJpaEntity.class, pushCampaignId))
                .map(entity -> new PushCampaign(entity.getTitle(), entity.getBody()));
    }

    @Override
    public List<UserId> loadRecipientIds(long pushCampaignId) {
        return entityManager.createQuery("""
                        SELECT r.userId
                        FROM PushCampaignRecipientJpaEntity r
                        WHERE r.campaign.pushCampaignId = :pushCampaignId
                        """, UUID.class)
                .setParameter("pushCampaignId", pushCampaignId)
                .getResultList().stream()
                .map(UserId::new)
                .toList();
    }
}
