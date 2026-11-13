package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardDraftPort;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Redis 초안 — 만료는 흉내 내지 않고 받은 유효 시간만 기록한다
public class InMemoryRecordCardDraftStore implements SaveRecordCardDraftPort {

    private final Map<UUID, RecordCardDraft> drafts = new HashMap<>();
    private final Map<UUID, Duration> ttls = new HashMap<>();

    @Override
    public void save(RecordCardDraft draft, Duration ttl) {
        drafts.put(draft.recordCardDraftId(), draft);
        ttls.put(draft.recordCardDraftId(), ttl);
    }

    // 아래는 검증 전용
    public Optional<RecordCardDraft> find(UUID recordCardDraftId) {
        return Optional.ofNullable(drafts.get(recordCardDraftId));
    }

    public Duration ttlOf(UUID recordCardDraftId) {
        return ttls.get(recordCardDraftId);
    }

    public boolean isEmpty() {
        return drafts.isEmpty();
    }
}
