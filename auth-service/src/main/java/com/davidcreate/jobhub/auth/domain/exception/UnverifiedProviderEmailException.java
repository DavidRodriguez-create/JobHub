package com.davidcreate.jobhub.auth.domain.exception;

/**
 * BR1 step 4 / BR2: the provider email is NOT provider-verified and it collides
 * with an existing account. Auto-linking is refused to prevent account takeover
 * (ADR 0027); no identity or account is created or altered.
 */
public class UnverifiedProviderEmailException extends RuntimeException {
    public UnverifiedProviderEmailException() {
        super("sign in with your existing method, then link this provider from account settings");
    }
}
