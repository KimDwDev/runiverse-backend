package com.runiverse.running_service.presentation.match.controller;

import com.runiverse.running_service.application.match.command.apply.ApplyMatchCommand;
import com.runiverse.running_service.application.match.command.apply.ApplyMatchResult;
import com.runiverse.running_service.application.match.port.in.ApplyMatchUsecase;
import com.runiverse.running_service.presentation.match.request.ApplyMatchRequest;
import com.runiverse.running_service.presentation.match.response.ApplyMatchResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 스트림(MatchStreamController)과 경로 앞부분을 공유하지만 관심사가 달라 나눈다 —
// 이쪽은 신청·슬롯 조회 같은 REST, 저쪽은 SSE 연결이다
@RestController
@RequestMapping("running-matches")
@RequiredArgsConstructor
public class RunningMatchController {

    private final ApplyMatchUsecase applyMatchUsecase;

    @PostMapping
    public ResponseEntity<ApplyMatchResponse> apply(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ApplyMatchRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ApplyMatchResult result = applyMatchUsecase.handle(new ApplyMatchCommand(
                userId, request.scheduledStartAt(), request.targetDistanceMeters()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApplyMatchResponse.from(result));
    }
}
