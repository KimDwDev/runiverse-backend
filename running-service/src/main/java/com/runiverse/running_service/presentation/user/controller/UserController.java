package com.runiverse.running_service.presentation.user.controller;

import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingResult;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlCommand;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlResult;
import com.runiverse.running_service.application.user.port.in.CompleteOnboardingUsecase;
import com.runiverse.running_service.application.user.port.in.CreateProfileImageUploadUrlUsecase;
import com.runiverse.running_service.presentation.common.security.SelfOnly;
import com.runiverse.running_service.presentation.user.request.OnboardingRequest;
import com.runiverse.running_service.presentation.user.request.ProfileImageUploadUrlRequest;
import com.runiverse.running_service.presentation.user.response.OnboardingResponse;
import com.runiverse.running_service.presentation.user.response.ProfileImageUploadUrlResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("users")
@RequiredArgsConstructor
public class UserController {

    private final CompleteOnboardingUsecase completeOnboardingUsecase;
    private final CreateProfileImageUploadUrlUsecase createProfileImageUploadUrlUsecase;

    @PostMapping("/onboarding")
    public ResponseEntity<OnboardingResponse> completeOnboarding(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OnboardingRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        CompleteOnboardingResult result = completeOnboardingUsecase.handle(
                new CompleteOnboardingCommand(
                        userId,
                        request.nickname(),
                        request.gender(),
                        request.birthday(),
                        request.averagePaceSecondsPerKm(),
                        request.weightKg(),
                        request.heightCm()
                ));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OnboardingResponse(result.userId(), result.nickname()));
    }

    @SelfOnly
    @PostMapping("/{userId}/profile-image/presigned-url")
    public ResponseEntity<ProfileImageUploadUrlResponse> createProfileImageUploadUrl(
            @PathVariable UUID userId,
            @Valid @RequestBody ProfileImageUploadUrlRequest request
    ) {
        CreateProfileImageUploadUrlResult result = createProfileImageUploadUrlUsecase.handle(
                new CreateProfileImageUploadUrlCommand(userId, request.mimeType())
        );
        return ResponseEntity.ok(
                new ProfileImageUploadUrlResponse(result.profileImageKey(), result.uploadUrl()));
    }
}
