package com.runiverse.running_service.infrastructure.oauth.google;

import com.runiverse.running_service.application.auth.exception.OauthCodeExchangeFailedException;
import com.runiverse.running_service.application.auth.exception.OauthEmailNotProvidedException;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.infrastructure.oauth.OauthClient;
import com.runiverse.running_service.infrastructure.oauth.OauthErrorCode;
import com.runiverse.running_service.infrastructure.oauth.google.dto.GoogleTokenResponse;
import com.runiverse.running_service.infrastructure.oauth.google.dto.GoogleUserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Slf4j
@Component
public class GoogleOauthClient implements OauthClient {

    private static final String GRANT_TYPE = "authorization_code";
    private static final String BEARER_PREFIX = "Bearer ";
    private final RestClient restClient;
    private final GoogleOauthProperties properties;
    private final JsonMapper jsonMapper;

    GoogleOauthClient(
            RestClient restClient,
            GoogleOauthProperties properties,
            JsonMapper jsonMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Provider provider() {
        return Provider.GOOGLE;
    }

    @Override
    public OauthProfile exchange(String authorizationCode, String codeVerifier) {
        try {
            String googleAccessToken = requestAccessToken(authorizationCode, codeVerifier);
            GoogleUserResponse user = fetchUser(googleAccessToken);
            return toProfile(user);
        } catch (RestClientException e) {
            log.error("[인증] 구글 로그인 실패: 구글 통신 오류", e);
            throw new OauthCodeExchangeFailedException();
        }
    }

    private String requestAccessToken(String authorizationCode, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE);
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);
        // 앱이 PKCE로 인가를 시작하므로 검증값은 항상 온다
        form.add("code_verifier", codeVerifier);
        // 모바일 클라이언트 타입은 secret이 없다
        if (StringUtils.hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }
        GoogleTokenResponse response = restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    logFailure("[인증] 구글 토큰 요청 실패: 구글 응답 오류", res);
                    throw new OauthCodeExchangeFailedException();
                })
                .body(GoogleTokenResponse.class);
        if (response == null || !StringUtils.hasText(response.accessToken())) {
            log.error("[인증] 구글 토큰 요청 실패: access_token 누락");
            throw new OauthCodeExchangeFailedException();
        }
        return response.accessToken();
    }

    // 구글 액세스 토큰으로 사용자 정보 조회
    private GoogleUserResponse fetchUser(String googleAccessToken) {
        GoogleUserResponse response = restClient.get()
                .uri(properties.userInfoUri())
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + googleAccessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    logFailure("[인증] 구글 사용자 조회 실패: 구글 응답 오류", res);
                    throw new OauthCodeExchangeFailedException();
                })
                .body(GoogleUserResponse.class);
        if (response == null || !StringUtils.hasText(response.sub())) {
            log.error("[인증] 구글 사용자 조회 실패: sub 누락");
            throw new OauthCodeExchangeFailedException();
        }
        return response;
    }

    private OauthProfile toProfile(GoogleUserResponse response) {
        // email 스코프에 동의하지 않으면 필드 자체가 빠진다
        if (!StringUtils.hasText(response.email())) {
            throw new OauthEmailNotProvidedException();
        }
        return new OauthProfile(Provider.GOOGLE, response.sub(), response.email());
    }

    // 문구는 호출하는 곳에서 고정 문자열로 넘긴다 — 변하는 값은 key=value로만 붙인다
    private void logFailure(String message, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        String errorCode = OauthErrorCode.of(jsonMapper, response);
        if (status.is5xxServerError()) {
            log.error(message + " - status={}, errorCode={}", status.value(), errorCode);
            return;
        }
        log.warn(message + " - status={}, errorCode={}", status.value(), errorCode);
    }
}
