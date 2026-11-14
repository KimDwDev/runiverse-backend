package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.recordcard.port.out.ExistsOwnRunningRecordPort;
import com.runiverse.running_service.application.recordcard.port.out.FindRecordCardIdByRunningRecordPort;
import com.runiverse.running_service.domain.common.vo.RunningRecordId;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 카드를 붙일 기록과 그 기록의 카드 — 기록 주인·숨긴 방과 카드가 있는지만 흉내 낸다
public class InMemoryRecordCardStore implements ExistsOwnRunningRecordPort, FindRecordCardIdByRunningRecordPort {

    private record StoredRecord(UUID userId, boolean roomHidden) {

    }

    private final Map<Long, StoredRecord> records = new HashMap<>();
    private final Map<Long, UUID> cardIdsByRecord = new HashMap<>();

    @Override
    public boolean existsOwnedBy(RunningRecordId runningRecordId, UserId userId) {
        StoredRecord record = records.get(runningRecordId.value());
        return record != null && record.userId().equals(userId.value()) && !record.roomHidden();
    }

    @Override
    public Optional<RecordCardId> findIdByRunningRecord(RunningRecordId runningRecordId) {
        return Optional.ofNullable(cardIdsByRecord.get(runningRecordId.value())).map(RecordCardId::new);
    }

    // 아래는 준비 전용
    public void addRecord(long runningRecordId, UUID userId) {
        records.put(runningRecordId, new StoredRecord(userId, false));
    }

    public void addRecordInHiddenRoom(long runningRecordId, UUID userId) {
        records.put(runningRecordId, new StoredRecord(userId, true));
    }

    public void addCard(long runningRecordId, UUID recordCardId) {
        cardIdsByRecord.put(runningRecordId, recordCardId);
    }
}
