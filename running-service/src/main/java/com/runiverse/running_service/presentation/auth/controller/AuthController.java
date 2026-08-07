package com.runiverse.running_service.presentation.auth.controller;

import com.runiverse.running_service.application.auth.command.emailverification.SendEmailVerificationCommand;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeCommand;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeResult;
import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginResult;
import com.runiverse.running_service.application.auth.command.logout.LogoutCommand;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginCommand;
import com.runiverse.running_service.application.auth.command.oauthlogin.OauthLoginResult;
import com.runiverse.running_service.application.auth.command.reissue.ReissueCommand;
import com.runiverse.running_service.application.auth.command.reissue.ReissueResult;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpResult;
import com.runiverse.running_service.application.auth.port.in.*;
import com.runiverse.running_service.presentation.auth.request.*;
import com.runiverse.running_service.presentation.auth.response.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignUpUsecase signUpUsecase;
    private final SendEmailVerificationUsecase sendEmailVerificationUsecase;
    private final VerifyEmailCodeUsecase verifyEmailCodeUsecase;
    private final LoginUsecase loginUsecase;
    private final LogoutUsecase logoutUsecase;
    private final OauthLoginUsecase oauthLoginUsecase;
    private final ReissueUsecase reissueUsecase;

    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signUp(
            @Valid @RequestBody SignUpRequest request
    ) {

        SignUpCommand command = new SignUpCommand(
                        request.email(),
                        request.password()
                );

        SignUpResult result = signUpUsecase.handle(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SignUpResponse(result.userId()));
    }

    @PostMapping("/email/verifications")
    public ResponseEntity<Void> sendEmailVerification(
            @Valid @RequestBody EmailVerificationRequest request
    ) {
        SendEmailVerificationCommand command = new SendEmailVerificationCommand(request.email());
        sendEmailVerificationUsecase.handle(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/verifications/confirm")
    public ResponseEntity<VerifyEmailCodeResponse> verifyEmailCode(
            @Valid @RequestBody VerifyEmailCodeRequest request
    ) {
        VerifyEmailCodeCommand command = new VerifyEmailCodeCommand(
                request.email(),
                request.code()
        );
        VerifyEmailCodeResult result = verifyEmailCodeUsecase.handle(command);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new VerifyEmailCodeResponse(result.verificationTicket()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
            ) {

        LoginCommand command = new LoginCommand(
                request.email(),
                request.password()
        );

        LoginResult result = loginUsecase.handle(command);

        return ResponseEntity.status(HttpStatus.OK)
                .body(new LoginResponse(
                        result.userId(),
                        result.accessToken(),
                        result.refreshToken(),
                        result.isOnboarded()
                ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt
            ) {
        LogoutCommand command = new LogoutCommand(
                UUID.fromString(jwt.getSubject()),
                jwt.getId()
        );
        logoutUsecase.handle(command);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/oauth/{provider}")
    public ResponseEntity<OauthLoginResponse> oauthLogin(
            @PathVariable String provider,
            @Valid @RequestBody OauthLoginRequest request
            ) {
        OauthLoginCommand command = new OauthLoginCommand(
                provider,
                request.authorizationCode(),
                request.codeVerifier()
        );
        OauthLoginResult result = oauthLoginUsecase.handle(command);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new OauthLoginResponse(
                        result.userId(),
                        result.accessToken(),
                        result.refreshToken(),
                        result.isOnboarded()
                ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ReissueResponse> reissue(
            @Valid @RequestBody ReissueRequest request
            ) {
        ReissueCommand command = new ReissueCommand(
                request.refreshToken()
        );
        ReissueResult result = reissueUsecase.handle(command);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ReissueResponse(
                        result.accessToken(),
                        result.refreshToken()
                ));
    }
}
