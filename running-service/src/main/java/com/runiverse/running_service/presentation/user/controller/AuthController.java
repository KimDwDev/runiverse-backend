package com.runiverse.running_service.presentation.user.controller;

import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginResult;
import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpResult;
import com.runiverse.running_service.application.auth.port.in.LoginUsecase;
import com.runiverse.running_service.application.auth.port.in.SignUpUsecase;
import com.runiverse.running_service.presentation.user.request.LoginRequest;
import com.runiverse.running_service.presentation.user.request.SignUpRequest;
import com.runiverse.running_service.presentation.user.response.LoginResponse;
import com.runiverse.running_service.presentation.user.response.SignUpResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignUpUsecase signUpUsecase;
    private final LoginUsecase loginUsecase;

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

}
