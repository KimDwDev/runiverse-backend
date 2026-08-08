package com.runiverse.running_service.infrastructure.oauth.kakao;

import com.runiverse.running_service.application.auth.exception.OauthCodeExchangeFailedException;
import com.runiverse.running_service.application.auth.exception.OauthEmailNotProvidedException;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.infrastructure.oauth.OauthClient;
import com.runiverse.running_service.infrastructure.oauth.kakao.dto.KakaoTokenResponse;
import com.runiverse.running_service.infrastructure.oauth.kakao.dto.KakaoUserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class KakaoOauthClient implements OauthClient{
    private static final String GRANT_TYPE = "authorization_code";
    private static final String BEARER_PREFIX = "Bearer ";
    private final RestClient restClient;
    private final KakaoOauthProperties properties;
    KakaoOauthClient(
            @Qualifier("kakaoRestClient") RestClient restClient,
            KakaoOauthProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }
    @Override
    public Provider provider() {
        return Provider.KAKAO;
    }
    @Override
    public OauthProfile exchange(String authorizationCode, String codeVerifier) {
        try {
            String kakaoAccessToken = requestAccessToken(authorizationCode, codeVerifier);
            KakaoUserResponse user = fetchUser(kakaoAccessToken);
            return toProfile(user);
        } catch (RestClientException e) {
            log.warn("카카오 통신 실패", e);
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
        // REST API 키에 기본 활성화돼 있으면 필수다
        if (StringUtils.hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }
        KakaoTokenResponse response = restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    logFailure("토큰 요청", res);
                    throw new OauthCodeExchangeFailedException();
                })
                .body(KakaoTokenResponse.class);
        if (response == null || !StringUtils.hasText(response.accessToken())) {
            log.warn("카카오 토큰 응답에 access_token이 없다");
            throw new OauthCodeExchangeFailedException();
        }
        return response.accessToken();
    }
    // 카카오 액세스 토큰으로 사용자 정보 조회
    private KakaoUserResponse fetchUser(String kakaoAccessToken) {
        KakaoUserResponse response = restClient.get()
                .uri(properties.userInfoUri())
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + kakaoAccessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                    logFailure("사용자 정보 조회", res);
                    throw new OauthCodeExchangeFailedException();
                })
                .body(KakaoUserResponse.class);
        if (response == null || response.id() == null) {
            log.warn("카카오 사용자 응답에 id가 없다");
            throw new OauthCodeExchangeFailedException();
        }
        return response;
    }
    private OauthProfile toProfile(KakaoUserResponse response) {
        // 보낸 데이터 에서 email이 있으면 account로 받아온다
        KakaoUserResponse.KakaoAccount account = response.kakaoAccount();
        String email = (account == null) ? null : account.email();
        if (!StringUtils.hasText(email)) {
            throw new OauthEmailNotProvidedException();
        }
        return new OauthProfile(Provider.KAKAO, String.valueOf(response.id()), email);
    }
    private void logFailure(String step, ClientHttpResponse response) throws IOException {
        log.warn("카카오 {} 실패: status={}, body={}",
                step,
                response.getStatusCode(),
                StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8)
        );
    }
}
