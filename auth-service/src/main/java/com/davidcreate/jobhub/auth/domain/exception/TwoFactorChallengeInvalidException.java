package com.davidcreate.jobhub.auth.domain.exception;

/**
 * Raised when a 2FA login challenge token does not exist, is expired, or has
 * already been consumed (BR10, BR11). Maps to HTTP 400 — distinct from
 * {@link InvalidTotpCodeException} (401, wrong code with a still-usable
 * challenge).
 */
public class TwoFactorChallengeInvalidException extends RuntimeException {

    public TwoFactorChallengeInvalidException() {
        super("two-factor challenge token is invalid, expired, or already used");
    }
}
