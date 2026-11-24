package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.running.FinishLeaseProperties;
import com.runiverse.running_service.infrastructure.redis.running.FinishLeaseRedisAdapter;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("끝내기 임대 Redis 어댑터 단위 테스트")
public class FinishLeaseRedisAdapterTest {

    private static final long ROOM_ID = 125L;
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final String KEY = "running:finish-lease:" + ROOM_ID + ":" + USER_ID;
    private static final Duration TTL = Duration.ofSeconds(30);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private FinishLeaseRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FinishLeaseRedisAdapter(redisTemplate, new FinishLeaseProperties(TTL));
    }

    @Test
    @DisplayName("비어 있으면 TTL을 걸어 잡고 토큰을 돌려준다")
    void acquiresWhenFree() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(true);

        // when
        Optional<String> token = adapter.tryAcquire(ROOM_ID, new UserId(USER_ID));

        // then - 저장한 값이 돌려준 토큰이어야 해제 때 자기 것인지 가릴 수 있다
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(eq(KEY), stored.capture(), eq(TTL));
        assertThat(token).contains(stored.getValue());
    }

    @Test
    @DisplayName("남이 쥐고 있으면 기다리지 않고 empty를 돌려준다")
    void returnsEmptyWhenHeld() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(false);

        // when & then
        assertThat(adapter.tryAcquire(ROOM_ID, new UserId(USER_ID))).isEmpty();
    }

    @Test
    @DisplayName("Redis가 null로 답하면 잡지 못한 것으로 본다")
    void treatsNullAsNotAcquired() {
        // given - 파이프라인·트랜잭션 안에서는 null이 온다 — 잡았다고 보면 봉인이 겹친다
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(null);

        // when & then
        assertThat(adapter.tryAcquire(ROOM_ID, new UserId(USER_ID))).isEmpty();
    }

    @Test
    @DisplayName("요청마다 다른 토큰을 쓴다")
    void issuesDistinctTokens() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(true);

        // when
        String first = adapter.tryAcquire(ROOM_ID, new UserId(USER_ID)).orElseThrow();
        String second = adapter.tryAcquire(ROOM_ID, new UserId(USER_ID)).orElseThrow();

        // then - 같으면 TTL을 넘긴 앞 요청이 뒤 요청의 임대를 지운다
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("해제는 키와 자기 토큰을 스크립트 하나로 보낸다")
    @SuppressWarnings("unchecked")
    void releasesWithOwnToken() {
        // when
        adapter.release(ROOM_ID, new UserId(USER_ID), "token-1");

        // then
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)), eq("token-1"));
    }

    @Test
    @DisplayName("해제 스크립트는 값이 토큰과 같을 때만 지운다")
    void releaseScriptChecksToken() throws IOException {
        // 스크립트는 Redis 없이 돌릴 수 없어 내용으로 계약을 고정한다 — 확인 없이 DEL하면 남의 임대를 지운다
        String script = new ClassPathResource("scripts/release_finish_lease.lua")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(script)
                .contains("redis.call('GET', KEYS[1]) == ARGV[1]")
                .contains("redis.call('DEL', KEYS[1])");
    }
}
