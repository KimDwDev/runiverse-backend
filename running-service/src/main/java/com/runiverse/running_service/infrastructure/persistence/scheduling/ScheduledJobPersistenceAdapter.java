package com.runiverse.running_service.infrastructure.persistence.scheduling;

import com.runiverse.running_service.application.common.port.out.ClaimScheduledJobPort;
import com.runiverse.running_service.application.common.port.out.LoadPendingJobsPort;
import com.runiverse.running_service.application.common.port.out.SaveScheduledJobPort;
import com.runiverse.running_service.application.common.scheduling.ScheduledJob;
import com.runiverse.running_service.application.common.scheduling.ScheduledJobType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ScheduledJobPersistenceAdapter
        implements SaveScheduledJobPort, LoadPendingJobsPort, ClaimScheduledJobPort {

    private final EntityManager entityManager;

    @Override
    public ScheduledJob save(ScheduledJobType type, String targetId, LocalDateTime executeAt) {
        // 같은 (타입, 대상)이 이미 있으면 그대로 쓴다 — 중복 INSERT로 UNIQUE에 부딪히면
        // 대상 생성 트랜잭션까지 함께 죽는다
        return findBy(type, targetId).orElseGet(() -> {
            ScheduledJobJpaEntity entity =
                    ScheduledJobJpaEntity.create(type, targetId, executeAt);
            entityManager.persist(entity);
            // 타이머를 걸려면 ID가 필요하다 — 커밋까지 기다리지 않고 여기서 채운다
            entityManager.flush();
            return toScheduledJob(entity);
        });
    }

    @Override
    public List<ScheduledJob> loadPending() {
        return entityManager.createQuery("""
                        SELECT j FROM ScheduledJobJpaEntity j
                        WHERE j.sent = false
                        ORDER BY j.executeAt ASC
                        """, ScheduledJobJpaEntity.class)
                .getResultList().stream()
                .map(ScheduledJobPersistenceAdapter::toScheduledJob)
                .toList();
    }

    // 대상 처리와 같은 트랜잭션에 두면 그쪽이 롤백될 때 선점도 풀려 두 번 실행된다.
    // 선점은 독립 트랜잭션으로 먼저 확정한다 — 실패해도 다시 실행되지 않는 쪽을 택한다
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long scheduledJobId) {
        return entityManager.createQuery("""
                        UPDATE ScheduledJobJpaEntity j
                        SET j.sent = true, j.sentAt = :now
                        WHERE j.scheduledJobId = :scheduledJobId AND j.sent = false
                        """)
                .setParameter("now", LocalDateTime.now())
                .setParameter("scheduledJobId", scheduledJobId)
                .executeUpdate() == 1;
    }

    private java.util.Optional<ScheduledJob> findBy(ScheduledJobType type, String targetId) {
        return entityManager.createQuery("""
                        SELECT j FROM ScheduledJobJpaEntity j
                        WHERE j.jobType = :type AND j.targetId = :targetId
                        """, ScheduledJobJpaEntity.class)
                .setParameter("type", type)
                .setParameter("targetId", targetId)
                .getResultStream()
                .findFirst()
                .map(ScheduledJobPersistenceAdapter::toScheduledJob);
    }

    private static ScheduledJob toScheduledJob(ScheduledJobJpaEntity entity) {
        return new ScheduledJob(entity.getScheduledJobId(), entity.getJobType(),
                entity.getTargetId(), entity.getExecuteAt());
    }
}
