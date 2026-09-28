package com.runiverse.running_service.application.auth.command.oauthlogin;

import com.runiverse.running_service.domain.user.vo.Provider;

// provider마다 앱이 받아 오는 자격 증명이 달라 커맨드 모양이 갈린다
public sealed interface OauthLoginCommand {

    Provider provider();

    record Kakao(
            String authorizationCode,
            String codeVerifier
    ) implements OauthLoginCommand {

        @Override
        public Provider provider() {
            return Provider.KAKAO;
        }
    }

    record Google(
            String idToken
    ) implements OauthLoginCommand {

        @Override
        public Provider provider() {
            return Provider.GOOGLE;
        }
    }
}
