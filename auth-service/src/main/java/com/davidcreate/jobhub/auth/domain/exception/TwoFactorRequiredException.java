package com.davidcreate.jobhub.auth.domain.exception;

/**
 * Carries the opaque challenge token issued when a 2FA-enabled account
 * completes step 1 of login. Not currently thrown by LoginService (which
 * returns the challenge inline on LoginResult per ADR 0012 section "Decision"),
 * but kept available for adapters that prefer exception-based flow control.
 */
public class TwoFactorRequiredException extends RuntimeException {

    private final String twoFactorToken;

    public TwoFactorRequiredException(String twoFactorToken) {
        super("two-factor authentication required");
        this.twoFactorToken = twoFactorToken;
    }

    public String getTwoFactorToken() {
        return twoFactorToken;
    }
}
