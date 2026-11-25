package com.runiverse.running_service.infrastructure.redis.running;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@RequiredArgsConstructor
public class RunningChannelConfig {

    // 매칭 · 알림 · 예약 채널 — 메시지가 적다. 러닝 방 메시지가 몰려도 밀리지 않게 러닝 방과 컨테이너를 나눈다(F7).
    // 같은 타입 빈이 둘이라 이름을 몰라도 이쪽으로 가게 한다
    @Bean
    @Primary
    public RedisMessageListenerContainer runningChannelContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        // redis 주소 비번을 이 factory에서 받아서 쓰라는 의미이다.
        container.setConnectionFactory(factory);
        return container;
    }

    // 러닝 방 채널(running:room:*) 전용 — 구독은 방 단위로 런타임에 붙는다(RunningRoomSubscriber).
    // 실행기를 지정하지 않으면 메시지마다 새 스레드를 만들어(SimpleAsyncTaskExecutor) 몰릴 때 스레드가 상한 없이 는다
    @Bean
    public RedisMessageListenerContainer runningRoomChannelContainer(
            RedisConnectionFactory factory,
            @Qualifier("runningRoomListenerExecutor") Executor runningRoomListenerExecutor) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.setTaskExecutor(runningRoomListenerExecutor);
        return container;
    }

    // 이름으로만 쓰이게 둔다 — 실행기를 모두 주입받는 설정이 이 풀에 매이지 않게(AsyncConfig와 같다)
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskExecutor runningRoomListenerExecutor(RunningRoomListenerProperties properties,
                                                             MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.threads());
        executor.setMaxPoolSize(properties.threads());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("running-room-");
        executor.setRejectedExecutionHandler(
                new RunningRoomMessageDropHandler(properties.queueCapacity(), meterRegistry));
        return executor;
    }
}
