package com.runiverse.running_service.application.running.command.forcefinish;

import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.running.port.in.ForceFinishRunningRoomUsecase;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 시작 시각 + 유예 — 그때까지 닫히지 않은 방을 서버가 닫는다. 예약은 '알람'이다(D-83).
// 잠금 없이 보고 고칠 것이 있을 때만 잠근다 → 뛰던 사람이 남았으면 1분 뒤 다시 깨 달라고 답하고 러너마다 워커에 넘긴다.
// 다 끝났는지는 다음 알람이 DB로 본다. 방이 열린 채로 남으면 참가자의 신청도 활성이라 다음 러닝이 영영 막힌다.
// 트랜잭션은 여기서 열지 않는다 — 예약 실행기(또는 리스너)의 트랜잭션 안에서 돈다
@Slf4j
@Service
@RequiredArgsConstructor
public class ForceFinishRunningRoomHandler implements ForceFinishRunningRoomUsecase {

    private final LoadRunningRoomPort loadRunningRoomPort;
    private final LoadRoomPlayerPort loadRoomPlayerPort;
    private final ForceFinishSteps forceFinishSteps;
    private final ForceFinishRunnerDispatcher forceFinishRunnerDispatcher;
    private final MatchProperties matchProperties;
    private final ForceFinishProperties forceFinishProperties;

    @Override
    public Optional<LocalDateTime> handle(ForceFinishRunningRoomCommand command) {
        RunningRoomId roomId = new RunningRoomId(command.runningRoomId());
        // 1. 잠금 없이 본다 — 상태는 앞으로만 가서(JOINED → RUNNING → 끝남, STARTED → 닫힘)
        //    틀려도 '남았다고 봤는데 방금 끝남'뿐이고, 그 결과는 헛워커 하나와 알람 한 번이다
        Optional<RunningRoom> loaded = loadRunningRoomPort.loadById(roomId);
        if (loaded.isEmpty()) {
            log.warn("[러닝] 강제 종료 건너뜀: 방 없음 - roomId={}", roomId.value());
            return Optional.empty();
        }
        RunningRoom room = loaded.get();
        Snapshot snapshot = look(roomId, room);
        // 2. 고칠 것이 있을 때만 잠근다 — 잠그고 다시 읽은 값으로 미출석 이탈·방 닫기를 한다
        List<UserId> runners = snapshot.needsSettle()
                ? forceFinishSteps.settle(roomId)
                : snapshot.runners();
        // 3. 모두 끝났다 — 예약을 완료로 적는다
        if (runners.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime giveUpAt = room.getStartAt()
                .plus(matchProperties.forceFinishOffset())
                .plus(forceFinishProperties.retryWindow());
        if (now.isAfter(giveUpAt)) {
            // 트랙 보관 기간이 지나 상태만 확정될 러너도 끝내지 못했다 — 다시 해도 풀리지 않는 오류다
            log.error("[러닝] 강제 종료 포기: 다시 걸기 기간 초과 - roomId={}, remainingRunnerCount={}",
                    roomId.value(), runners.size());
            return Optional.empty();
        }
        // 4. 뛰는 사람이 남았다 — 먼저 다시 걸고(커밋되는 반환값), 커밋 뒤 러너마다 워커로 넘긴다.
        //    마지막 러너의 확정이 방을 닫고, 다음 알람이 다 끝난 것을 보고 완료로 적는다
        forceFinishRunnerDispatcher.dispatchAfterCommit(roomId.value(), runners);
        return Optional.of(now.plus(forceFinishProperties.retryInterval()));
    }

    // 미출석자(JOINED)·신청이 닫혔는데 남은 자리가 있거나, 방이 열려 있는데 뛰는 사람이 없으면 고칠 것이 있다
    private Snapshot look(RunningRoomId roomId, RunningRoom room) {
        List<UserId> runners = new ArrayList<>();
        boolean needsSettle = false;
        for (RoomSession session : room.getSessions()) {
            if (!session.isConnected()) {
                continue;
            }
            Optional<RunningPlayer> player = loadRoomPlayerPort.load(roomId, session.getUserId());
            if (player.isPresent() && player.get().isActive()
                    && player.get().getStatus() == RunningPlayerStatus.RUNNING) {
                runners.add(session.getUserId());
            } else {
                needsSettle = true;
            }
        }
        if (runners.isEmpty() && !room.getStatus().isTerminal()) {
            needsSettle = true;
        }
        return new Snapshot(runners, needsSettle);
    }

    private record Snapshot(List<UserId> runners, boolean needsSettle) {
    }
}
