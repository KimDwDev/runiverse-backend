package com.runiverse.running_service.application.notification.port.out;

import com.runiverse.running_service.domain.notification.campaign.PushCampaign;

import java.util.Optional;

public interface LoadPushCampaignPort {

    Optional<PushCampaign> load(long pushCampaignId);
}
