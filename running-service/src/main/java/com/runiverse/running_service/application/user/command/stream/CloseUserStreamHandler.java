package com.runiverse.running_service.application.user.command.stream;

import com.runiverse.running_service.application.user.port.in.CloseUserStreamUsecase;
import com.runiverse.running_service.application.user.port.out.UserStreamPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloseUserStreamHandler implements CloseUserStreamUsecase {

    private final UserStreamPort userStreamPort;

    @Override
    public void handle(CloseUserStreamCommand command) {
        UserId userId = new UserId(command.userId());
        // 새 연결이 이미 자리를 가져갔으면 지우지 않는다 — 그때는 false다
        boolean removed = userStreamPort.remove(userId, command.connection());
        log.info("[회원] 스트림 종료 성공 - userId={}, connectionId={}, removed={}",
                command.userId(), command.connection().id(), removed);
    }
}
