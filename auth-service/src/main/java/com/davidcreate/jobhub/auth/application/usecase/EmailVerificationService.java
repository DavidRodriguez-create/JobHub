package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.ResendVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.in.VerifyEmailUseCase;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.entity.VerificationCode;
import com.davidcreate.jobhub.auth.domain.exception.InvalidVerificationException;
import com.davidcreate.jobhub.auth.domain.exception.TooManyRequestsException;
import com.davidcreate.jobhub.auth.domain.valueobject.Email;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
@RequiredArgsConstructor
public class EmailVerificationService implements VerifyEmailUseCase, ResendVerificationUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final VerificationNotifier notifier;

    @ConfigProperty(name = "auth.verification.code-ttl-seconds", defaultValue = "900")
    long codeTtlSeconds;

    @ConfigProperty(name = "auth.verification.verify-max-attempts", defaultValue = "5")
    int verifyMaxAttempts;

    @ConfigProperty(name = "auth.verification.resend-max-attempts", defaultValue = "5")
    int resendMaxAttempts;

    // In-memory counters keyed by email — sufficient for single-instance; testable without sleeps.
    private final Map<String, AtomicInteger> verifyFailures = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> resendAttempts = new ConcurrentHashMap<>();

    /**
     * Issues a hashed 6-digit verify-email code, persists it, and dispatches via the notifier.
     * Called by RegisterUserService and by {@link #resend} (after prior invalidation).
     */
    @Transactional
    public void sendFor(User user) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codeRepository.save(VerificationCode.builder()
                .userId(user.getId())
                .action(VerificationAction.VERIFY_EMAIL)
                .codeHash(passwordHasher.hash(code))
                .expiresAt(OffsetDateTime.now().plusSeconds(codeTtlSeconds))
                .build());
        notifier.sendActionCode(user.getEmail(), VerificationAction.VERIFY_EMAIL, code);
    }

    @Override
    @Transactional
    public User verify(String email, String code) {
        String normalized = Email.of(email).value();

        AtomicInteger counter = verifyFailures.computeIfAbsent(normalized, k -> new AtomicInteger(0));
        if (counter.get() >= verifyMaxAttempts) {
            throw new TooManyRequestsException("Too many failed verification attempts. Please try again later.");
        }

        User user = userRepository.findByEmail(normalized)
                .orElseGet(() -> {
                    counter.incrementAndGet();
                    throw new InvalidVerificationException("verification code is invalid or expired");
                });

        VerificationCode stored = codeRepository
                .findActiveByUserAndAction(user.getId(), VerificationAction.VERIFY_EMAIL)
                .orElseGet(() -> {
                    counter.incrementAndGet();
                    throw new InvalidVerificationException("verification code is invalid or expired");
                });

        if (!stored.isUsable(OffsetDateTime.now()) || !passwordHasher.matches(code, stored.getCodeHash())) {
            counter.incrementAndGet();
            throw new InvalidVerificationException("verification code is invalid or expired");
        }

        // Consume the code
        codeRepository.save(stored.toBuilder().consumedAt(OffsetDateTime.now()).build());

        // Mark user verified
        OffsetDateTime now = OffsetDateTime.now();
        User updated = userRepository.save(
                user.toBuilder().emailVerified(true).emailVerifiedAt(now).build());

        // Clear the failure counter on success
        verifyFailures.remove(normalized);
        return updated;
    }

    @Override
    @Transactional
    public void resend(String email) {
        String normalized = Email.of(email).value();

        AtomicInteger counter = resendAttempts.computeIfAbsent(normalized, k -> new AtomicInteger(0));
        if (counter.get() >= resendMaxAttempts) {
            throw new TooManyRequestsException("Too many resend attempts. Please try again later.");
        }
        counter.incrementAndGet();

        userRepository.findByEmail(normalized).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                codeRepository.consumeAllActiveByUserAndAction(user.getId(), VerificationAction.VERIFY_EMAIL);
                sendFor(user);
            }
        });
        // Unknown email and verified email are both silent no-ops (anti-enumeration).
    }

    /** Visible for testing: reset the verify failure counter for an email. */
    public void resetVerifyFailures(String email) {
        verifyFailures.remove(email);
    }

    /** Visible for testing: reset the resend counter for an email. */
    public void resetResendAttempts(String email) {
        resendAttempts.remove(email);
    }
}
