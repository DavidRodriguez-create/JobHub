package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.ChangePasswordCommand;
import com.davidcreate.jobhub.auth.application.port.in.ChangePasswordUseCase;
import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidCredentialsException;
import com.davidcreate.jobhub.auth.domain.exception.InvalidTotpCodeException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import com.davidcreate.jobhub.auth.domain.valueobject.Password;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ChangePasswordService implements ChangePasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TotpSecretRepository totpSecretRepository;
    private final BackupCodeRepository backupCodeRepository;
    private final TotpCodeVerifier totpCodeVerifier;
    private final SecretEncryptor secretEncryptor;

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordCommand command) {
        if (command.currentPassword() == null || command.currentPassword().isEmpty()) {
            throw new InvalidCredentialsException();
        }
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // A social-only account (ADR 0027) has no password to change against; guard
        // the null hash instead of letting the BCrypt matcher NPE.
        if (user.getPasswordHash() == null) {
            throw new InvalidCredentialsException();
        }

        if (!passwordHasher.matches(command.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (user.isTwoFactorEnabled()) {
            enforceTwoFactorGuard(user, command.totpCode());
        }

        Password newPassword = Password.of(command.newPassword());
        userRepository.save(user.toBuilder()
                .passwordHash(passwordHasher.hash(newPassword.raw()))
                .build());
    }

    private void enforceTwoFactorGuard(User user, String totpCode) {
        TotpSecret secret = totpSecretRepository.findByUserId(user.getId())
                .orElseThrow(InvalidTotpCodeException::new);

        if (totpCode == null || totpCode.isBlank()) {
            throw new InvalidTotpCodeException();
        }

        if (matchesTotp(secret, totpCode)) {
            return;
        }

        Optional<BackupCode> match = TwoFactorCodeMatcher.findMatchingBackupCode(
                backupCodeRepository, passwordHasher, secret.getId(), totpCode);
        if (match.isEmpty()) {
            throw new InvalidTotpCodeException();
        }

        backupCodeRepository.save(match.get().toBuilder()
                .consumedAt(OffsetDateTime.now())
                .build());
    }

    private boolean matchesTotp(TotpSecret secret, String code) {
        String base32Secret = secretEncryptor.decrypt(secret.getEncryptedSecret());
        return totpCodeVerifier.verify(base32Secret, code);
    }
}
