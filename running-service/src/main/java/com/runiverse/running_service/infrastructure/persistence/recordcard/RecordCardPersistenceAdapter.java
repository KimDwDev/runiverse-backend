package com.runiverse.running_service.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardIdByRunningRecordPort;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RecordCardPersistenceAdapter implements FindRecordCardIdByRunningRecordPort {

    private final EntityManager entityManager;

    @Override
    public Optional<RecordCardId> findIdByRunningRecord(RunningRecordId runningRecordId) {
        // uk_record_card_running_record(running_record_id)를 탄다 — 기록당 많아야 한 행이다. 엔티티 대신 ID만 읽는다
        return entityManager.createQuery("""
                        SELECT card.recordCardId
                        FROM RecordCardJpaEntity card
                        WHERE card.runningRecord.runningRecordId = :runningRecordId
                        """, UUID.class)
                .setParameter("runningRecordId", runningRecordId.value())
                .getResultStream()
                .findFirst()
                .map(RecordCardId::new);
    }
}
