package com.runiverse.running_service.domain.running.room;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomInvitationStatus;

public record InvitationDraft(UserId inviterId, UserId inviteeId,
                              RunningRoomInvitationStatus status, int inviteCount) {

}
