package com.runiverse.running_service.presentation.common.filter;

import com.runiverse.running_service.observability.metrics.HttpRequestMetricsFilter;
import com.runiverse.running_service.presentation.common.exception.CommonErrorCode;
import com.runiverse.running_service.presentation.common.exception.ErrorExposurePolicy;
import com.runiverse.running_service.presentation.common.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// 관리자 API는 토큰 대신 키로 연다 — 키가 없거나 틀리면 경로를 찾기 전에 거절한다.
// 보안 필터는 이 경로를 공개로 두므로 여기서 막지 않으면 누구나 부를 수 있다
@Component
// 요청 ID·메트릭 필터 다음이라 거절도 추적·집계되고, 보안 필터(-100) 앞이라 토큰 검사보다 먼저 거른다
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
@RequiredArgsConstructor
public class AdminKeyFilter extends OncePerRequestFilter {

    public static final String ADMIN_KEY_HEADER = "X-Admin-Key";
    // 보안 설정이 공개로 여는 매처와 같게 만든다 — 판정이 어긋나면 인코딩·세미콜론을 끼운 주소가
    // 보안 필터에서는 공개로 열리고 여기서는 관리자 경로가 아니라고 빠져 키 없이 컨트롤러에 닿는다
    private static final RequestMatcher ADMIN_PATHS = PathPatternRequestMatcher.withDefaults().matcher("/admin/**");

    private final AdminProperties adminProperties;
    private final ObjectMapper objectMapper;

    // 앱 버전 필터도 이것으로 관리자 경로를 뺀다.
    // //api/v1/…처럼 컨텍스트 경로로 풀 수 없는 주소는 매처가 예외를 던진다 — 500 대신 관리자 경로로 보고 키를 검사한다
    static boolean isAdminPath(HttpServletRequest request) {
        try {
            return ADMIN_PATHS.matches(request);
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isAdminPath(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!matches(request.getHeader(ADMIN_KEY_HEADER))) {
            reject(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    // 걸린 시간으로 키를 한 글자씩 맞혀 보지 못하게 끝까지 비교한다
    private boolean matches(String key) {
        if (key == null) {
            return false;
        }
        return MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), adminProperties.key().getBytes(StandardCharsets.UTF_8));
    }

    // 중앙 예외 핸들러를 거치지 않으므로 메트릭 원인·노출 정책을 여기서 맞춘다
    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CommonErrorCode errorCode = CommonErrorCode.INVALID_ADMIN_KEY;
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        request.setAttribute(HttpRequestMetricsFilter.REASON, errorCode.getCode());
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
}
