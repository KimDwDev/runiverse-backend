package com.runiverse.running_service.infrastructure.redis.running;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupersedeListener implements MessageListener {

    private final JsonMapper jsonMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        SupersedeMessage payload;
        try {
            payload = jsonMapper.readValue(message.getBody(), SupersedeMessage.class);
        } catch (JacksonException e) {
            log.warn("밀어내기 메시지 파싱 실패");
            return;
        }
        log.info("밀어내기 수신 - userId={}, winner={}", payload.userId(), payload.winnerSessionId());
    }
}
