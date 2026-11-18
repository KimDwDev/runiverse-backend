package com.runiverse.running_service.unit_test.presentation;

import com.runiverse.running_service.observability.metrics.HttpRequestMetricsFilter;
import com.runiverse.running_service.presentation.common.filter.AdminKeyFilter;
import com.runiverse.running_service.presentation.common.filter.AdminProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 필터에서 끝난 요청은 중앙 예외 핸들러를 거치지 않는다 — 응답 본문·메트릭 원인을 여기서 확인한다
@DisplayName("관리자 키 검사 필터 단위 테스트")
class AdminKeyFilterTest {

    private static final String CONTEXT_PATH = "/api/v1";
    private static final String ADMIN_KEY = "admin-key-for-test";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private AdminKeyFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AdminKeyFilter(new AdminProperties(ADMIN_KEY), JSON_MAPPER);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    @DisplayName("키가 맞으면 그대로 처리한다")
    void passesWithMatchingKey() throws Exception {
        // when
        filter.doFilter(request("/admin/push-campaigns/7/send", ADMIN_KEY), response, chain);

        // then
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "wrong-key", "admin-key-for-tes", "admin-key-for-test ", "ADMIN-KEY-FOR-TEST"})
    @DisplayName("키가 없거나 한 글자라도 다르면 처리하지 않고 401이다")
    void rejectsMissingOrWrongKey(String key) throws Exception {
        // given
        MockHttpServletRequest request = request("/admin/push-campaigns/7/send", key);

        // when
        filter.doFilter(request, response, chain);

        // then
        assertInvalidAdminKey(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/unknown", "/admin"})
    @DisplayName("관리자 경로면 없는 경로도 키부터 본다")
    void checksKeyBeforePath(String path) throws Exception {
        // given -> 키 없이 경로를 더듬어 관리자 API가 무엇이 있는지 알아낼 수 없다. 보안 설정의 /admin/**는 /admin도 연다
        MockHttpServletRequest request = request(path, null);

        // when
        filter.doFilter(request, response, chain);

        // then
        assertInvalidAdminKey(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/%61dmin/push-campaigns/7/send", "/admin;x=1/push-campaigns/7/send"})
    @DisplayName("인코딩하거나 세미콜론을 끼운 관리자 경로도 키를 본다")
    void checksDecodedPath(String path) throws Exception {
        // given -> 컨트롤러는 디코딩한 경로로 찾는다. 원래 주소로 거르면 키 검사만 건너뛴다
        MockHttpServletRequest request = request(path, null);

        // when
        filter.doFilter(request, response, chain);

        // then
        assertInvalidAdminKey(request);
    }

    @Test
    @DisplayName("컨텍스트 경로에 세미콜론을 끼워도 키를 본다")
    void checksPathWithSemicolonInContextPath() throws Exception {
        // given -> 톰캣은 컨텍스트 경로를 원래 주소 그대로 준다. 보안 설정의 /admin/**는 이 요청을 공개로 연다
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api;x=1/v1/admin/push-campaigns/7/send");
        request.setContextPath("/api;x=1/v1");

        // when
        filter.doFilter(request, response, chain);

        // then
        assertInvalidAdminKey(request);
    }

    @Test
    @DisplayName("컨텍스트 경로로 풀 수 없는 주소는 500 대신 키를 본다")
    void checksKeyWhenPathCannotBeParsed() throws Exception {
        // given -> 톰캣은 //api/v1/…도 이 컨텍스트로 넘긴다. 매처가 예외를 던져 그대로 두면 아무나 500을 만든다
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "//api/v1/users/me");
        request.setContextPath(CONTEXT_PATH);

        // when
        filter.doFilter(request, response, chain);

        // then
        assertInvalidAdminKey(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/users/me", "/administrators/1", "/app/version"})
    @DisplayName("관리자 경로가 아니면 키를 보지 않는다")
    void skipsOtherPaths(String path) throws Exception {
        // when -> 앱 요청은 키를 싣지 않는다
        filter.doFilter(request(path, null), response, chain);

        // then
        assertThat(chain.getRequest()).isNotNull();
    }

    // 배포와 같이 컨텍스트 경로 아래로 들어온 요청 — 키는 null이면 싣지 않는다
    private static MockHttpServletRequest request(String path, String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", CONTEXT_PATH + path);
        request.setContextPath(CONTEXT_PATH);
        if (key != null) {
            request.addHeader(AdminKeyFilter.ADMIN_KEY_HEADER, key);
        }
        return request;
    }

    private void assertInvalidAdminKey(MockHttpServletRequest request) throws Exception {
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "code", "INVALID_ADMIN_KEY",
                "message", "관리자 키가 올바르지 않습니다."));
        // 중앙 예외 핸들러를 거치지 않아 원인을 직접 남겨야 UNKNOWN이 되지 않는다
        assertThat(request.getAttribute(HttpRequestMetricsFilter.REASON)).isEqualTo("INVALID_ADMIN_KEY");
    }

    private Map<String, Object> body() throws Exception {
        return JSON_MAPPER.readValue(response.getContentAsString(StandardCharsets.UTF_8), new TypeReference<>() {
        });
    }
}
