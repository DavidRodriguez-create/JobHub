package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.ConsumeVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.in.DeleteAccountUseCase;
import com.davidcreate.jobhub.auth.application.port.in.RequestVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.entity.VerificationCode;
import com.davidcreate.jobhub.auth.domain.exception.InvalidVerificationException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class AccountVerificationService
        implements RequestVerificationUseCase, DeleteAccountUseCase, ConsumeVerificationUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final VerificationNotifier notifier;

    @ConfigProperty(name = "auth.verification.code-ttl-seconds", defaultValue = "900")
    long codeTtlSeconds;

    @Override
    @Transactional
    public VerificationResult request(UUID userId, VerificationAction action) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        VerificationCode saved = codeRepository.save(VerificationCode.builder()
                .userId(userId)
                .action(action)
                .codeHash(passwordHasher.hash(code))
                .expiresAt(OffsetDateTime.now().plusSeconds(codeTtlSeconds))
                .build());

        notifier.sendActionCode(user.getEmail(), action, code);
        return new VerificationResult(saved.getId(), saved.getExpiresAt());
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID verificationId, String code) {
        validateAndConsume(userId, verificationId, code, VerificationAction.DELETE_ACCOUNT);
        userRepository.removeById(userId);
    }

    @Override
    @Transactional
    public void consume(UUID userId, UUID verificationId, String code, VerificationAction action) {
        validateAndConsume(userId, verificationId, code, action);
    }

    private void validateAndConsume(UUID userId, UUID verificationId, String code, VerificationAction action) {
        VerificationCode stored = codeRepository.findOneById(verificationId)
                .orElseThrow(() -> new InvalidVerificationException("verification code is invalid"));

        boolean valid = stored.getUserId().equals(userId)
                && stored.getAction() == action
                && stored.isUsable(OffsetDateTime.now())
                && passwordHasher.matches(code, stored.getCodeHash());
        if (!valid) {
            throw new InvalidVerificationException("verification code is invalid or expired");
        }

        codeRepository.save(stored.toBuilder().consumedAt(OffsetDateTime.now()).build());
    }
}
