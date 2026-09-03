package com.runiverse.running_service.presentation.match.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("running-matches")
public class MatchStreamController {

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) throws IOException {
        // 30초 컨테이너 기본값 대신 명시한다
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitter.complete());
        emitter.onCompletion(() -> log.info("매칭 스트림 종료 — userId={}", jwt.getSubject()));
        // 첫 바이트를 써야 응답 헤더가 나간다 — 없으면 클라는 연결됐는지 알 수 없다
        emitter.send(SseEmitter.event().comment("connected"));
        return emitter;
    }
}
