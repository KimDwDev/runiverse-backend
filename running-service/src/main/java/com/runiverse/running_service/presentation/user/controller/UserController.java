package com.runiverse.running_service.presentation.user.controller;

import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingCommand;
import com.runiverse.running_service.application.user.command.onboarding.CompleteOnboardingResult;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageCommand;
import com.runiverse.running_service.application.user.command.profileimage.ChangeProfileImageResult;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlCommand;
import com.runiverse.running_service.application.user.command.profileimage.CreateProfileImageUploadUrlResult;
import com.runiverse.running_service.application.user.command.profileimage.DeleteProfileImageCommand;
import com.runiverse.running_service.application.user.port.in.ChangeProfileImageUsecase;
import com.runiverse.running_service.application.user.port.in.CompleteOnboardingUsecase;
import com.runiverse.running_service.application.user.port.in.CreateProfileImageUploadUrlUsecase;
import com.runiverse.running_service.application.user.port.in.DeleteProfileImageUsecase;
import com.runiverse.running_service.application.user.port.in.GetProfileImageUsecase;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlQuery;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlResult;
import com.runiverse.running_service.presentation.common.security.SelfOnly;
import com.runiverse.running_service.presentation.user.request.OnboardingRequest;
import com.runiverse.running_service.presentation.user.request.ProfileImageUploadUrlRequest;
import com.runiverse.running_service.presentation.user.request.ProfileUpdateRequest;
import com.runiverse.running_service.presentation.user.response.OnboardingResponse;
import com.runiverse.running_service.presentation.user.response.ProfileImageUploadUrlResponse;
import com.runiverse.running_service.presentation.user.response.ProfileImageUrlResponse;
import com.runiverse.running_service.presentation.user.response.ProfileUpdateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final ChangeProfileImageUsecase changeProfileImageUsecase;
    private final GetProfileImageUsecase getProfileImageUsecase;
    private final DeleteProfileImageUsecase deleteProfileImageUsecase;

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
                new CreateProfileImageUploadUrlCommand(userId, request.mimeType(), request.fileSizeBytes())
        );
        return ResponseEntity.ok(
                new ProfileImageUploadUrlResponse(result.profileImageKey(), result.uploadUrl()));
    }

    @SelfOnly
    @PatchMapping("/{userId}/profile-image")
    public ResponseEntity<ProfileUpdateResponse> changeProfileImage(
            @PathVariable UUID userId,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        ChangeProfileImageResult result = changeProfileImageUsecase.handle(
                new ChangeProfileImageCommand(userId, request.profileImageKey()));
        return ResponseEntity.ok(new ProfileUpdateResponse(result.profileImageKey()));
    }

    @GetMapping("/{userId}/profile-image")
    public ResponseEntity<ProfileImageUrlResponse> getProfileImageUrl(@PathVariable UUID userId) {
        GetProfileImageUrlResult result = getProfileImageUsecase.handle(
                new GetProfileImageUrlQuery(userId));
        return ResponseEntity.ok(new ProfileImageUrlResponse(result.profileImageUrl()));
    }

    @SelfOnly
    @DeleteMapping("/{userId}/profile-image")
    public ResponseEntity<Void> deleteProfileImage(@PathVariable UUID userId) {
        deleteProfileImageUsecase.handle(new DeleteProfileImageCommand(userId));
        return ResponseEntity.noContent().build();
    }
}
