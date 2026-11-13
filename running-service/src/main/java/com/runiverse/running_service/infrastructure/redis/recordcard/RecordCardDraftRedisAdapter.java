package com.runiverse.running_service.infrastructure.redis.recordcard;

import com.runiverse.running_service.application.recordcard.port.out.RecordCardDraft;
import com.runiverse.running_service.application.recordcard.port.out.SaveRecordCardDraftPort;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.UUID;

// 기록 카드 초안 — 확정(9-2) 전 작성 내용. 유효 시간이 지나면 Redis가 지운다
@Component
@RequiredArgsConstructor
public class RecordCardDraftRedisAdapter implements SaveRecordCardDraftPort {

    private static final String DRAFT = "draft";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    // 초안 모델을 JSON으로 그대로 둔다 — 값이 30분만 살아서 모델이 바뀌어도 옮길 옛 데이터가 없다.
    // 실패하면 그대로 던진다 — 저장하지 못한 초안으로 업로드 URL을 주면 앱은 확정할 수 없는 사진을 올린다
    @Override
    public void save(RecordCardDraft draft, Duration ttl) {
        redisTemplate.opsForValue().set(key(draft.recordCardDraftId()), jsonMapper.writeValueAsString(draft), ttl);
    }

    private String key(UUID recordCardDraftId) {
        return RedisKey.RECORD_CARD.of(DRAFT, recordCardDraftId.toString());
    }
}
