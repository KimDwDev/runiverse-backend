package com.runiverse.running_service.application.campaign.port.out;

import com.runiverse.running_service.domain.campaign.PushCampaign;

import java.util.Optional;

public interface LoadPushCampaignPort {

    Optional<PushCampaign> load(long pushCampaignId);
}
