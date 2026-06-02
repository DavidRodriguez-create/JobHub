package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.entity.EmailVerificationToken;

import java.util.Optional;

public interface EmailVerificationTokenRepository {

    EmailVerificationToken save(EmailVerificationToken token);

    Optional<EmailVerificationToken> findByToken(String token);
}
