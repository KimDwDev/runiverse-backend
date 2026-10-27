package com.runiverse.running_service.presentation.match.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// 중복은 오류가 아니다 — Handler가 하나로 합친다. 친구 여부·활성 신청은 저장소를 봐야 해 여기서 보지 않는다
public record InviteFriendsRequest(
        @NotNull(message = "초대할 친구는 필수입니다.")
        @Size(min = 1, max = 50, message = "초대할 친구는 1명 이상 50명 이하여야 합니다.")
        List<@NotNull(message = "입력값이 올바르지 않습니다.") UUID> inviteeIds
) {

}
