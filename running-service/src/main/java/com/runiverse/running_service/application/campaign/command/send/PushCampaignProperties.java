package com.runiverse.running_service.application.campaign.command.send;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "push-campaign")
@Validated
public record PushCampaignProperties(
        // 발송부터 이 시간이 지나면 보내지 않는다 — 두지 않으면 FCM이 꺼진 폰을 위해 최대 4주 들고 있는다
        @NotNull Duration messageTtl
) {

}
