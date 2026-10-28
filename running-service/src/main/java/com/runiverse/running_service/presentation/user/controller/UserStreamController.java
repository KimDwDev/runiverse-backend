package com.runiverse.running_service.presentation.user.controller;

import com.runiverse.running_service.application.user.command.stream.CloseUserStreamCommand;
import com.runiverse.running_service.application.user.command.stream.OpenUserStreamCommand;
import com.runiverse.running_service.application.user.port.in.CloseUserStreamUsecase;
import com.runiverse.running_service.application.user.port.in.OpenUserStreamUsecase;
import com.runiverse.running_service.application.user.port.out.UserStreamConnection;
import com.runiverse.running_service.presentation.user.sse.SseUserStreamConnection;
import com.runiverse.running_service.presentation.user.sse.UserStreamProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
// 앱이 켜져 있는 동안 내내 여는 개인 스트림 — 연결 직후 상태 스냅샷을 받는다
@RequestMapping("users")
@RequiredArgsConstructor
public class UserStreamController {

    private final OpenUserStreamUsecase openUserStreamUsecase;
    private final CloseUserStreamUsecase closeUserStreamUsecase;
    private final UserStreamProperties userStreamProperties;

    @GetMapping(path = "me/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        // 30초 컨테이너 기본값 대신 명시한다
        SseEmitter emitter = new SseEmitter(userStreamProperties.timeout().toMillis());
        UserStreamConnection connection =
                new SseUserStreamConnection(UUID.randomUUID().toString(), emitter);
        // 이게 없으면 타임아웃이 예외로 터져 500이 된다. 스스로 complete하지 않는다
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitter.complete());
        // 타임아웃·에러·클라 종료가 전부 여기로 모인다 — 정리는 한 곳에서만 한다
        emitter.onCompletion(() -> closeUserStreamUsecase.handle(
                new CloseUserStreamCommand(userId, connection)));
        // 첫 이벤트(USER_STATUS)를 여기서 바로 보낸다 — 응답 헤더도 이때 나간다
        openUserStreamUsecase.handle(new OpenUserStreamCommand(userId, connection));
        return emitter;
    }
}
