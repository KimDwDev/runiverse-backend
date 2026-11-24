package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.FinishLeasePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FinishLeaseRedisAdapter implements FinishLeasePort {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = releaseScript();
    private final StringRedisTemplate redisTemplate;
    private final FinishLeaseProperties properties;

    // SET NX PX — 다른 인스턴스로 다시 붙은 연결의 끝내기도 같은 키를 본다.
    // TTL은 봉인 중 프로세스가 죽었을 때만 쓰인다 — 정상 경로는 지휘자의 finally가 지운다
    @Override
    public Optional<String> tryAcquire(Long runningRoomId, UserId userId) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key(runningRoomId, userId), token, properties.ttl());
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    // 확인과 삭제를 한 번에 한다 — 나눠 하면 그 사이 TTL이 지나 남이 잡은 임대를 지울 수 있다
    @Override
    public void release(Long runningRoomId, UserId userId, String token) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key(runningRoomId, userId)), token);
    }

    private static String key(Long runningRoomId, UserId userId) {
        return RedisKey.RUNNING_FINISH_LEASE.of(String.valueOf(runningRoomId), userId.value().toString());
    }

    private static DefaultRedisScript<Long> releaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("scripts/release_finish_lease.lua")));
        script.setResultType(Long.class);
        return script;
    }
}
