package com.runiverse.running_service.application.match.common;

import com.runiverse.running_service.application.device.command.push.PushRequestedEvent;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

// 매칭 확정 푸시 — 시작하면 의미가 없어 알림함에 쌓지 않고 시작 시각에 만료된다.
// 커밋 뒤에 보내므로 방을 바꾼 트랜잭션 안에서 부른다
@Component
@RequiredArgsConstructor
public class MatchPushRequester {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ApplicationEventPublisher eventPublisher;

    // 방에 남은 사람 전원이 받는다. 인원은 받는 사람 본인을 포함한다
    public void matchConfirmed(RunningRoom room) {
        List<UserId> recipients = room.getSessions().stream()
                .filter(RoomSession::isConnected)
                .map(RoomSession::getUserId)
                .toList();
        String startTime = room.getStartAt().format(TIME_FORMAT);
        Map<String, String> data = Map.of("runningRoomId", String.valueOf(roomIdOf(room)));
        PushMessage message = recipients.size() > 1
                ? new PushMessage(PushType.MATCH_CONFIRMED, "매칭이 완료됐어요",
                        "%s 러닝에 %d명이 모였어요".formatted(startTime, recipients.size()), data, room.getStartAt())
                : new PushMessage(PushType.MATCH_CONFIRMED, "혼자 달리게 됐어요",
                        "%s에 혼자 달려요. 지금 나가도 다시 신청할 수 있어요".formatted(startTime), data, room.getStartAt());
        recipients.forEach(userId -> eventPublisher.publishEvent(new PushRequestedEvent(userId.value(), message)));
    }

    private static Long roomIdOf(RunningRoom room) {
        return room.getRunningRoomId()
                .orElseThrow(() -> new IllegalStateException("저장되지 않은 방은 알릴 수 없다"))
                .value();
    }
}
