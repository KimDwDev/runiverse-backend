package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.match.port.out.LoadMatchPlayersPort;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.MatchPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// 매칭 단계의 조회만 담는다 — 방·참가자 애그리거트의 저장은 RunningPersistenceAdapter 몫이다
@Component
@RequiredArgsConstructor
public class MatchPersistenceAdapter implements LoadMatchRoomPort, LoadMatchPlayersPort {

    private final EntityManager entityManager;

    @Override
    public Optional<RunningRoomId> findAssignedRoom(RunningPlayerId runningPlayerId) {
        return entityManager.createQuery(
                        """
                                SELECT s.room.runningRoomId
                                FROM RunningRoomSessionJpaEntity s
                                WHERE s.player.runningPlayerId = :runningPlayerId
                                  AND s.connected = true
                                """, Long.class
                )
                .setParameter("runningPlayerId", runningPlayerId.value())
                // 현재 배정된 행은 하나다(erd) — 어긋나도 깨지지 않게 첫 건만 쓴다
                .getResultStream()
                .findFirst()
                .map(RunningRoomId::new);
    }

    @Override
    public List<MatchPlayer> loadPlayers(RunningRoomId runningRoomId) {
        return entityManager.createQuery(
                        """
                                SELECT new com.runiverse.running_service.application.match.port.out.MatchPlayer(
                                    p.userId, p.avgPace)
                                FROM RunningRoomSessionJpaEntity s
                                JOIN s.player p
                                WHERE s.room.runningRoomId = :runningRoomId
                                  AND s.connected = true
                                ORDER BY p.runningPlayerId
                                """, MatchPlayer.class
                )
                .setParameter("runningRoomId", runningRoomId.value())
                .getResultList();
    }
}
