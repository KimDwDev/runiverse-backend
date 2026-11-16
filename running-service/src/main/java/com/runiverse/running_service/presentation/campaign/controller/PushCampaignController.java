package com.runiverse.running_service.presentation.campaign.controller;

import com.runiverse.running_service.application.campaign.command.send.SendPushCampaignCommand;
import com.runiverse.running_service.application.campaign.port.in.SendPushCampaignUsecase;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 운영자가 터미널에서 부른다 — 토큰 대신 관리자 키 필터가 막는다
@RestController
@RequestMapping("admin/push-campaigns")
@RequiredArgsConstructor
public class PushCampaignController {

    private final SendPushCampaignUsecase sendPushCampaignUsecase;

    // 상태를 바꾸면 바로 답한다 — 푸시는 커밋 뒤 발송 스레드가 보낸다
    @PostMapping("/{pushCampaignId}/send")
    public ResponseEntity<Void> sendPushCampaign(
            @PathVariable @Min(value = 1, message = "입력값이 올바르지 않습니다.") Long pushCampaignId
    ) {
        sendPushCampaignUsecase.handle(new SendPushCampaignCommand(pushCampaignId));
        return ResponseEntity.noContent().build();
    }
}
