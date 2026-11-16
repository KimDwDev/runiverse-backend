package com.runiverse.running_service.application.campaign.port.in;

import com.runiverse.running_service.application.campaign.command.send.SendPushCampaignCommand;

public interface SendPushCampaignUsecase {

    void handle(SendPushCampaignCommand command);
}
