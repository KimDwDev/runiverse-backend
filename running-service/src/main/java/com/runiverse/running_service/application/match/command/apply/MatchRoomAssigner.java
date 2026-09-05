package com.runiverse.running_service.application.match.command.apply;

import com.runiverse.running_service.application.match.MatchProperties;
import com.runiverse.running_service.application.match.port.out.CreateMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchCandidatesPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchPlayersPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchCandidate;
import com.runiverse.running_service.application.match.port.out.MatchPlayer;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.exception.RoomNotJoinableException;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;


// 후보 스캔·정렬·합류를 맡는다 — 핸들러는 신청 자격과 신청 생성만 본다
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchRoomAssigner {

    private final LoadMatchCandidatesPort loadMatchCandidatesPort;
    private final LockMatchRoomPort lockMatchRoomPort;
    private final LoadMatchPlayersPort loadMatchPlayersPort;
    private final UpdateMatchRoomPort updateMatchRoomPort;
    private final CreateMatchRoomPort createMatchRoomPort;
    private final MatchProperties matchProperties;

    // 붙을 방이 없으면 1인 방을 새로 연다 — "방 미배정" 상태는 없다(feature-spec).
    // 세션의 키가 유저라 배정에는 둘 다 필요하다 — 유저로 자리를 잡고 신청을 그 자리에 꽂는다
    public RunningRoomId assign(UserId userId, RunningPlayerId playerId, Pace pace,
                                LocalDateTime startAt, int targetDistanceMeters) {
        for (MatchCandidate candidate : ranked(pace, startAt, targetDistanceMeters)) {
            RunningRoomId roomId = new RunningRoomId(candidate.runningRoomId());
            // 잠금 없이 스캔했으므로 그새 자리가 찼을 수 있다 — 확정 직전에 잠그고 다시 읽는다
            Optional<RunningRoom> locked = lockMatchRoomPort.lockById(roomId);
            if (locked.isEmpty()) {
                continue;
            }
            RunningRoom room = locked.get();
            try {
                room.join(userId, playerId, pace);
            } catch (RoomNotJoinableException e) {
                // 스캔과 합류 사이에 마감됐거나 자리가 찼다 — 다음 후보로 넘어간다
                log.debug("후보 방 합류 실패 — roomId={}", roomId.value());
                continue;
            }
            room.recalculateAvgPace(pacesAfterJoin(roomId, pace));
            updateMatchRoomPort.update(room);
            return roomId;
        }
        return openNewRoom(userId, playerId, pace, startAt, targetDistanceMeters);
    }

    // ① 페이스가 가장 가까운 방 ② 그 차이가 임계 안으로 비슷하면 leave_count가 적은 방(feature-spec)
    private List<MatchCandidate> ranked(Pace pace, LocalDateTime startAt, int targetDistanceMeters) {
        int tolerance = matchProperties.paceTieToleranceSecondsPerKm();
        return loadMatchCandidatesPort.loadCandidates(startAt, targetDistanceMeters).stream()
                // 후보 자격(±30초)의 정본은 도메인이다 — 여기서 미리 걸러 헛된 잠금을 줄인다
                .filter(candidate -> pace.isCloseTo(paceOf(candidate)))
                .sorted(Comparator
                        // 차이를 임계 단위로 뭉뚱그려 같은 구간이면 leave_count가 순위를 가르게 한다
                        .comparingInt((MatchCandidate candidate) ->
                                pace.gapTo(paceOf(candidate)) / tolerance)
                        .thenComparingLong(MatchCandidate::totalLeaveCount))
                .toList();
    }

    // 합류자의 세션은 아직 저장 전이라 조회에 안 잡힌다 — 기존 참가자에 합류자를 더해 계산한다
    private List<Pace> pacesAfterJoin(RunningRoomId roomId, Pace joined) {
        List<Pace> paces = new ArrayList<>(loadMatchPlayersPort.loadPlayers(roomId).stream()
                .map(MatchRoomAssigner::paceOf)
                .toList());
        paces.add(joined);
        return paces;
    }

    private RunningRoomId openNewRoom(UserId userId, RunningPlayerId playerId, Pace pace,
                                      LocalDateTime startAt, int targetDistanceMeters) {
        // 1인 방은 창설자 페이스가 곧 방 평균이라 재계산할 것이 없다
        RunningRoom room = createMatchRoomPort.create(RunningRoom.openMatch(
                userId, playerId, pace.secondsPerKm(), targetDistanceMeters, startAt));
        return room.getRunningRoomId().orElseThrow();
    }

    private static Pace paceOf(MatchCandidate candidate) {
        return new Pace(candidate.avgPaceSecondsPerKm());
    }

    private static Pace paceOf(MatchPlayer player) {
        return new Pace(player.avgPaceSecondsPerKm());
    }
}
