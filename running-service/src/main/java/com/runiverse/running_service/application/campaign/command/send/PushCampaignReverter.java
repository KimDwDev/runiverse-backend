package com.runiverse.running_service.application.campaign.command.send;

import com.runiverse.running_service.application.campaign.port.out.RevertPushCampaignPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 발송을 시작하지 못한 캠페인을 DRAFT로 되돌려 다시 부를 수 있게 한다 — 받은 사람이 없어 다시 보내도 겹치지 않는다
@Component
@RequiredArgsConstructor
public class PushCampaignReverter {

    private final RevertPushCampaignPort revertPushCampaignPort;

    // 요청이 커밋된 뒤나 서버가 꺼지는 중에 불린다 — 커밋 뒤 콜백 안에서는 끝난 트랜잭션에 낄 수 없어 새로 연다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean revert(long pushCampaignId) {
        return revertPushCampaignPort.revert(pushCampaignId);
    }
}
