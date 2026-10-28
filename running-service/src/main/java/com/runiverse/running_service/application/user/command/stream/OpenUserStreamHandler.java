package com.runiverse.running_service.application.user.command.stream;

import com.runiverse.running_service.application.running.port.in.GetUserStatusUsecase;
import com.runiverse.running_service.application.running.query.status.GetUserStatusQuery;
import com.runiverse.running_service.application.running.query.status.GetUserStatusResult;
import com.runiverse.running_service.application.user.port.in.OpenUserStreamUsecase;
import com.runiverse.running_service.application.user.port.out.UserStreamConnection;
import com.runiverse.running_service.application.user.port.out.UserStreamEvent;
import com.runiverse.running_service.application.user.port.out.UserStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenUserStreamHandler implements OpenUserStreamUsecase {

    private final UserStreamPort userStreamPort;
    private final GetUserStatusUsecase getUserStatusUsecase;

    @Override
    public void handle(OpenUserStreamCommand command) {
        UserId userId = new UserId(command.userId());
        UserStreamConnection connection = command.connection();
        // 앱 재시작처럼 끊긴 줄 모르고 남아 있는 옛 연결이 있다 — 마지막 연결만 남긴다
        userStreamPort.register(userId, connection)
                .ifPresent(UserStreamConnection::closeSuperseded);
        // 등록을 먼저 한다 — 스냅샷을 계산하는 사이에 생긴 이벤트도 이 연결로 받게 한다.
        // 상태 판정은 2-2와 같은 유스케이스를 쓴다 — 두 입구가 다른 답을 낼 수 없다
        GetUserStatusResult status = getUserStatusUsecase.handle(new GetUserStatusQuery(command.userId()));
        // 재연결이 곧 스냅샷 재수신이다 — 놓친 이벤트를 되짚을 필요가 없다
        connection.send(UserStreamEvent.status(status));
        log.info("[회원] 스트림 연결 성공 - userId={}, connectionId={}, status={}",
                command.userId(), connection.id(), status.status());
    }
}
