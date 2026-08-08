package com.runiverse.running_service.unit_test.infrastructure.oauth;

import com.runiverse.running_service.infrastructure.oauth.OauthClientRouter;

import com.runiverse.running_service.infrastructure.oauth.OauthClient;

import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.exception.UnsupportedProviderException;
import com.runiverse.running_service.domain.user.vo.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OauthClientRouterTest {

    private static final String AUTHORIZATION_CODE = "authorization-code";
    private static final String CODE_VERIFIER = "pkce-code-verifier";

    @Mock
    private OauthClient kakaoOauthClient;

    @Mock
    private OauthClient googleOauthClient;

    // 두 provider가 모두 등록된 실제 구동 상태를 재현한다
    private OauthClientRouter createRouter() {
        when(kakaoOauthClient.provider()).thenReturn(Provider.KAKAO);
        when(googleOauthClient.provider()).thenReturn(Provider.GOOGLE);

        return new OauthClientRouter(List.of(kakaoOauthClient, googleOauthClient));
    }

    @Test
    @DisplayName("KAKAO 요청은 카카오 클라이언트로 위임한다")
    void exchangeDelegatesToKakaoClient() {
        // given
        OauthProfile profile =
                new OauthProfile(Provider.KAKAO, "1234567890", "kakao@example.com");

        OauthClientRouter router = createRouter();
        when(kakaoOauthClient.exchange(AUTHORIZATION_CODE, CODE_VERIFIER)).thenReturn(profile);

        // when
        OauthProfile result =
                router.exchange(Provider.KAKAO, AUTHORIZATION_CODE, CODE_VERIFIER);

        // then
        assertThat(result).isSameAs(profile);
    }

    @Test
    @DisplayName("GOOGLE 요청은 구글 클라이언트로 위임한다")
    void exchangeDelegatesToGoogleClient() {
        // given
        OauthProfile profile =
                new OauthProfile(Provider.GOOGLE, "107812345678901234567", "google@example.com");

        OauthClientRouter router = createRouter();
        when(googleOauthClient.exchange(AUTHORIZATION_CODE, CODE_VERIFIER)).thenReturn(profile);

        // when
        OauthProfile result =
                router.exchange(Provider.GOOGLE, AUTHORIZATION_CODE, CODE_VERIFIER);

        // then
        assertThat(result).isSameAs(profile);
    }

    @Test
    @DisplayName("등록된 클라이언트가 없는 provider면 예외를 던진다")
    void exchangeRejectsUnregisteredProvider() {
        // given -> 어떤 클라이언트도 등록되지 않은 라우터
        OauthClientRouter router = new OauthClientRouter(List.of());

        // when & then
        assertThatThrownBy(() ->
                router.exchange(Provider.KAKAO, AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(UnsupportedProviderException.class);
    }

}
