package com.runiverse.running_service.presentation.common.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ErrorCode;
import com.runiverse.running_service.presentation.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 유스케이스 예외
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("업무 예외: {} - {}", errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity.status(toStatus(errorCode))
                .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
    }
    // 도메인 검증 예외
    @ExceptionHandler(com.runiverse.running_service.domain.common.exception.BusinessException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            com.runiverse.running_service.domain.common.exception.BusinessException e
    ) {
        log.warn("도메인 예외: {} - {}", e.getErrorCode().getCode(), e.getErrorCode().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }
    // @Valid 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(" "));
        log.warn("요청 검증 실패: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                   CommonErrorCode.INVALID_REQUEST.getCode(),
                   message.isBlank() ? CommonErrorCode.INVALID_REQUEST.getMessage() : message
                ));
    }
    // JSON 문법 오류 등 본문 자체를 읽지 못한 경우
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문 파싱 실패: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        CommonErrorCode.MALFORMED_REQUEST_BODY.getCode(),
                        CommonErrorCode.MALFORMED_REQUEST_BODY.getMessage()
                ));
    }
    // 예상 못한 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("처리하지 못한 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }

    private HttpStatus toStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case EMAIL_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case INVALID_EMAIL_CREDENTIALS,
                 INVALID_PASSWORD_CREDENTIALS,
                 INVALID_CREDENTIALS,
                 INVALID_REFRESH_TOKEN -> HttpStatus.UNAUTHORIZED;
        };
    }
}
