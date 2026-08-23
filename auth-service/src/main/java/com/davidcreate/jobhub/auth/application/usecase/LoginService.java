package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.LoginCommand;
import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.application.port.in.LoginUseCase;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.TokenGenerator;
import com.davidcreate.jobhub.auth.application.port.out.TwoFactorChallengeRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.TwoFactorChallenge;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.EmailNotVerifiedException;
import com.davidcreate.jobhub.auth.domain.exception.InvalidCredentialsException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@ApplicationScoped
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenGenerator tokenGenerator;
    private final TwoFactorChallengeRepository challengeRepository;

    @ConfigProperty(name = "auth.totp.challenge-ttl-minutes", defaultValue = "5")
    long challengeTtlMinutes;

    @Override
    @Transactional
    public LoginResult login(LoginCommand command) {
        if (command.email() == null || command.password() == null) {
            throw new InvalidCredentialsException();
        }
        User user = userRepository.findByEmail(command.email().trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);

        // A social-only account (ADR 0027) has no password hash; treat it the same
        // as a bad password (401, no enumeration, no NPE from the BCrypt matcher).
        if (user.getPasswordHash() == null) {
            throw new InvalidCredentialsException();
        }

        // Check password first, never leak verified state on bad credentials.
        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // Password correct — now enforce email-verified gate (403, not 401).
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        if (user.isTwoFactorEnabled()) {
            return issueChallenge(user);
        }

        String token = tokenGenerator.generate(user);
        return new LoginResult(token, tokenGenerator.lifespanSeconds(), user);
    }

    private LoginResult issueChallenge(User user) {
        TwoFactorChallenge challenge = TwoFactorChallenge.builder()
                .userId(user.getId())
                .tokenHash(hash(user.getId().toString() + OffsetDateTime.now()))
                .expiresAt(OffsetDateTime.now().plusMinutes(challengeTtlMinutes))
                .build();
        TwoFactorChallenge saved = challengeRepository.save(challenge);
        return new LoginResult(null, 0, null, saved.getId().toString());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
