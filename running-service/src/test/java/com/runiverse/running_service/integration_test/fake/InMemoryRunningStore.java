package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.CreateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.CreateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.ExistsActiveRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.ExistsRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadActiveRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

// RunningPersistenceAdapter를 대신한다 — 실제 어댑터처럼 bigserial ID를 채워 돌려준다
public class InMemoryRunningStore implements CreateRunningPlayerPort, CreateRunningRoomPort,
        ExistsActiveRunningPlayerPort, LoadRunningRoomPort, UpdateRunningRoomPort,
        LoadActiveRunningPlayerPort, UpdateRunningPlayerPort, LoadRoomPlayerPort,
        ExistsRunningPlayerPort {

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
        RunningPlayer saved = copyWithId(player, id);
        players.put(id, saved);
        return saved;
    }

    @Override
    public RunningRoom create(RunningRoom room) {
        if (!room.isNew()) {
            throw new IllegalStateException("이미 저장된 방이다 — 상태 변경은 별도 포트로 처리한다");
        }
        long id = nextRoomId++;
        RunningRoom saved = copyWithId(room, id);
        rooms.put(id, saved);
        return saved;
    }

    // 실제 쿼리와 같이 deleted_at IS NULL인 신청만 "진행 중"으로 본다
    @Override
    public boolean existsActive(UserId userId) {
        return players.values().stream()
                .anyMatch(player -> player.getUserId().equals(userId) && player.isActive());
    }

    // 어댑터의 toDomain처럼 분리된 객체를 돌려준다 —
    // 저장본을 그대로 주면 update()를 빼먹어도 변경이 반영돼 테스트가 거짓으로 통과한다
    @Override
    public Optional<RunningRoom> loadById(RunningRoomId runningRoomId) {
        return Optional.ofNullable(rooms.get(runningRoomId.value()))
                .map(room -> copyWithId(room, runningRoomId.value()));
    }

    @Override
    public Optional<RunningPlayer> loadActive(UserId userId) {
        return players.values().stream()
                .filter(player -> player.getUserId().equals(userId) && player.isActive())
                .findFirst()
                .map(player -> copyWithId(player, player.getRunningPlayerId().orElseThrow().value()));
    }

    // 실제 어댑터처럼 세션을 거쳐 방의 참가자를 찾는다.
    // deleted_at은 보지 않는다 — 이미 종료된 참가자도 찾아야 RUNNING_FINISH가 멱등이 된다
    @Override
    public Optional<RunningPlayer> load(RunningRoomId runningRoomId, UserId userId) {
        return playersOf(runningRoomId)
                .filter(player -> player.getUserId().equals(userId))
                .findFirst()
                .map(player -> copyWithId(player, player.getRunningPlayerId().orElseThrow().value()));
    }

    @Override
    public boolean existsRunning(RunningRoomId runningRoomId) {
        return playersOf(runningRoomId)
                .anyMatch(player -> player.getStatus() == RunningPlayerStatus.RUNNING);
    }

    private Stream<RunningPlayer> playersOf(RunningRoomId runningRoomId) {
        RunningRoom room = rooms.get(runningRoomId.value());
        if (room == null) {
            return Stream.empty();
        }
        return room.getSessions().stream()
                .map(session -> players.get(session.getRunningPlayerId().value()))
                .filter(Objects::nonNull);
    }

    @Override
    public void update(RunningRoom room) {
        long id = room.getRunningRoomId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 방은 갱신할 수 없다"))
                .value();
        if (!rooms.containsKey(id)) {
            throw new IllegalStateException("없는 방은 갱신할 수 없다");
        }
        rooms.put(id, copyWithId(room, id));
    }

    @Override
    public void update(RunningPlayer player) {
        long id = player.getRunningPlayerId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 신청은 갱신할 수 없다"))
                .value();
        if (!players.containsKey(id)) {
            throw new IllegalStateException("없는 신청은 갱신할 수 없다");
        }
        players.put(id, copyWithId(player, id));
    }

    private RunningPlayer copyWithId(RunningPlayer player, long id) {
        return RunningPlayer.builder()
                .runningPlayerId(id)
                .userId(player.getUserId().value())
                .status(player.getStatus())
                .avgPace(player.getAvgPace().secondsPerKm())
                .targetDistance(player.getTargetDistance().meters())
                .desiredPlayerCount(player.getDesiredPlayerCount().value())
                .startAt(player.getStartAt())
                .deletedAt(player.getDeletedAt().orElse(null))
                .build();
    }

    private RunningRoom copyWithId(RunningRoom room, long id) {
        List<SessionDraft> sessions = new ArrayList<>();
        room.getSessions().forEach(session -> sessions.add(new SessionDraft(
                session.getRunningPlayerId(), session.getLeaveCount().value(), session.isConnected())));
        return RunningRoom.builder()
                .runningRoomId(id)
                .type(room.getType())
                .status(room.getStatus())
                .startAt(room.getStartAt())
                .closeAt(room.getCloseAt().orElse(null))
                .targetDistance(room.getTargetDistance().map(Distance::meters).orElse(null))
                .avgPace(room.getAvgPace().map(Pace::secondsPerKm).orElse(null))
                .currentPlayerCount(room.getPlayerCount().current())
                .maxPlayerCount(room.getPlayerCount().max())
                .sessions(sessions)
                .build();
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
