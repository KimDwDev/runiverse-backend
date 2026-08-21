package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.running.port.out.CreateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.CreateRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.ExistsActiveRunningPlayerPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RunningPersistenceAdapter implements CreateRunningPlayerPort, CreateRunningRoomPort,
        ExistsActiveRunningPlayerPort {

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
                room.getCloseAt().orElse(null),
                room.getTargetDistance().map(Distance::meters).orElse(null),
                room.getAvgPace().secondsPerKm(),
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
