package com.runiverse.running_service.infrastructure.persistence.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardByRunningRecordPort;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RecordCardPersistenceAdapter implements FindRecordCardByRunningRecordPort {

    private final EntityManager entityManager;

    @Override
    public Optional<ExistingRecordCard> findByRunningRecord(RunningRecordId runningRecordId) {
        // uk_record_card_running_record(running_record_id)를 탄다 — 지운 카드도 행이 남아 많아야 한 행이다.
        // 엔티티 전체가 아니라 ID와 삭제 여부만 읽는다
        return entityManager.createQuery("""
                        SELECT card.recordCardId AS recordCardId, card.deletedAt AS deletedAt
                        FROM RecordCardJpaEntity card
                        WHERE card.runningRecord.runningRecordId = :runningRecordId
                        """, Tuple.class)
                .setParameter("runningRecordId", runningRecordId.value())
                .getResultStream()
                .findFirst()
                .map(row -> new ExistingRecordCard(
                        new RecordCardId(row.get("recordCardId", UUID.class)),
                        row.get("deletedAt", LocalDateTime.class) != null));
    }
}
