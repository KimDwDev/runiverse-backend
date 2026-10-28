package com.runiverse.running_service.application.user.command.stream;

import com.runiverse.running_service.application.user.port.in.OpenUserStreamUsecase;
import com.runiverse.running_service.application.user.port.out.UserStreamConnection;
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

    @Override
    public void handle(OpenUserStreamCommand command) {
        UserId userId = new UserId(command.userId());
        UserStreamConnection connection = command.connection();
        // 앱 재시작처럼 끊긴 줄 모르고 남아 있는 옛 연결이 있다 — 마지막 연결만 남긴다
        userStreamPort.register(userId, connection)
                .ifPresent(UserStreamConnection::closeSuperseded);
        log.info("[회원] 스트림 연결 성공 - userId={}, connectionId={}", command.userId(), connection.id());
    }
}
