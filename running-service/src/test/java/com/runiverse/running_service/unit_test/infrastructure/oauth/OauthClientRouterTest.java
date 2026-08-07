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

    private static final String AUTHORIZATION_CODE = "kakao-authorization-code";
    private static final String CODE_VERIFIER = "pkce-code-verifier";

    @Mock
    private OauthClient kakaoOauthClient;

    @Test
    @DisplayName("provider에 해당하는 클라이언트로 위임한다")
    void exchangeDelegatesToMatchingClient() {
        // given
        OauthProfile profile =
                new OauthProfile(Provider.KAKAO, "1234567890", "kakao@example.com");

        when(kakaoOauthClient.provider()).thenReturn(Provider.KAKAO);
        when(kakaoOauthClient.exchange(AUTHORIZATION_CODE, CODE_VERIFIER)).thenReturn(profile);

        OauthClientRouter router = new OauthClientRouter(List.of(kakaoOauthClient));

        // when
        OauthProfile result =
                router.exchange(Provider.KAKAO, AUTHORIZATION_CODE, CODE_VERIFIER);

        // then
        assertThat(result).isSameAs(profile);
    }

    @Test
    @DisplayName("등록된 클라이언트가 없는 provider면 예외를 던진다")
    void exchangeRejectsUnregisteredProvider() {
        // given
        when(kakaoOauthClient.provider()).thenReturn(Provider.KAKAO);

        OauthClientRouter router = new OauthClientRouter(List.of(kakaoOauthClient));

        // when & then
        assertThatThrownBy(() ->
                router.exchange(Provider.GOOGLE, AUTHORIZATION_CODE, CODE_VERIFIER))
                .isInstanceOf(UnsupportedProviderException.class);
    }

}
