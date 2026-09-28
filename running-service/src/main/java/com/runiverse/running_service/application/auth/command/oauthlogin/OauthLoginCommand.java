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

    // 목록에 없는 provider 경로로 들어온 요청 — 실패로 세고 거절하려고 핸들러까지 보낸다
    record Unsupported(
            String rawProvider
    ) implements OauthLoginCommand {

        // 대응하는 Provider가 없다 — 핸들러는 이 커맨드를 받으면 provider()를 쓰기 전에 거절한다
        @Override
        public Provider provider() {
            return null;
        }
    }
}
