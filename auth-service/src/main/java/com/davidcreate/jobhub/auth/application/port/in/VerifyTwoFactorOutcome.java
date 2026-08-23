package com.davidcreate.jobhub.auth.application.port.in;

/**
 * Result of a successful service-to-service 2FA verification (ADR 0019). Both outcomes
 * authorize the caller to proceed; they differ only in why. A failed verification is
 * signalled by an exception, not a value of this enum.
 */
public enum VerifyTwoFactorOutcome {

    /** The user has 2FA enabled and the supplied code was valid. */
    VERIFIED,

    /** The user has no 2FA enabled; no code was needed. */
    NOT_ENROLLED
}
