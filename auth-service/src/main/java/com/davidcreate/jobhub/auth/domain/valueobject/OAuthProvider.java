package com.davidcreate.jobhub.auth.domain.valueobject;

import java.util.Arrays;
import java.util.Optional;

/**
 * The two providers the contract's {@code provider} path enum allows (ADR 0027).
 * Parsing here is a pure check, independent of whether a provider is actually
 * configured with credentials - that is {@code OAuthProviderClient.isConfigured()}'s
 * concern, checked only after this one passes.
 */
public enum OAuthProvider {

    GOOGLE("google"),
    GITHUB("github");

    private final String value;

    OAuthProvider(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<OAuthProvider> fromValue(String provider) {
        if (provider == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(p -> p.value.equalsIgnoreCase(provider))
                .findFirst();
    }
}
