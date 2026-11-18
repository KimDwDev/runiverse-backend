package com.runiverse.running_service.application.notification.port.in;

import com.runiverse.running_service.application.notification.command.campaign.SendPushCampaignCommand;

public interface SendPushCampaignUsecase {

    void handle(SendPushCampaignCommand command);
}
