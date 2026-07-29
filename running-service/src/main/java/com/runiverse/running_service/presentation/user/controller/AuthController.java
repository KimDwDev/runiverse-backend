package com.runiverse.running_service.presentation.user.controller;

import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginResult;
import com.runiverse.running_service.application.auth.command.logout.LogoutCommand;
import com.runiverse.running_service.application.auth.command.reissue.ReissueCommand;
import com.runiverse.running_service.application.auth.command.reissue.ReissueResult;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpResult;
import com.runiverse.running_service.application.auth.port.in.LoginUsecase;
import com.runiverse.running_service.application.auth.port.in.LogoutUsecase;
import com.runiverse.running_service.application.auth.port.in.ReissueUsecase;
import com.runiverse.running_service.application.auth.port.in.SignUpUsecase;
import com.runiverse.running_service.presentation.user.request.LoginRequest;
import com.runiverse.running_service.presentation.user.request.ReissueRequest;
import com.runiverse.running_service.presentation.user.request.SignUpRequest;
import com.runiverse.running_service.presentation.user.response.LoginResponse;
import com.runiverse.running_service.presentation.user.response.ReissueResponse;
import com.runiverse.running_service.presentation.user.response.SignUpResponse;
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
    private final LoginUsecase loginUsecase;
    private final LogoutUsecase logoutUsecase;
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
                        result.refreshToken()
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
