package com.runiverse.running_service.application.running.command.forcefinish;

import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.running.common.RunningComboArchiver;
import com.runiverse.running_service.application.running.port.out.ExistsRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LockRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.StartMatchCooldownPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 강제 종료 알람의 잠금 안 단계 — 잠금 없이 본 결과로 '고칠 것이 있다'고 판단했을 때만 부른다(D-83).
// 잠그고 다시 읽은 값으로만 고친다 — 잠금 없이 읽은 사이에 본인 종료·RUNNING_START가 끼었을 수 있다.
// S3·날씨가 드는 러너 확정은 여기서 하지 않는다 — 커밋 뒤 워커가 한다
@Slf4j
@Component
@RequiredArgsConstructor
public class ForceFinishSteps {

    // 확정 인원이 1이면 안 나타나도 곤란해지는 상대가 없다 — 시작 전 이탈 면제와 같은 기준이다
    private static final int PENALTY_MIN_PLAYER_COUNT = 2;
    private final LockRunningPlayerPort lockRunningPlayerPort;
    private final LockRunningRoomPort lockRunningRoomPort;
    private final LoadRoomPlayerPort loadRoomPlayerPort;
    private final UpdateRunningRoomPort updateRunningRoomPort;
    private final UpdateRunningPlayerPort updateRunningPlayerPort;
    private final StartMatchCooldownPort startMatchCooldownPort;
    private final ExistsRunningRecordPort existsRunningRecordPort;
    private final MatchProperties matchProperties;
    private final RunningComboArchiver runningComboArchiver;

    // 안 나타난 사람을 이탈로 닫고, 뛰는 사람이 없으면 방도 닫는다. 남은 러너(RUNNING)를 돌려준다
    @Transactional
    public List<UserId> settle(RunningRoomId roomId) {
        // 참가자를 방보다 먼저 잠근다 — 시작·취소·탈퇴 정산·끝내기가 모두 참가자 → 방 순이라,
        // 방을 먼저 잡으면 같은 순간 들어온 RUNNING_START와 서로의 잠금을 기다리다 교착에 빠진다
        lockRunningPlayerPort.lockActiveInRoom(roomId);
        RunningRoom room = lockRunningRoomPort.lockById(roomId).orElse(null);
        if (room == null) {
            log.warn("[러닝] 강제 종료 건너뜀: 방 없음 - roomId={}", roomId.value());
            return List.of();
        }
        // 방이 이미 닫혔어도 그냥 빠지지 않는다 — 남들이 정상 종료해 방이 FINISHED가 된 뒤에도
        // 한 번도 붙지 않은 참가자는 JOINED인 채로 남아 다음 러닝을 막는다
        //
        // 판정 기준은 루프 전에 굳힌다. 처리하면서 세면 순회 순서에 따라 뒤쪽만 면제받는다
        boolean penalty = isPenalty(room);
        List<UserId> remaining = room.getSessions().stream()
                .filter(RoomSession::isConnected)
                .map(RoomSession::getUserId)
                .toList();
        List<UserId> runners = new ArrayList<>();
        for (UserId userId : remaining) {
            RunningPlayer player = loadRoomPlayerPort.load(roomId, userId).orElse(null);
            if (player == null || !player.isActive()) {
                // 신청은 이미 닫혔는데 세션만 남은 경우 — 자리만 비운다
                room.finishSession(userId);
                continue;
            }
            if (player.getStatus() == RunningPlayerStatus.RUNNING) {
                runners.add(userId);   // 워커의 러닝 종료가 세션까지 정리한다
                continue;
            }
            leaveWithoutShowing(player, penalty);
            room.finishSession(userId);
        }
        // 인원은 줄이지 않는다 — 면제 판정은 순회 전에 굳힌 인원으로 이미 끝났다.
        // leave()를 쓰면 마지막 한 명에서 방이 CANCELLED가 돼 종료 경로가 무너진다
        //
        // 뛰는 사람이 남았으면 방은 마지막 러너의 확정이 닫는다. 아무도 뛰지 않으면 닫아 줄 확정이 없다
        if (runners.isEmpty()) {
            closeIfStillOpen(room);
        }
        updateRunningRoomPort.update(room);
        return runners;
    }

    // 안 나타난 사람은 확정 후 이탈과 같게 다룬다 — 함께 뛰기로 한 사람을 곤란하게 만든 것은 같다.
    // 상태가 MATCHED_LEFT_*라 쿨다운도 조기 종료가 아니라 이탈 쪽 값을 쓴다
    private void leaveWithoutShowing(RunningPlayer player, boolean penalty) {
        player.leave(penalty, LocalDateTime.now());
        updateRunningPlayerPort.update(player);
        if (penalty) {
            // 근거는 status(MATCHED_LEFT_PENALTY)에 남고, "지금 막혀 있나"는 Redis TTL이 답한다
            startMatchCooldownPort.start(player.getUserId(), matchProperties.cooldown());
        }
    }

    // 러닝 종료와 같은 규칙이다 — 남길 기록이 있으면 FINISHED, 없으면 CANCELLED
    private void closeIfStillOpen(RunningRoom room) {
        if (room.getStatus().isTerminal()) {
            return;   // 마지막 참가자의 러닝 종료가 이미 닫았다 — 콤보 정리도 거기서 걸었다
        }
        RunningRoomId roomId = room.getRunningRoomId().orElseThrow();
        LocalDateTime closedAt = LocalDateTime.now();
        if (existsRunningRecordPort.existsInRoom(roomId)) {
            room.finish(closedAt);
            runningComboArchiver.archiveAfterCommit(roomId.value());
        } else {
            room.cancel(closedAt);
            runningComboArchiver.discardAfterCommit(roomId.value());
        }
    }

    // 마감 시각은 이미 한참 전이라 조건에서 빠진다 — 방 종류와 확정 인원만 남는다
    private boolean isPenalty(RunningRoom room) {
        return room.getType() == RunningRoomType.MATCH
                && room.getPlayerCount().current() >= PENALTY_MIN_PLAYER_COUNT;
    }
}
