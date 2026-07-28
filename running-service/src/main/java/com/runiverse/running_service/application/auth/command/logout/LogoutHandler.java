package com.runiverse.running_service.application.auth.command.logout;

import com.runiverse.running_service.application.auth.port.in.LogoutUsecase;
import org.springframework.stereotype.Service;

@Service
public class LogoutHandler implements LogoutUsecase {

    @Override
    public LogoutResult handle(LogoutCommand command) {

        // 1. cache에서 데이터를 refresh token을 찾고 삭제

        return null;
    }
}
