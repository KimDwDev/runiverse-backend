package com.runiverse.running_service.presentation.common.security;

import com.runiverse.running_service.infrastructure.security.jwt.validator.ExpiredTokenValidator;
import com.runiverse.running_service.presentation.common.exception.AuthErrorCode;
import com.runiverse.running_service.presentation.common.exception.ErrorExposurePolicy;
import com.runiverse.running_service.presentation.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        AuthErrorCode errorCode = resolve(authException);
        log.warn("인증 실패: {} - {}", errorCode.getCode(), errorCode.getMessage());
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        ErrorResponse body = new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
        if (!ErrorExposurePolicy.isExposed(status, errorCode.getCode())) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            body = ErrorExposurePolicy.masked();
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
    private AuthErrorCode resolve(AuthenticationException authException) {
        // 만료는 ExpiredTokenValidator가 심어둔 코드로 판별한다
        if (authException.getCause() instanceof JwtValidationException validationException) {
            boolean expired = validationException.getErrors().stream()
                    .anyMatch(error -> ExpiredTokenValidator.ERROR_CODE.equals(error.getErrorCode()));
            if (expired) return AuthErrorCode.TOKEN_EXPIRED;
        }
        // 토큰을 아예 보내지 않은 경우
        if (authException instanceof InsufficientAuthenticationException) {
            return AuthErrorCode.AUTHENTICATION_REQUIRED;
        }
        return AuthErrorCode.INVALID_TOKEN;
    }
}
