package com.runiverse.running_service.infrastructure.oauth.google;

import com.runiverse.running_service.application.auth.exception.OauthCodeExchangeFailedException;
import com.runiverse.running_service.application.auth.exception.OauthEmailNotProvidedException;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.vo.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

public class GoogleOauthClientTest {

    private static final String CLIENT_ID = "google-client-id";
    private static final String CLIENT_SECRET = "google-client-secret";
    private static final String REDIRECT_URI = "http://localhost:5173";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    private static final String AUTHORIZATION_CODE = "google-authorization-code";
    private static final String CODE_VERIFIER = "pkce-code-verifier";
    private static final String GOOGLE_ACCESS_TOKEN = "google-access-token";

    // 구글 sub는 Long 범위를 넘길 수 있는 21자리라 문자열 그대로 다룬다
    private static final String PROVIDER_ID = "107812345678901234567";
    private static final String EMAIL = "google@example.com";

    private static final String TOKEN_RESPONSE =
            """
                    {
                      "access_token": "google-access-token",
                      "expires_in": 3599,
                      "scope": "openid email profile",
                      "token_type": "Bearer",
                      "id_token": "ey..."
                    }
                    """;

    private static final String USER_RESPONSE =
            """
                    {
                      "sub": "107812345678901234567",
                      "email": "google@example.com",
                      "email_verified": true,
                      "name": "러니버스",
                      "picture": "https://lh3.googleusercontent.com/a/example"
                    }
                    """;

    // email 스코프에 동의하지 않으면 email 필드 자체가 응답에서 빠진다
    private static final String USER_RESPONSE_WITHOUT_EMAIL =
            """
                    {
                      "sub": "107812345678901234567",
                      "name": "러니버스"
                    }
                    """;

    private MockRestServiceServer mockServer;

    // 실제 네트워크 없이 구글 응답을 흉내내기 위해 빌더에 MockRestServiceServer를 바인딩한다
    private GoogleOauthClient createClient(String clientSecret) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        GoogleOauthProperties properties = new GoogleOauthProperties(
                CLIENT_ID,
                clientSecret,
                REDIRECT_URI,
                TOKEN_URI,
                USER_INFO_URI
        );

        return new GoogleOauthClient(builder.build(), properties);
    }

    @Test
    @DisplayName("provider는 GOOGLE을 반환한다")
    void providerReturnsGoogle() {
        assertThat(createClient(CLIENT_SECRET).provider()).isEqualTo(Provider.GOOGLE);
    }

    @Test
    @DisplayName("인가 코드를 교환해 구글 프로필을 반환한다")
    void exchangeReturnsProfile() {
        // given
        GoogleOauthClient client = createClient(CLIENT_SECRET);

        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("client_id", CLIENT_ID);
        expectedForm.add("redirect_uri", REDIRECT_URI);
        expectedForm.add("code", AUTHORIZATION_CODE);
        expectedForm.add("client_secret", CLIENT_SECRET);
        expectedForm.add("code_verifier", CODE_VERIFIER);

        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + GOOGLE_ACCESS_TOKEN))
                .andRespond(withSuccess(USER_RESPONSE, MediaType.APPLICATION_JSON));

        // when
        OauthProfile profile = client.exchange(AUTHORIZATION_CODE, CODE_VERIFIER);

        // then
        assertThat(profile.provider()).isEqualTo(Provider.GOOGLE);
        assertThat(profile.providerId()).isEqualTo(PROVIDER_ID);
        assertThat(profile.email()).isEqualTo(EMAIL);

        mockServer.verify();
    }

    @Test
    @DisplayName("client_secret이 없으면 폼에 담지 않는다")
    void exchangeOmitsBlankClientSecret() {
        // given -> secret이 발급되지 않는 모바일 클라이언트 타입
        GoogleOauthClient client = createClient("");

        // formData는 완전 일치를 검사하므로 이 5개만 있어야 통과한다
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("client_id", CLIENT_ID);
        expectedForm.add("redirect_uri", REDIRECT_URI);
        expectedForm.add("code", AUTHORIZATION_CODE);
        expectedForm.add("code_verifier", CODE_VERIFIER);

        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(USER_RESPONSE, MediaType.APPLICATION_JSON));

        // when
        client.exchange(AUTHORIZATION_CODE, CODE_VERIFIER);

        // then
        mockServer.verify();
    }

    @Test
    @DisplayName("토큰 요청이 실패하면 OauthCodeExchangeFailedException을 던진다")
    void exchangeFailsWhenTokenRequestRejected() {
        // given -> 인가 코드 재사용 시 구글이 invalid_grant로 거부한다
        GoogleOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest()
                        .body(
                                """
                                        {"error":"invalid_grant","error_description":"Bad Request"}
                                        """)
                        .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.exchange(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthCodeExchangeFailedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("토큰 응답에 access_token이 없으면 OauthCodeExchangeFailedException을 던진다")
    void exchangeFailsWhenAccessTokenMissing() {
        // given -> 200이지만 본문이 비어 있는 경우를 방어한다
        GoogleOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.exchange(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthCodeExchangeFailedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("사용자 정보 조회가 실패하면 OauthCodeExchangeFailedException을 던진다")
    void exchangeFailsWhenUserRequestRejected() {
        // given
        GoogleOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withUnauthorizedRequest());

        // when & then
        assertThatThrownBy(() -> client.exchange(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthCodeExchangeFailedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("사용자 응답에 sub가 없으면 OauthCodeExchangeFailedException을 던진다")
    void exchangeFailsWhenSubMissing() {
        // given -> 200이지만 식별자가 빠진 경우를 방어한다
        GoogleOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.exchange(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthCodeExchangeFailedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("이메일 동의를 받지 못하면 OauthEmailNotProvidedException을 던진다")
    void exchangeFailsWhenEmailNotAgreed() {
        // given
        GoogleOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(USER_RESPONSE_WITHOUT_EMAIL, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.exchange(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthEmailNotProvidedException.class);

        mockServer.verify();
    }

}
