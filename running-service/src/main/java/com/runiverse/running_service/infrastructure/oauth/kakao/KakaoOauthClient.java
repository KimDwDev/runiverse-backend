package com.runiverse.running_service.infrastructure.oauth.kakao;

import com.runiverse.running_service.application.auth.exception.OauthCodeExchangeFailedException;
import com.runiverse.running_service.application.auth.exception.OauthEmailNotProvidedException;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.application.user.port.out.UnlinkKakaoPort;
import com.runiverse.running_service.domain.user.vo.Provider;
import com.runiverse.running_service.domain.user.vo.ProviderId;
import com.runiverse.running_service.infrastructure.oauth.OauthClient;
import com.runiverse.running_service.infrastructure.oauth.kakao.dto.KakaoTokenResponse;
import com.runiverse.running_service.infrastructure.oauth.kakao.dto.KakaoUserResponse;
import lombok.extern.slf4j.Slf4j;
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
public class KakaoOauthClient implements OauthClient, UnlinkKakaoPort {

    private static final String GRANT_TYPE = "authorization_code";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String KAKAO_AK_PREFIX = "KakaoAK ";
    private static final String TARGET_ID_TYPE = "user_id";
    private final RestClient restClient;
    private final KakaoOauthProperties properties;

    KakaoOauthClient(
            RestClient restClient,
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

    // 탈퇴가 커밋된 뒤에 부른다. 실패해도 던지지 않는다 — 되돌릴 수 없고,
    // 남는 피해는 카카오 앱 목록에 이름이 남는 것뿐이다
    @Override
    public void unlink(ProviderId providerId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", TARGET_ID_TYPE);
        form.add("target_id", providerId.value());
        try {
            restClient.post()
                    .uri(properties.unlinkUri())
                    .header(HttpHeaders.AUTHORIZATION, KAKAO_AK_PREFIX + properties.unlinkAdminKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    // 본문을 남기지 않는다 — 카카오 오류 메시지에 어드민 키가 섞여 온다
                    .onStatus(HttpStatusCode::isError, (request, res) ->
                            log.warn("카카오 연동 해제 실패 — status={}", res.getStatusCode()))
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // 예외 메시지에도 응답 본문이 섞이므로 종류만 남긴다
            log.warn("카카오 연동 해제 통신 실패 — {}", e.getClass().getSimpleName());
        }
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
