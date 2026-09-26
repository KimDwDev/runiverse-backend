package com.runiverse.running_service.observability.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

// 로그 앞머리의 [도메인] 태그를 요청을 처리한 컨트롤러의 패키지로 정한다
// 레이어를 import하지 않도록 패키지 이름 문자열로만 판단한다
public final class LogTag {

    private static final String PRESENTATION_PACKAGE = "com.runiverse.running_service.presentation.";
    private static final String COMMON = "[공통]";
    private static final Map<String, String> TAGS = Map.of(
            "auth", "[인증]",
            "user", "[회원]",
            "match", "[매칭]",
            "running", "[러닝]"
    );

    private LogTag() {
    }

    // 컨트롤러에 닿기 전에 난 예외(없는 경로 등)는 처리한 컨트롤러가 없어 [공통]이다
    public static String of(HttpServletRequest request) {
        if (!(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
                instanceof HandlerMethod handlerMethod)) {
            return COMMON;
        }
        String packageName = handlerMethod.getBeanType().getPackageName();
        if (!packageName.startsWith(PRESENTATION_PACKAGE)) {
            return COMMON;
        }
        String domain = packageName.substring(PRESENTATION_PACKAGE.length()).split("\\.")[0];
        return TAGS.getOrDefault(domain, COMMON);
    }
}
