package com.runiverse.running_service.application.match.command.invitesend;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomInvitationStatus;

import java.util.List;
import java.util.UUID;

// 이번 요청으로 실제로 보낸 초대만 담는다 — 건너뛴 사람은 빠진다
public record InviteFriendsResult(List<SentInvitation> invitations) {

    // inviteCount는 호출자가 이 사람에게 보낸 횟수(이번 포함)다
    public record SentInvitation(UUID userId, RunningRoomInvitationStatus status, int inviteCount) {

    }
}
