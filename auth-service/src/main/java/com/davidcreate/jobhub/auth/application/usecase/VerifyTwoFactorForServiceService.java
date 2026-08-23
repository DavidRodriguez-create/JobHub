package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.VerifyTwoFactorForServiceCommand;
import com.davidcreate.jobhub.auth.application.port.in.VerifyTwoFactorForServiceUseCase;
import com.davidcreate.jobhub.auth.application.port.in.VerifyTwoFactorOutcome;
import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.TooManyRequestsException;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorVerificationRequiredException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service-to-service 2FA authorization decision (ADR 0019). Reuses the same TOTP +
 * backup-code verification collaborators as {@link VerifyLoginTwoFactorService}
 * ({@link TwoFactorCodeMatcher}, {@link TotpCodeVerifier}, {@link TotpSecretRepository}):
 * no new crypto is introduced here.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class VerifyTwoFactorForServiceService implements VerifyTwoFactorForServiceUseCase {

    private final UserRepository userRepository;
    private final TotpSecretRepository totpSecretRepository;
    private final BackupCodeRepository backupCodeRepository;
    private final TotpCodeVerifier totpCodeVerifier;
    private final SecretEncryptor secretEncryptor;
    private final PasswordHasher passwordHasher;

    @ConfigProperty(name = "auth.two-factor.verify-max-attempts", defaultValue = "5")
    int verifyMaxAttempts;

    // In-memory counters keyed by userId, sufficient for single-instance; mirrors
    // EmailVerificationService's throttle pattern. Fresh per bean instance, so unit
    // tests naturally get a clean counter per test.
    private final Map<String, AtomicInteger> verifyFailures = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public VerifyTwoFactorOutcome verify(VerifyTwoFactorForServiceCommand command) {
        UUID userId = command.userId();
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isTwoFactorEnabled()) {
            return VerifyTwoFactorOutcome.NOT_ENROLLED;
        }

        String key = userId.toString();
        AtomicInteger counter = verifyFailures.computeIfAbsent(key, k -> new AtomicInteger(0));
        if (counter.get() >= verifyMaxAttempts) {
            throw new TooManyRequestsException("Too many failed two-factor verification attempts. Please try again later.");
        }

        String code = command.code();
        if (code == null) {
            counter.incrementAndGet();
            throw new TwoFactorVerificationRequiredException();
        }

        TotpSecret secret = totpSecretRepository.findByUserId(userId).orElse(null);
        if (secret == null) {
            counter.incrementAndGet();
            throw new TwoFactorVerificationRequiredException();
        }

        if (matchesTotp(secret, code)) {
            verifyFailures.remove(key);
            return VerifyTwoFactorOutcome.VERIFIED;
        }

        if (consumeBackupCodeIfMatches(secret, code)) {
            verifyFailures.remove(key);
            return VerifyTwoFactorOutcome.VERIFIED;
        }

        counter.incrementAndGet();
        throw new TwoFactorVerificationRequiredException();
    }

    private boolean matchesTotp(TotpSecret secret, String code) {
        String base32Secret = secretEncryptor.decrypt(secret.getEncryptedSecret());
        return totpCodeVerifier.verify(base32Secret, code);
    }

    private boolean consumeBackupCodeIfMatches(TotpSecret secret, String code) {
        Optional<BackupCode> match = TwoFactorCodeMatcher.findMatchingBackupCode(
                backupCodeRepository, passwordHasher, secret.getId(), code);
        match.ifPresent(backupCode -> backupCodeRepository.save(backupCode.toBuilder()
                .consumedAt(OffsetDateTime.now())
                .build()));
        return match.isPresent();
    }
}
