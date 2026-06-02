package com.davidcreate.jobhub.auth.application.port.in;

import com.davidcreate.jobhub.auth.domain.entity.User;

/**
 * Issues a fresh email-verification token for a user and dispatches the verification
 * email. Used by registration and by the resend-verification flow.
 */
public interface SendEmailVerificationUseCase {

    void sendFor(User user);
}
