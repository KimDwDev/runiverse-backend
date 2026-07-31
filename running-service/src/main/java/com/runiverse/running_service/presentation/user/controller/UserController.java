package com.runiverse.running_service.presentation.user.controller;

import com.runiverse.running_service.application.user.command.onboard.CompleteOnboardCommand;
import com.runiverse.running_service.application.user.command.onboard.CompleteOnboardResult;
import com.runiverse.running_service.application.user.port.in.CompleteOnboardUsecase;
import com.runiverse.running_service.presentation.user.request.OnboardRequest;
import com.runiverse.running_service.presentation.user.response.OnboardResponse;
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

@RestController
@RequestMapping("users")
@RequiredArgsConstructor
public class UserController {

    private final CompleteOnboardUsecase completeOnboardUsecase;

    @PostMapping("/onboard")
    public ResponseEntity<OnboardResponse> completeOnboard(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OnboardRequest request) {
        UUID userId =  UUID.fromString(jwt.getSubject());
        CompleteOnboardResult result = completeOnboardUsecase.handle(
                new CompleteOnboardCommand(
                        userId,
                        request.nickname(),
                        request.gender(),
                        request.birthday(),
                        request.averagePaceSecondsPerKm(),
                        request.weight(),
                        request.height()
                ));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OnboardResponse(result.userId(), result.nickname()));
    }

}
