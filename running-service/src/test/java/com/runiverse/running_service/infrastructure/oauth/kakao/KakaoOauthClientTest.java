package com.runiverse.running_service.infrastructure.oauth.kakao;

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

public class KakaoOauthClientTest {

    private static final String CLIENT_ID = "kakao-rest-api-key";
    private static final String CLIENT_SECRET = "kakao-client-secret";
    private static final String REDIRECT_URI = "http://localhost:5173";
    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private static final String AUTHORIZATION_CODE = "kakao-authorization-code";
    private static final String CODE_VERIFIER = "pkce-code-verifier";
    private static final String KAKAO_ACCESS_TOKEN = "kakao-access-token";

    private static final String PROVIDER_ID = "1234567890";
    private static final String EMAIL = "kakao@example.com";

    private static final String TOKEN_RESPONSE = """
            {
              "token_type": "bearer",
              "access_token": "kakao-access-token",
              "expires_in": 21599,
              "refresh_token": "kakao-refresh-token"
            }
            """;

    private static final String USER_RESPONSE = """
            {
              "id": 1234567890,
              "connected_at": "2026-07-30T00:00:00Z",
              "kakao_account": {
                "has_email": true,
                "email_needs_agreement": false,
                "is_email_valid": true,
                "is_email_verified": true,
                "email": "kakao@example.com"
              }
            }
            """;

    // 이메일 동의를 받지 못하면 email 필드 자체가 응답에서 빠진다
    private static final String USER_RESPONSE_WITHOUT_EMAIL = """
            {
              "id": 1234567890,
              "kakao_account": {
                "has_email": true,
                "email_needs_agreement": true
              }
            }
            """;

    // 동의 항목이 하나도 없으면 kakao_account 자체가 오지 않는다
    private static final String USER_RESPONSE_WITHOUT_ACCOUNT = """
            {
              "id": 1234567890
            }
            """;

    private MockRestServiceServer mockServer;

    // 실제 네트워크 없이 카카오 응답을 흉내내기 위해 빌더에 MockRestServiceServer를 바인딩한다
    private KakaoOauthClient createClient(String clientSecret) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        KakaoOauthProperties properties = new KakaoOauthProperties(
                CLIENT_ID,
                clientSecret,
                REDIRECT_URI,
                TOKEN_URI,
                USER_INFO_URI
        );

        return new KakaoOauthClient(builder.build(), properties);
    }

    @Test
    @DisplayName("provider는 KAKAO를 반환한다")
    void providerReturnsKakao() {
        assertThat(createClient(CLIENT_SECRET).provider()).isEqualTo(Provider.KAKAO);
    }

    @Test
    @DisplayName("인가 코드를 교환해 카카오 프로필을 반환한다")
    void exchangeReturnsProfile() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);

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
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + KAKAO_ACCESS_TOKEN))
                .andRespond(withSuccess(USER_RESPONSE, MediaType.APPLICATION_JSON));

        // when
        OauthProfile profile = client.exchange(AUTHORIZATION_CODE, CODE_VERIFIER);

        // then
        assertThat(profile.provider()).isEqualTo(Provider.KAKAO);
        assertThat(profile.providerId()).isEqualTo(PROVIDER_ID);
        assertThat(profile.email()).isEqualTo(EMAIL);

        mockServer.verify();
    }

    @Test
    @DisplayName("client_secret과 code_verifier가 없으면 폼에 담지 않는다")
    void exchangeOmitsBlankOptionalParameters() {
        // given - 콘솔에서 client_secret을 쓰지 않고, PKCE도 쓰지 않는 클라이언트
        KakaoOauthClient client = createClient("");

        // formData는 완전 일치를 검사하므로 이 4개만 있어야 통과한다
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("client_id", CLIENT_ID);
        expectedForm.add("redirect_uri", REDIRECT_URI);
        expectedForm.add("code", AUTHORIZATION_CODE);

        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(USER_RESPONSE, MediaType.APPLICATION_JSON));

        // when
        client.exchange(AUTHORIZATION_CODE, null);

        // then
        mockServer.verify();
    }

    @Test
    @DisplayName("토큰 요청이 실패하면 OauthCodeExchangeFailedException을 던진다")
    void exchangeFailsWhenTokenRequestRejected() {
        // given - 인가 코드 재사용 시 카카오가 KOE320으로 거부한다
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest()
                        .body("""
                                {"error":"invalid_grant","error_description":"authorization code not found","error_code":"KOE320"}
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
        // given - 200이지만 본문이 비어 있는 경우를 방어한다
        KakaoOauthClient client = createClient(CLIENT_SECRET);

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
        KakaoOauthClient client = createClient(CLIENT_SECRET);

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
    @DisplayName("이메일 동의를 받지 못하면 OauthEmailNotProvidedException을 던진다")
    void exchangeFailsWhenEmailNotAgreed() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(USER_RESPONSE_WITHOUT_EMAIL, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.exchange(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthEmailNotProvidedException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("kakao_account 자체가 없어도 OauthEmailNotProvidedException을 던진다")
    void exchangeFailsWhenKakaoAccountMissing() {
        // given
        KakaoOauthClient client = createClient(CLIENT_SECRET);

        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(USER_RESPONSE_WITHOUT_ACCOUNT, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.exchange(AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(OauthEmailNotProvidedException.class);

        mockServer.verify();
    }

}
