package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.ListOAuthProvidersUseCase;
import com.davidcreate.jobhub.auth.application.port.in.OAuthProviderAvailability;
import com.davidcreate.jobhub.auth.application.port.out.OAuthProviderClient;
import com.davidcreate.jobhub.auth.domain.valueobject.OAuthProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * {@code GET /oauth/providers} (ADR 0028, Decision 2). Iterates
 * {@link OAuthProvider#values()} - not the injected beans - so the declaration
 * order (google, then github) is stable regardless of CDI resolution order
 * (AVAIL-6, TC-506-A26). Depends only on the existing
 * {@link OAuthProviderClient} out-port; no new port, no I/O.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class ListOAuthProvidersService implements ListOAuthProvidersUseCase {

    private final Instance<OAuthProviderClient> clients;

    @Override
    public List<OAuthProviderAvailability> list() {
        return Arrays.stream(OAuthProvider.values())
                .map(provider -> new OAuthProviderAvailability(provider, isConfigured(provider)))
                .toList();
    }

    private boolean isConfigured(OAuthProvider provider) {
        return clients.stream()
                .filter(client -> client.supports(provider.value()))
                .findFirst()
                .map(OAuthProviderClient::isConfigured)
                .orElse(false);
    }
}
