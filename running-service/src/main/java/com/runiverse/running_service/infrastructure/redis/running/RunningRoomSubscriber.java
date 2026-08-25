package com.runiverse.running_service.infrastructure.redis.running;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

// 이 인스턴스가 참가자를 들고 있는 방만 구독한다
@Slf4j
@Component
@RequiredArgsConstructor
public class RunningRoomSubscriber {

    private final RedisMessageListenerContainer runningChannelContainer;
    private final RunningRoomListener runningRoomListener;

    public void subscribe(Long runningRoomId) {
        runningChannelContainer.addMessageListener(
                runningRoomListener, new ChannelTopic(RunningChannel.room(runningRoomId)));
        log.debug("러닝 방 채널 구독 — roomId={}", runningRoomId);
    }

    public void unsubscribe(Long runningRoomId) {
        runningChannelContainer.removeMessageListener(
                runningRoomListener, new ChannelTopic(RunningChannel.room(runningRoomId)));
        log.debug("러닝 방 채널 구독 해제 — roomId={}", runningRoomId);
    }
}
