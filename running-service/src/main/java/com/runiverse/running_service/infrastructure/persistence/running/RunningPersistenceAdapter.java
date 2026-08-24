package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.running.port.out.CreateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.CreateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.ExistsActiveRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadActiveRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RunningPersistenceAdapter implements CreateRunningPlayerPort, CreateRunningRoomPort,
        ExistsActiveRunningPlayerPort, LoadRunningRoomPort, UpdateRunningRoomPort,
        LoadActiveRunningPlayerPort, UpdateRunningPlayerPort {

    private final EntityManager entityManager;

    @Override
    public RunningPlayer create(RunningPlayer player) {
        if (!player.isNew()) {
            throw new IllegalStateException("이미 저장된 신청이다 — 종료·상태 변경은 별도 포트로 처리한다");
        }
        RunningPlayerJpaEntity entity = RunningPlayerJpaEntity.create(
                player.getUserId().value(),
                player.getStatus(),
                player.getAvgPace().secondsPerKm(),
                player.getTargetDistance().meters(),
                player.getDesiredPlayerCount().value(),
                player.getStartAt()
        );
        // IDENTITY 전략이라 persist 시점에 INSERT가 나가고 ID가 채워진다 —
        // 방의 세션이 이 ID를 참조하므로 여기서 확보돼야 한다
        entityManager.persist(entity);
        return toDomain(entity);
    }

    @Override
    public RunningRoom create(RunningRoom room) {
        if (!room.isNew()) {
            throw new IllegalStateException("이미 저장된 방이다 — 상태 변경은 별도 포트로 처리한다");
        }
        RunningRoomJpaEntity roomEntity = RunningRoomJpaEntity.create(
                room.getType(),
                room.getStatus(),
                room.getStartAt(),
                room.getTargetDistance().map(Distance::meters).orElse(null),
                room.getAvgPace().map(Pace::secondsPerKm).orElse(null),
                room.getPlayerCount().current(),
                room.getPlayerCount().max()
        );
        entityManager.persist(roomEntity);
        // 세션은 방 애그리거트의 내부 엔티티라 별도 포트 없이 여기서 함께 저장한다.
        // 플레이어는 이미 저장돼 있으니 프록시만 잡고 조회는 하지 않는다
        List<RunningRoomSessionJpaEntity> sessions = room.getSessions().stream()
                .map(session -> RunningRoomSessionJpaEntity.create(
                        roomEntity,
                        entityManager.getReference(RunningPlayerJpaEntity.class,
                                session.getRunningPlayerId().value()),
                        session.getLeaveCount().value(),
                        session.isConnected()))
                .toList();
        sessions.forEach(entityManager::persist);
        return toDomain(roomEntity, sessions);
    }

    @Override
    public boolean existsActive(UserId userId) {
        Long count = entityManager.createQuery(
                        """
                                SELECT COUNT(p)
                                FROM RunningPlayerJpaEntity p
                                WHERE p.userId = :userId
                                  AND p.deletedAt IS NULL
                                """, Long.class
                )
                .setParameter("userId", userId.value())
                .getSingleResult();
        return count > 0;
    }

    @Override
    public Optional<RunningRoom> loadById(RunningRoomId runningRoomId) {
        return entityManager.createQuery(
                        """
                                SELECT r
                                FROM RunningRoomJpaEntity r
                                WHERE r.runningRoomId = :runningRoomId
                                  AND r.deletedAt IS NULL
                                """, RunningRoomJpaEntity.class
                )
                .setParameter("runningRoomId", runningRoomId.value())
                .getResultStream()
                .findFirst()
                // 세션 없이는 "이 방 참가자인가"를 판정할 수 없다 — 방과 항상 함께 복원한다
                .map(entity -> toDomain(entity, loadSessions(entity)));
    }

    @Override
    public Optional<RunningPlayer> loadActive(UserId userId) {
        return entityManager.createQuery(
                        """
                                SELECT p
                                FROM RunningPlayerJpaEntity p
                                WHERE p.userId = :userId
                                  AND p.deletedAt IS NULL
                                """, RunningPlayerJpaEntity.class
                )
                .setParameter("userId", userId.value())
                // 앱이 한 개만 보장하고 DB는 강제하지 않는다 — 여럿이어도 깨지지 않게 첫 건만 쓴다
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public void update(RunningRoom room) {
        Long roomId = room.getRunningRoomId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 방은 갱신할 수 없다"))
                .value();
        // 영속 상태로 올려두면 더티 체킹이 UPDATE를 만든다
        RunningRoomJpaEntity entity = entityManager.find(RunningRoomJpaEntity.class, roomId);
        entity.changeStatus(room.getStatus());
        entity.changeCloseAt(room.getCloseAt().orElse(null));
        entity.changeAvgPace(room.getAvgPace().map(Pace::secondsPerKm).orElse(null));
        entity.changeCurrentPlayerCount(room.getPlayerCount().current());
        // 세션은 방 애그리거트의 내부 엔티티라 별도 포트 없이 여기서 함께 반영한다
        Map<Long, RunningRoomSessionJpaEntity> stored = loadSessions(entity).stream()
                .collect(Collectors.toMap(RunningRoomSessionJpaEntity::playerId,
                        session -> session));
        room.getSessions().forEach(session -> {
            Long playerId = session.getRunningPlayerId().value();
            RunningRoomSessionJpaEntity target = stored.get(playerId);
            if (target == null) {
                // 합류로 새로 생긴 관계 — 방은 이미 저장돼 있으니 여기서 만든다.
                // 플레이어도 저장돼 있으므로 프록시만 잡고 조회는 하지 않는다
                entityManager.persist(RunningRoomSessionJpaEntity.create(
                        entity,
                        entityManager.getReference(RunningPlayerJpaEntity.class, playerId),
                        session.getLeaveCount().value(),
                        session.isConnected()));
                return;
            }
            target.changeLeaveCount(session.getLeaveCount().value());
            target.changeConnected(session.isConnected());
        });
    }

    @Override
    public void update(RunningPlayer player) {
        Long playerId = player.getRunningPlayerId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 신청은 갱신할 수 없다"))
                .value();
        RunningPlayerJpaEntity entity = entityManager.find(RunningPlayerJpaEntity.class, playerId);
        entity.changeStatus(player.getStatus());
        entity.changeDeletedAt(player.getDeletedAt().orElse(null));
    }

    private List<RunningRoomSessionJpaEntity> loadSessions(RunningRoomJpaEntity room) {
        return entityManager.createQuery(
                        """
                                SELECT s
                                FROM RunningRoomSessionJpaEntity s
                                WHERE s.room = :room
                                """, RunningRoomSessionJpaEntity.class
                )
                .setParameter("room", room)
                .getResultList();
    }

    private RunningPlayer toDomain(RunningPlayerJpaEntity entity) {
        return RunningPlayer.builder()
                .runningPlayerId(entity.getRunningPlayerId())
                .userId(entity.getUserId())
                .status(entity.getStatus())
                .avgPace(entity.getAvgPace())
                .targetDistance(entity.getTargetDistance())
                .desiredPlayerCount(entity.getDesiredPlayerCount())
                .startAt(entity.getStartAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    private RunningRoom toDomain(RunningRoomJpaEntity entity,
                                 List<RunningRoomSessionJpaEntity> sessions) {
        return RunningRoom.builder()
                .runningRoomId(entity.getRunningRoomId())
                .type(entity.getType())
                .status(entity.getStatus())
                .startAt(entity.getStartAt())
                .closeAt(entity.getCloseAt())
                .targetDistance(entity.getTargetDistance())
                .avgPace(entity.getAvgPace())
                .currentPlayerCount(entity.getCurrentPlayerCount())
                .maxPlayerCount(entity.getMaxPlayerCount())
                .sessions(sessions.stream()
                        .map(s -> new SessionDraft(new RunningPlayerId(s.playerId()),
                                s.getLeaveCount(), s.isConnected()))
                        .toList())
                .build();
    }
}
