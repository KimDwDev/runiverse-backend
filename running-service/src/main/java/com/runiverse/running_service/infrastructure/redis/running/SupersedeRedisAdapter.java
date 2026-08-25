package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.PublishSupersedePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SupersedeRedisAdapter implements PublishSupersedePort {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(UUID userId, String winnerSessionId) {
        redisTemplate.convertAndSend(
                RunningChannel.SUPERSEDE,
                jsonMapper.writeValueAsString(new SupersedeMessage(userId, winnerSessionId))
        );
    }
}
