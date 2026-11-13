package com.runiverse.running_service.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.ExistsRecordCardByRunningRecordPort;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecordCardPersistenceAdapter implements ExistsRecordCardByRunningRecordPort {

    private final EntityManager entityManager;

    @Override
    public boolean existsByRunningRecord(RunningRecordId runningRecordId) {
        // uk_record_card_running_record(running_record_id)를 탄다 — 기록당 많아야 한 행이다
        return entityManager.createQuery("""
                        SELECT COUNT(card)
                        FROM RecordCardJpaEntity card
                        WHERE card.runningRecord.runningRecordId = :runningRecordId
                        """, Long.class)
                .setParameter("runningRecordId", runningRecordId.value())
                .getSingleResult() > 0;
    }
}
