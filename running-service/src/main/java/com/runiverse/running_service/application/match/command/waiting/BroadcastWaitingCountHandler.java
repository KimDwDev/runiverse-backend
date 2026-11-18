package com.runiverse.running_service.application.match.command.waiting;

import com.runiverse.running_service.application.app.port.out.AppStreamConnection;
import com.runiverse.running_service.application.app.port.out.AppStreamEvent;
import com.runiverse.running_service.application.app.port.out.AppStreamPort;
import com.runiverse.running_service.application.match.port.in.BroadcastWaitingCountUsecase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;

// 모든 인스턴스가 주기마다 부른다 — 이 인스턴스에 붙은 연결에만 보낸다. 다른 서버는 자기 연결에 같은 값을 보낸다.
// 트랜잭션을 걸지 않는다: 연결이 없는 인스턴스가 DB 커넥션을 잡지 않게 읽기는 리더가 연다
@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastWaitingCountHandler implements BroadcastWaitingCountUsecase {

    private final AppStreamPort appStreamPort;
    private final WaitingCountReader waitingCountReader;

    @Override
    public void broadcast() {
        // 1. 붙은 연결이 없으면 계산도 하지 않는다
        Collection<AppStreamConnection> connections = appStreamPort.findAll();
        if (connections.isEmpty()) {
            return;
        }
        // 2. 누가 보든 같은 값이라 한 번 계산해 전부에 보낸다.
        //    끊긴 단말이면 연결이 스스로 닫고 레지스트리에서 빠진다
        AppStreamEvent event = AppStreamEvent.homeWaiting(waitingCountReader.read());
        connections.forEach(connection -> send(connection, event));
    }

    private void send(AppStreamConnection connection, AppStreamEvent event) {
        try {
            connection.send(event);
        } catch (RuntimeException e) {
            // 한 연결의 실패가 나머지 수신자를 막지 않는다 — 끊긴 단말은 스스로 정리된다
            log.error("[매칭] 대기 인원 전송 실패: 처리하지 못한 예외 - connectionId={}", connection.id(), e);
        }
    }
}
