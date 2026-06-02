package com.davidcreate.jobhub.auth.application.port.in;

public interface ResendVerificationUseCase {

    /**
     * Send a fresh verification email if the address is registered and unverified.
     * Silently no-ops otherwise, to avoid email enumeration.
     */
    void resend(String email);
}
