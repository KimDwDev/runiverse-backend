package com.runiverse.running_service.application.running.command.accountdeletion;

import com.runiverse.running_service.application.match.command.stream.MatchStreamCloseRequestedEvent;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.LockMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.port.in.SettleRunningForAccountDeletionUsecase;
import com.runiverse.running_service.application.running.port.out.DeleteRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class SettleRunningForAccountDeletionHandler
        implements SettleRunningForAccountDeletionUsecase {

    private final LockMatchApplicationPort lockMatchApplicationPort;
    private final LoadMatchRoomPort loadMatchRoomPort;
    private final LockRunningRoomPort lockRunningRoomPort;
    private final UpdateMatchRoomPort updateMatchRoomPort;
    private final DeleteRunningPlayerPort deleteRunningPlayerPort;
    private final FinishRunningUsecase finishRunningUsecase;
    private final RoomInfoAssembler roomInfoAssembler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void handle(SettleRunningForAccountDeletionCommand command) {
        UserId userId = new UserId(command.userId());
        // 연결은 커밋 뒤에 닫는다 — 여기서 닫으면 탈퇴가 롤백돼도 되살릴 수 없다
        eventPublisher.publishEvent(new MatchStreamCloseRequestedEvent(userId));
        // 활성 신청이 없으면 정리할 러닝이 없다 — 탈퇴자 대부분이 여기서 끝난다
        RunningPlayer player = lockMatchApplicationPort.lockActive(userId).orElse(null);
        if (player == null) {
            return;
        }
        // "방 미배정" 상태는 없다 — 비어 있으면 데이터 사고라 드러낸다
        RunningRoomId roomId = loadMatchRoomPort.findAssignedRoom(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "활성 신청에 배정된 방이 없다 — userId=" + userId.value()));
        // 인원이 함께 줄어드니 잠그고 읽는다. 취소·시작과 같은 순서다
        RunningRoom room = lockRunningRoomPort.lockById(roomId)
                .orElseThrow(() -> new IllegalStateException(
                        "배정된 방을 찾을 수 없다 — runningRoomId=" + roomId.value()));

        // 시작 여부는 참가자가 아니라 방이 답한다 —
        // 방이 시작된 뒤에 앱을 켜지 않은 참가자는 status가 JOINED로 남아 있다
        if (room.getStatus().isBeforeStart()) {
            leaveBeforeStart(userId, player, room);
            return;
        }
        // 시작한 방은 기존 종료 경로가 마지막 좌표까지로 기록을 확정한다.
        // 신청·세션 행은 남긴다 — 기록이 없는 참가자도 결과에 남아야 한다
        finishRunningUsecase.handle(
                new FinishRunningCommand(roomId.value(), command.userId(), true));
    }

    private void leaveBeforeStart(UserId userId, RunningPlayer player, RunningRoom room) {
        // 인원을 줄이고, 0이 되면 방이 CANCELLED로 닫힌다
        room.leave(userId, LocalDateTime.now());
        updateMatchRoomPort.update(room);
        // 이탈 이력이 아니라 신청 자체가 없던 일이 된다 — 세션도 함께 지워진다
        deleteRunningPlayerPort.delete(player);
        // 남은 참가자에게 알린다. 인원이 0이면 받을 사람이 없다
        if (room.getPlayerCount().current() > 0) {
            eventPublisher.publishEvent(new MatchRoomChangedEvent(
                    MatchStreamEvent.updated(roomInfoAssembler.assemble(room))));
        }
    }
}
