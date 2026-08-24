package com.runiverse.running_service.presentation.running.controller;

import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomCommand;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomResult;
import com.runiverse.running_service.application.running.port.in.OpenSoloRoomUsecase;
import com.runiverse.running_service.presentation.running.response.SoloRunningStartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("running-rooms")
@RequiredArgsConstructor
public class RunningRoomController {

    private final OpenSoloRoomUsecase startSoloRunningUsecase;

    @PostMapping("/solo")
    public ResponseEntity<SoloRunningStartResponse> startSolo(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        OpenSoloRoomResult result = startSoloRunningUsecase.handle(
                new OpenSoloRoomCommand(userId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SoloRunningStartResponse(result.runningRoomId()));
    }
}
