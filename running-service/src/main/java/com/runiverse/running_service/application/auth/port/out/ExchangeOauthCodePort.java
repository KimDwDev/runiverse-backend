package com.runiverse.running_service.application.auth.port.out;

import com.runiverse.running_service.domain.user.vo.Provider;

public interface ExchangeOauthCodePort {

    OauthProfile exchange(Provider provider, String authorizationCode, String codeVerifier);
}
