package com.runiverse.running_service.presentation.user.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignUpRequest(
        @Email
        @NotBlank
        String email,

        @NotBlank
        String password
) {
}