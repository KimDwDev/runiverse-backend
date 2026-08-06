package com.runiverse.running_service.infrastructure.redis;

import com.runiverse.running_service.application.auth.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmailVerificationRedisAdapter implements AcquireSendCooldownPort, ReleaseSendCooldownPort,
        CheckDailySendLimitPort, SaveVerificationCodePort, DeleteVerificationCodePort {
    private final StringRedisTemplate redisTemplate;
    private final EmailVerificationProperties properties;
    private static final String EMAIL_VERIFICATION = "email_verification";
    private static final String COOLDOWN = "cooldown";
    private static final String DAILY = "daily";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_ATTEMPTS = "attempts";
    private static final String OCCUPIED = "1";
    private static final String INITIAL_ATTEMPTS = "0";
    private static final Duration DAILY_TTL = Duration.ofDays(1);

    // 쿨다운을 선점한다. 이미 있으면 실패한다 (SET NX EX)
    @Override
    public boolean tryAcquire(String email) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey(email), OCCUPIED, properties.cooldown());
        return Boolean.TRUE.equals(acquired);
    }
    // 발송에 실패한 경우 쿨다운을 되돌린다
    @Override
    public void release(String email) {
        redisTemplate.delete(cooldownKey(email));
    }
    // 일일 발송 횟수를 소비한다. 한도를 넘으면 실패한다
    @Override
    public boolean tryConsume(String email) {
        String key = dailyKey(email);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) return false;
        // 첫 요청일 때만 만료를 건다. 매번 걸면 창이 닫히지 않는다
        if (count == 1L) redisTemplate.expire(key, DAILY_TTL);
        return count <= properties.dailyLimit();
    }
    // 해시된 코드를 저장한다. 재발송이면 이전 코드와 시도 횟수를 덮어쓴다
    @Override
    public void save(String email, String hashedCode) {
        String key = codeKey(email);
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        hashOps.putAll(key, Map.of(
                FIELD_CODE, hashedCode,
                FIELD_ATTEMPTS, INITIAL_ATTEMPTS
        ));
        redisTemplate.expire(key, properties.codeTtl());
    }
    @Override
    public void delete(String email) {
        redisTemplate.delete(codeKey(email));
    }
    private String codeKey(String email) {
        return RedisKey.USER.of(EMAIL_VERIFICATION, email);
    }
    private String cooldownKey(String email) {
        return RedisKey.USER.of(EMAIL_VERIFICATION, COOLDOWN, email);
    }
    private String dailyKey(String email) {
        return RedisKey.USER.of(EMAIL_VERIFICATION, DAILY, email);
    }
}
