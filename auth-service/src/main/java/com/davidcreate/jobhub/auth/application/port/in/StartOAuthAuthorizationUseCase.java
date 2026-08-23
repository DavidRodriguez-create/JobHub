package com.davidcreate.jobhub.auth.application.port.in;

public interface StartOAuthAuthorizationUseCase {

    /**
     * @throws com.davidcreate.jobhub.auth.domain.exception.ProviderNotConfiguredException
     *         {@code provider} is not {@code google}/{@code github}, or has no
     *         client id/secret configured (404, OAUTH-START-3)
     */
    OAuthAuthorizationResult start(String provider);
}
