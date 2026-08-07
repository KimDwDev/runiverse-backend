package com.runiverse.running_service.infrastructure.redis;

import com.runiverse.running_service.application.auth.port.out.SaveVerificationTicketPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationTicketRedisAdapter implements SaveVerificationTicketPort {
    private final StringRedisTemplate redisTemplate;
    private final EmailVerificationProperties properties;
    private static final String EMAIL_VERIFICATION = "email_verification";
    private static final String TICKET = "ticket";

    // 인증을 마친 이메일을 티켓 해시에 묶는다
    @Override
    public void save(String hashedTicket, String email) {
        redisTemplate.opsForValue().set(key(hashedTicket), email, properties.ticketTtl());
    }
    private String key(String hashedTicket) {
        return RedisKey.USER.of(EMAIL_VERIFICATION, TICKET, hashedTicket);
    }
}
