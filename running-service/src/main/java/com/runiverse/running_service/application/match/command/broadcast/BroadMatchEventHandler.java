package com.runiverse.running_service.application.match.command.broadcast;

import com.runiverse.running_service.application.match.port.in.BroadcastMatchEventUsecase;
import com.runiverse.running_service.application.match.port.out.MatchStreamConnection;
import com.runiverse.running_service.application.match.port.out.MatchStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BroadMatchEventHandler implements BroadcastMatchEventUsecase {

    private final MatchStreamPort matchStreamPort;

    @Override
    public void handle(BroadcastMatchEventCommand command) {
        command.userIds().forEach(userId -> matchStreamPort.find(new UserId(userId))
                .ifPresent(connection -> send(connection, command)));
    }

    private void send(MatchStreamConnection connection, BroadcastMatchEventCommand command) {
        try {
            connection.send(command.event());
        } catch (RuntimeException e) {
            // 한 연결의 실패가 나머지 수신자를 막지 않는다 — 끊긴 단말은 스스로 정리된다
            log.warn("매칭 이벤트 전송 실패 — connectionId={}", connection.id(), e);
        }
    }
}
