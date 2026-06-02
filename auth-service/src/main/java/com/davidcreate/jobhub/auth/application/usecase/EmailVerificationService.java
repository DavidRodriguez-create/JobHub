package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.ResendVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.in.SendEmailVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.in.VerifyEmailUseCase;
import com.davidcreate.jobhub.auth.application.port.out.EmailVerificationTokenRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.entity.EmailVerificationToken;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidVerificationException;
import com.davidcreate.jobhub.auth.domain.valueobject.Email;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class EmailVerificationService
        implements SendEmailVerificationUseCase, VerifyEmailUseCase, ResendVerificationUseCase {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final VerificationNotifier notifier;

    @ConfigProperty(name = "auth.verification.email-token-ttl-seconds", defaultValue = "86400")
    long tokenTtlSeconds;

    @Override
    @Transactional
    public void sendFor(User user) {
        String token = UUID.randomUUID().toString();
        tokenRepository.save(EmailVerificationToken.builder()
                .userId(user.getId())
                .token(token)
                .expiresAt(OffsetDateTime.now().plusSeconds(tokenTtlSeconds))
                .build());
        notifier.sendEmailVerification(user.getEmail(), token);
    }

    @Override
    @Transactional
    public void verify(String token) {
        EmailVerificationToken stored = tokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidVerificationException("verification token is invalid"));
        OffsetDateTime now = OffsetDateTime.now();
        if (!stored.isUsable(now)) {
            throw new InvalidVerificationException("verification token is invalid or expired");
        }
        User user = userRepository.findUserById(stored.getUserId())
                .orElseThrow(() -> new InvalidVerificationException("verification token is invalid"));

        userRepository.save(user.toBuilder().emailVerified(true).emailVerifiedAt(now).build());
        tokenRepository.save(stored.toBuilder().consumedAt(now).build());
    }

    @Override
    @Transactional
    public void resend(String email) {
        String normalized = Email.of(email).value();
        userRepository.findByEmail(normalized).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                sendFor(user);
            }
        });
    }
}
