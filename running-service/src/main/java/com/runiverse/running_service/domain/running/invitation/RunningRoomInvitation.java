package com.runiverse.running_service.domain.running.invitation;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.invitation.exception.SelfInvitationException;
import com.runiverse.running_service.domain.running.invitation.vo.RunningRoomInvitationId;
import com.runiverse.running_service.domain.running.invitation.vo.RunningRoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.Builder;
import lombok.Getter;

import java.util.Optional;
import java.util.UUID;

// 초대 방에 한 사람을 부른 기록 — 신청이 아니다. 수락하기 전에는 running_players 행이 없다.
// 방·사람 쌍당 한 행이라 다시 초대하면 새로 만들지 않고 이 행을 되살린다
@Getter
public class RunningRoomInvitation {

    private final RunningRoomInvitationId runningRoomInvitationId;   // 저장 전에는 null
    private final RunningRoomId runningRoomId;
    private UserId inviterId;                                        // 다시 초대하면 마지막으로 초대한 사람으로 바뀐다
    private final UserId inviteeId;
    private RunningRoomInvitationStatus status;

    @Builder
    private RunningRoomInvitation(Long runningRoomInvitationId, Long runningRoomId,
                                  UUID inviterId, UUID inviteeId, RunningRoomInvitationStatus status) {
        this.runningRoomInvitationId = runningRoomInvitationId == null
                ? null : new RunningRoomInvitationId(runningRoomInvitationId);
        this.runningRoomId = new RunningRoomId(runningRoomId);
        this.inviteeId = new UserId(inviteeId);
        this.inviterId = inviterOf(inviterId, this.inviteeId);
        this.status = status == null ? RunningRoomInvitationStatus.PENDING : status;
    }

    public static RunningRoomInvitation invite(Long runningRoomId, UUID inviterId, UUID inviteeId) {
        return builder()
                .runningRoomId(runningRoomId)
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .build();
    }

    // 거절했거나 나간 사람을 다시 부른다. 대기 중·방에 있음·만료는 전이 규칙이 막는다
    public void reinvite(UUID inviterId) {
        UserId nextInviter = inviterOf(inviterId, inviteeId);   // 상태를 바꾸기 전에 검증을 끝낸다
        this.status = status.transitionTo(RunningRoomInvitationStatus.PENDING);
        this.inviterId = nextInviter;
    }

    public void accept() {
        this.status = status.transitionTo(RunningRoomInvitationStatus.ACCEPTED);
    }

    public boolean isPending() {
        return status == RunningRoomInvitationStatus.PENDING;
    }

    public boolean isNew() {
        return runningRoomInvitationId == null;
    }

    public Optional<RunningRoomInvitationId> getRunningRoomInvitationId() {
        return Optional.ofNullable(runningRoomInvitationId);
    }

    // DB CHECK(inviter_id <> invitee_id)와 같은 규칙 — 저장 전에 잡는다
    private static UserId inviterOf(UUID inviterId, UserId inviteeId) {
        UserId inviter = new UserId(inviterId);
        if (inviter.equals(inviteeId)) {
            throw new SelfInvitationException();
        }
        return inviter;
    }
}
