package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.campaign.port.out.CompletePushCampaignPort;
import com.runiverse.running_service.application.campaign.port.out.LoadPushCampaignPort;
import com.runiverse.running_service.application.campaign.port.out.LoadPushCampaignRecipientsPort;
import com.runiverse.running_service.application.campaign.port.out.RevertPushCampaignPort;
import com.runiverse.running_service.domain.campaign.PushCampaign;
import com.runiverse.running_service.domain.campaign.vo.PushCampaignStatus;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// PushCampaignPersistenceAdapter를 대신한다 — 운영이 SQL로 캠페인·명단을 넣고 상태를 되돌리는 것을 흉내 낸다
public class InMemoryPushCampaignStore implements CompletePushCampaignPort, LoadPushCampaignPort,
        LoadPushCampaignRecipientsPort, RevertPushCampaignPort {

    private final Map<Long, Row> campaigns = new HashMap<>();

    public void insert(long pushCampaignId, String title, String body, List<UUID> recipientIds) {
        campaigns.put(pushCampaignId, new Row(title, body, PushCampaignStatus.DRAFT, List.copyOf(recipientIds)));
    }

    public void resetToDraft(long pushCampaignId) {
        Row row = campaigns.get(pushCampaignId);
        campaigns.put(pushCampaignId, new Row(row.title(), row.body(), PushCampaignStatus.DRAFT, row.recipientIds()));
    }

    public PushCampaignStatus statusOf(long pushCampaignId) {
        return campaigns.get(pushCampaignId).status();
    }

    // 조건부 UPDATE와 같다 — DRAFT일 때만 바꾸고 바꿨는지만 알려 준다
    @Override
    public boolean complete(long pushCampaignId) {
        Row row = campaigns.get(pushCampaignId);
        if (row == null || row.status() != PushCampaignStatus.DRAFT) {
            return false;
        }
        campaigns.put(pushCampaignId,
                new Row(row.title(), row.body(), PushCampaignStatus.COMPLETED, row.recipientIds()));
        return true;
    }

    // 되돌리는 조건부 UPDATE와 같다 — COMPLETED일 때만 DRAFT로 바꾼다
    @Override
    public boolean revert(long pushCampaignId) {
        Row row = campaigns.get(pushCampaignId);
        if (row == null || row.status() != PushCampaignStatus.COMPLETED) {
            return false;
        }
        campaigns.put(pushCampaignId, new Row(row.title(), row.body(), PushCampaignStatus.DRAFT, row.recipientIds()));
        return true;
    }

    @Override
    public Optional<PushCampaign> load(long pushCampaignId) {
        return Optional.ofNullable(campaigns.get(pushCampaignId))
                .map(row -> new PushCampaign(row.title(), row.body()));
    }

    @Override
    public List<UserId> loadRecipientIds(long pushCampaignId) {
        return campaigns.get(pushCampaignId).recipientIds().stream()
                .map(UserId::new)
                .toList();
    }

    private record Row(String title, String body, PushCampaignStatus status, List<UUID> recipientIds) {

    }
}
