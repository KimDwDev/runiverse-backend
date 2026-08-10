package com.runiverse.running_service.infrastructure.oauth;

import com.runiverse.running_service.application.auth.port.out.ExchangeOauthCodePort;
import com.runiverse.running_service.application.auth.port.out.OauthProfile;
import com.runiverse.running_service.domain.user.exception.UnsupportedProviderException;
import com.runiverse.running_service.domain.user.vo.Provider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OauthClientRouter implements ExchangeOauthCodePort {

    private final Map<Provider, OauthClient> clients;

    public OauthClientRouter(List<OauthClient> clientList) {
        this.clients = clientList.stream()
                .collect(Collectors.toMap(OauthClient::provider, Function.identity()));
    }

    @Override
    public OauthProfile exchange(Provider provider, String authorizationCode, String codeVerifier) {
        OauthClient client = clients.get(provider);
        if (client == null) {
            throw new UnsupportedProviderException();
        }
        return client.exchange(authorizationCode, codeVerifier);
    }
}
