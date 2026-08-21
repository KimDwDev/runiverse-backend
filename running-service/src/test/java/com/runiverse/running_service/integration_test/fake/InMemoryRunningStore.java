package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.CreateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.CreateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.ExistsActiveRunningPlayerPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// RunningPersistenceAdapter를 대신한다 — 실제 어댑터처럼 bigserial ID를 채워 돌려준다
public class InMemoryRunningStore implements CreateRunningPlayerPort, CreateRunningRoomPort,
        ExistsActiveRunningPlayerPort {

    private final Map<Long, RunningPlayer> players = new LinkedHashMap<>();
    private final Map<Long, RunningRoom> rooms = new LinkedHashMap<>();
    // bigserial은 1부터 시작한다
    private long nextPlayerId = 1L;
    private long nextRoomId = 1L;

    @Override
    public RunningPlayer create(RunningPlayer player) {
        if (!player.isNew()) {
            throw new IllegalStateException("이미 저장된 신청이다 — 종료·상태 변경은 별도 포트로 처리한다");
        }
        long id = nextPlayerId++;
        RunningPlayer saved = RunningPlayer.builder()
                .runningPlayerId(id)
                .userId(player.getUserId().value())
                .status(player.getStatus())
                .avgPace(player.getAvgPace().secondsPerKm())
                .targetDistance(player.getTargetDistance().meters())
                .desiredPlayerCount(player.getDesiredPlayerCount().value())
                .startAt(player.getStartAt())
                .deletedAt(player.getDeletedAt().orElse(null))
                .build();
        players.put(id, saved);
        return saved;
    }

    @Override
    public RunningRoom create(RunningRoom room) {
        if (!room.isNew()) {
            throw new IllegalStateException("이미 저장된 방이다 — 상태 변경은 별도 포트로 처리한다");
        }
        long id = nextRoomId++;
        List<SessionDraft> sessions = new ArrayList<>();
        room.getSessions().forEach(session -> sessions.add(new SessionDraft(
                session.getRunningPlayerId(), session.getLeaveCount().value(), session.isConnected())));

        RunningRoom saved = RunningRoom.builder()
                .runningRoomId(id)
                .type(room.getType())
                .status(room.getStatus())
                .startAt(room.getStartAt())
                .closeAt(room.getCloseAt().orElse(null))
                .targetDistance(room.getTargetDistance().map(Distance::meters).orElse(null))
                .avgPace(room.getAvgPace().secondsPerKm())
                .currentPlayerCount(room.getPlayerCount().current())
                .maxPlayerCount(room.getPlayerCount().max())
                .sessions(sessions)
                .build();
        rooms.put(id, saved);
        return saved;
    }

    // 실제 쿼리와 같이 deleted_at IS NULL인 신청만 "진행 중"으로 본다
    @Override
    public boolean existsActive(UserId userId) {
        return players.values().stream()
                .anyMatch(player -> player.getUserId().equals(userId) && player.isActive());
    }

    // 검증 전용
    public Optional<RunningRoom> findRoom(Long runningRoomId) {
        return Optional.ofNullable(rooms.get(runningRoomId));
    }

    public Optional<RunningPlayer> findPlayer(Long runningPlayerId) {
        return Optional.ofNullable(players.get(runningPlayerId));
    }

    public int playerCount() {
        return players.size();
    }

    public int roomCount() {
        return rooms.size();
    }
}
