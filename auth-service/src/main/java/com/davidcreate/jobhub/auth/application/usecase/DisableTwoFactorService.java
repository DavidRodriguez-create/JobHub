package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.DisableTwoFactorUseCase;
import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidTotpCodeException;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorNotEnabledException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class DisableTwoFactorService implements DisableTwoFactorUseCase {

    private final UserRepository userRepository;
    private final TotpSecretRepository totpSecretRepository;
    private final BackupCodeRepository backupCodeRepository;
    private final TotpCodeVerifier totpCodeVerifier;
    private final SecretEncryptor secretEncryptor;
    private final PasswordHasher passwordHasher;

    @Override
    @Transactional
    public void disable(UUID userId, String totpCode) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isTwoFactorEnabled()) {
            throw new TwoFactorNotEnabledException();
        }

        TotpSecret secret = totpSecretRepository.findByUserId(userId)
                .orElseThrow(InvalidTotpCodeException::new);

        if (!matchesTotp(secret, totpCode) && !matchesBackupCode(secret, totpCode)) {
            throw new InvalidTotpCodeException();
        }

        userRepository.save(user.toBuilder().twoFactorEnabled(false).build());
        backupCodeRepository.removeAllByTotpSecretId(secret.getId());
        totpSecretRepository.removeByUserId(userId);
    }

    private boolean matchesTotp(TotpSecret secret, String code) {
        String base32Secret = secretEncryptor.decrypt(secret.getEncryptedSecret());
        return totpCodeVerifier.verify(base32Secret, code);
    }

    private boolean matchesBackupCode(TotpSecret secret, String code) {
        Optional<BackupCode> match = TwoFactorCodeMatcher.findMatchingBackupCode(
                backupCodeRepository, passwordHasher, secret.getId(), code);
        return match.isPresent();
    }
}
