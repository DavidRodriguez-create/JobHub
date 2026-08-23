package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.OAuthAuthorizationResult;
import com.davidcreate.jobhub.auth.application.port.in.StartOAuthAuthorizationUseCase;
import com.davidcreate.jobhub.auth.application.port.out.OAuthProviderClient;
import com.davidcreate.jobhub.auth.domain.exception.ProviderNotConfiguredException;
import com.davidcreate.jobhub.auth.domain.valueobject.OAuthProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.RequiredArgsConstructor;

import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
@RequiredArgsConstructor
public class StartOAuthAuthorizationService implements StartOAuthAuthorizationUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Instance<OAuthProviderClient> clients;

    @Override
    public OAuthAuthorizationResult start(String provider) {
        OAuthProvider.fromValue(provider).orElseThrow(() -> new ProviderNotConfiguredException(provider));
        OAuthProviderClient client = findClient(provider);
        String state = generateState();
        return new OAuthAuthorizationResult(client.buildAuthorizationUrl(state), state);
    }

    private OAuthProviderClient findClient(String provider) {
        return clients.stream()
                .filter(c -> c.supports(provider))
                .filter(OAuthProviderClient::isConfigured)
                .findFirst()
                .orElseThrow(() -> new ProviderNotConfiguredException(provider));
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
