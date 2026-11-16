package com.runiverse.running_service.domain.campaign;

import com.runiverse.running_service.domain.campaign.exception.PushCampaignBodyRequiredException;
import com.runiverse.running_service.domain.campaign.exception.PushCampaignTitleRequiredException;
import lombok.Getter;

// 운영이 SQL로 넣는 공지 — 앱은 보낼 때 문구만 읽는다. 길이는 DB가 막고, 공백만 있는 문구는 여기서 막는다
@Getter
public class PushCampaign {

    private final String title;
    private final String body;

    public PushCampaign(String title, String body) {
        if (title == null || title.isBlank()) {
            throw new PushCampaignTitleRequiredException();
        }
        if (body == null || body.isBlank()) {
            throw new PushCampaignBodyRequiredException();
        }
        this.title = title;
        this.body = body;
    }
}
