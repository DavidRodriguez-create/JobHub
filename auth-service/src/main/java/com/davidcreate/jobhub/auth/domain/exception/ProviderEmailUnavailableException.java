package com.davidcreate.jobhub.auth.domain.exception;

/**
 * BR7: the provider returned no usable email at all for a first-time subject.
 * {@code auth.user.email} is NOT NULL UNIQUE, so provisioning is impossible
 * without some email value; no account or identity is created.
 */
public class ProviderEmailUnavailableException extends RuntimeException {
    public ProviderEmailUnavailableException() {
        super("your provider account has no visible or verified email; verify one with your "
                + "provider or sign up with a password instead");
    }
}
