package com.runiverse.running_service.infrastructure.oauth;

import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.vo.Provider;

public interface OauthClient {

    Provider provider();

    OauthProfile exchange(String authorizationCode, String codeVerifier);
}
