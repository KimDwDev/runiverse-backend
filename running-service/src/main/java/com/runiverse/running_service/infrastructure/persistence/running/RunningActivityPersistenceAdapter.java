package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.running.port.out.FinishedRunRow;
import com.runiverse.running_service.application.running.port.out.LoadRunningActivityPort;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// 홈 공지 띠의 집계만 담는다 — 참가자·기록 애그리거트의 저장은 각자의 어댑터 몫이다
@Component
@RequiredArgsConstructor
public class RunningActivityPersistenceAdapter implements LoadRunningActivityPort {

    private static final String SELECT_FINISHED_RUN = """
            SELECT NEW com.runiverse.running_service.application.running.port.out.FinishedRunRow(
                r.runningRecordId, o.nickname, COALESCE(room.targetDistance, r.totalDistance))
            """;
    private static final String SELECT_FINISHER_COUNT = """
            SELECT COUNT(DISTINCT r.userId)
            """;
    // 완주 판정은 러닝 종료의 상태 확정과 같은 값으로 한다 — 기록 거리는 목표에서 끊긴 확정 거리다.
    // 숨긴 방의 기록은 기록 목록에도 나오지 않아 뺀다. 온보딩 행은 탈퇴하면 지워져 INNER JOIN이 탈퇴자를 거른다
    private static final String FROM_FINISHED_RUNS = """
            FROM RunningRecordJpaEntity r
            JOIN r.room room
            JOIN UserOnboardingJpaEntity o ON o.userId = r.userId
            WHERE r.createdAt >= :fromInclusive
              AND r.createdAt < :toExclusive
              AND room.deletedAt IS NULL
              AND (room.targetDistance IS NULL OR r.totalDistance >= room.targetDistance)
            """;
    // 같은 시각에 저장된 기록끼리 순서가 바뀌면 앱이 같은 소식을 새 소식으로 읽는다 — ID로 순서를 고정한다
    private static final String ORDER_BY_LATEST = " ORDER BY r.createdAt DESC, r.runningRecordId DESC";

    private final EntityManager entityManager;

    @Override
    public int countRunningPlayers(LocalDateTime startedAfter) {
        // JPQL의 COUNT는 Long이다
        return entityManager.createQuery("""
                        SELECT COUNT(DISTINCT p.userId)
                        FROM RunningPlayerJpaEntity p
                        WHERE p.status = :status
                          AND p.startAt > :startedAfter
                        """, Long.class)
                .setParameter("status", RunningPlayerStatus.RUNNING)
                .setParameter("startedAfter", startedAfter)
                .getSingleResult()
                .intValue();
    }

    @Override
    public List<FinishedRunRow> loadLatestFinishedRuns(LocalDateTime fromInclusive, LocalDateTime toExclusive,
                                                       int limit) {
        String jpql = SELECT_FINISHED_RUN + FROM_FINISHED_RUNS + ORDER_BY_LATEST;
        return entityManager.createQuery(jpql, FinishedRunRow.class)
                .setParameter("fromInclusive", fromInclusive)
                .setParameter("toExclusive", toExclusive)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public int countFinishers(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        String jpql = SELECT_FINISHER_COUNT + FROM_FINISHED_RUNS;
        return entityManager.createQuery(jpql, Long.class)
                .setParameter("fromInclusive", fromInclusive)
                .setParameter("toExclusive", toExclusive)
                .getSingleResult()
                .intValue();
    }
}
