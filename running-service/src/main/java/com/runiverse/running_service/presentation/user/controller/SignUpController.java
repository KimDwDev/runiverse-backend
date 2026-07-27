package com.runiverse.running_service.presentation.user.controller;

import com.runiverse.running_service.application.user.command.signup.SignUpCommand;
import com.runiverse.running_service.application.user.command.signup.SignUpResult;
import com.runiverse.running_service.application.user.port.in.SignUpUsecase;
import com.runiverse.running_service.presentation.user.request.SignUpRequest;
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
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SignUpController {

    private final SignUpUsecase signUpUsecase;

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
}
