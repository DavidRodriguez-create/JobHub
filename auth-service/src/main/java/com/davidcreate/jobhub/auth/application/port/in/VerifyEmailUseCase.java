package com.davidcreate.jobhub.auth.application.port.in;

public interface VerifyEmailUseCase {

    /** Consume the token and mark the owning account's email as verified. */
    void verify(String token);
}
