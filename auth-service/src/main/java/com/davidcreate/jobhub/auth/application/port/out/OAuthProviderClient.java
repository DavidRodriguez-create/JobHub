package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.valueobject.ExternalIdentity;

/**
 * One implementation per provider (google, github), selected by {@link #supports(String)}
 * (ADR 0027). Mirrors the {@code JobSourceClient}/{@code Instance<T>} selection pattern
 * already used in crawler-service.
 */
public interface OAuthProviderClient {

    boolean supports(String provider);

    /**
     * False when the provider has no client id/secret configured (dev placeholder
     * left blank); callers treat this the same as an unknown provider (404, OAUTH-START-3).
     */
    boolean isConfigured();

    /**
     * Builds the fully-formed provider authorization URL (client id, redirect_uri,
     * scope, and the given {@code state} already applied).
     */
    String buildAuthorizationUrl(String state);

    /**
     * Exchanges the authorization code for a provider access token (outbound HTTP)
     * and resolves the provider's userinfo into an {@link ExternalIdentity}.
     *
     * @throws com.davidcreate.jobhub.auth.domain.exception.ProviderAuthorizationFailedException
     *         the provider rejected the code (401, OAUTH-ERR-2)
     * @throws com.davidcreate.jobhub.auth.domain.exception.ProviderUnavailableException
     *         the provider's token or userinfo endpoint was unreachable or returned a 5xx (502, OAUTH-ERR-4)
     */
    ExternalIdentity exchange(String code);
}
