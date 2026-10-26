package com.runiverse.running_service.application.invite.command.send;

import java.util.List;
import java.util.UUID;

// 중복은 Handler가 하나로 합친다 — 요청 순서는 응답 순서로 남긴다
public record InviteFriendsCommand(UUID userId, Long runningRoomId, List<UUID> inviteeIds) {

}
