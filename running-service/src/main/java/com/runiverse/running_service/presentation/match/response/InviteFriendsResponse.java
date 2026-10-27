package com.runiverse.running_service.presentation.match.response;

import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsResult;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomInvitationStatus;

import java.util.List;
import java.util.UUID;

// 이번 요청으로 실제로 보낸 초대만 담는다 — 건너뛴 사람은 빠지고, 아무에게도 안 보냈으면 빈 배열이다
public record InviteFriendsResponse(List<InvitationResponse> invitations) {

    public static InviteFriendsResponse from(InviteFriendsResult result) {
        return new InviteFriendsResponse(result.invitations().stream()
                .map(InvitationResponse::from)
                .toList());
    }

    // inviteCount는 호출자가 이 사람에게 보낸 횟수(이번 포함) — 상한에 닿으면 다음 요청부터 건너뛴다
    public record InvitationResponse(UUID userId, RunningRoomInvitationStatus status, int inviteCount) {

        public static InvitationResponse from(InviteFriendsResult.SentInvitation invitation) {
            return new InvitationResponse(invitation.userId(), invitation.status(), invitation.inviteCount());
        }
    }
}
