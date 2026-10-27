package com.runiverse.running_service.application.match.command.invitesend;

import com.runiverse.running_service.application.match.command.invitesend.InviteFriendsResult.SentInvitation;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.exception.InviteRoomNotFoundException;
import com.runiverse.running_service.application.match.exception.NotFriendException;
import com.runiverse.running_service.application.match.exception.RunningInvitationClosedException;
import com.runiverse.running_service.application.match.port.in.InviteFriendsUsecase;
import com.runiverse.running_service.application.match.port.out.LoadActiveUserIdsPort;
import com.runiverse.running_service.application.match.port.out.LoadFriendIdsPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class InviteFriendsHandler implements InviteFriendsUsecase {

    private final LockMatchRoomPort lockMatchRoomPort;
    private final UpdateMatchRoomPort updateMatchRoomPort;
    private final LoadFriendIdsPort loadFriendIdsPort;
    private final LoadActiveUserIdsPort loadActiveUserIdsPort;
    private final InviteStartPolicy inviteStartPolicy;
    private final InviteProperties inviteProperties;
    private final RoomInfoAssembler roomInfoAssembler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public InviteFriendsResult handle(InviteFriendsCommand command) {
        UserId inviterId = new UserId(command.userId());
        // 0. 중복은 하나로 본다 — 요청 순서는 응답 순서로 남긴다
        List<UserId> inviteeIds = command.inviteeIds().stream().distinct().map(UserId::new).toList();
        // 1. 방을 잠그고 본다 — 없거나, 초대 방이 아니거나, 호출자가 지금 이 방에 없으면 셋 다 404다.
        //    애그리거트도 같은 규칙을 지키지만 그쪽 예외는 500이라 여기서 먼저 거른다
        RunningRoom room = lockMatchRoomPort.lockById(new RunningRoomId(command.runningRoomId()))
                .filter(locked -> locked.getType() == RunningRoomType.INVITE)
                .filter(locked -> isInRoom(locked, inviterId))
                .orElseThrow(InviteRoomNotFoundException::new);
        // 2. 모집 중이고 마감 전인가 — 경계는 수락과 같아야 해 정책이 함께 판정한다
        if (!inviteStartPolicy.isRecruiting(room, LocalDateTime.now())) {
            throw new RunningInvitationClosedException();
        }
        // 3. 하나라도 친구가 아니면 아무에게도 보내지 않는다 — 자기 자신도 친구가 아니라 여기서 걸린다
        Set<UserId> friendIds = loadFriendIdsPort.loadFriendIds(inviterId, inviteeIds);
        if (!friendIds.containsAll(inviteeIds)) {
            throw new NotFriendException();
        }
        // 4. 활성 신청이 있는 사람은 건너뛴다 — 다른 방이라 이 방 애그리거트가 볼 수 없다.
        //    이 방에 이미 들어온 사람도 여기 잡히고, 방도 한 번 더 거른다
        Set<UserId> activeIds = loadActiveUserIdsPort.loadActiveUserIds(inviteeIds);
        List<SentInvitation> sent = new ArrayList<>();
        for (UserId inviteeId : inviteeIds) {
            if (activeIds.contains(inviteeId)) {
                continue;
            }
            // 상한에 닿은 사람은 방이 건너뛴다
            room.invite(inviterId, inviteeId, inviteProperties.maxInviteCount())
                    .ifPresent(invitation -> sent.add(new SentInvitation(
                            inviteeId.value(), invitation.getStatus(), invitation.getInviteCount().value())));
        }
        // 5. 아무에게도 안 보냈으면 바뀐 것이 없다 — 저장도 알림도 하지 않는다
        if (!sent.isEmpty()) {
            updateMatchRoomPort.update(room);
            // 방 참가자에게 초대 현황을 알린다 — 조립은 트랜잭션 안에서 한다(자기 쓰기를 본다)
            eventPublisher.publishEvent(new MatchRoomChangedEvent(
                    MatchStreamEvent.updated(roomInfoAssembler.assemble(room))));
        }
        return new InviteFriendsResult(sent);
    }

    private static boolean isInRoom(RunningRoom room, UserId userId) {
        return room.getSessions().stream()
                .anyMatch(session -> session.isSameUser(userId) && session.isConnected());
    }

}
