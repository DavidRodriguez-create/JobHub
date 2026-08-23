package com.davidcreate.jobhub.auth.application.port.in;

public interface CompleteOAuthLoginUseCase {

    /**
     * Resolves BR1's account-linking order (existing link, verified-email auto-link,
     * JIT create, or refuse) and returns the same {@link LoginResult} shape password
     * login uses, including the 2FA-challenge branch when the resolved account has
     * TOTP enabled (ADR 0027).
     *
     * @throws com.davidcreate.jobhub.auth.domain.exception.OAuthStateMismatchException      400
     * @throws com.davidcreate.jobhub.auth.domain.exception.ProviderNotConfiguredException    404
     * @throws com.davidcreate.jobhub.auth.domain.exception.ProviderAuthorizationFailedException 401
     * @throws com.davidcreate.jobhub.auth.domain.exception.UnverifiedProviderEmailException  401
     * @throws com.davidcreate.jobhub.auth.domain.exception.ProviderEmailUnavailableException  401
     * @throws com.davidcreate.jobhub.auth.domain.exception.ProviderUnavailableException       502
     */
    LoginResult handle(OAuthCallbackCommand command);
}
