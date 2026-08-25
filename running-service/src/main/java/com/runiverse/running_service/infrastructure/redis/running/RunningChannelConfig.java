package com.runiverse.running_service.infrastructure.redis.running;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class RunningChannelConfig {

    private final SupersedeListener supersedeListener;

    @Bean
    public RedisMessageListenerContainer runningChannelContainer(RedisConnectionFactory factory) {
        // factory는 redis를 설치하면 하나의 빈으로 사용이 가능하다
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        // redis 주소 비번을 이 factory에서 받아서 쓰라는 의미이다.
        container.setConnectionFactory(factory);
        // 이코드는 들리게 되었을때 해당 채널로 리스너라는 함수가 처리하겠다는 것
        container.addMessageListener(supersedeListener, new ChannelTopic(RunningChannel.SUPERSEDE));
        // 스프링이 빈으로 등록하고 앱이 뜰 때 알아서 start 진행
        // 객체 유지, 다른 클래스 주입 후 사용
        return container;
    }
}
