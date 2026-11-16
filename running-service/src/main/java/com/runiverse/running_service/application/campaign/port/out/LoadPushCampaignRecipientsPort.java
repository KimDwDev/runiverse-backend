package com.runiverse.running_service.application.campaign.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.List;

public interface LoadPushCampaignRecipientsPort {

    // 탈퇴한 사람도 명단에 남아 있다 — 보낼 때 켜진 기기가 없어 빠진다
    List<UserId> loadRecipientIds(long pushCampaignId);
}
