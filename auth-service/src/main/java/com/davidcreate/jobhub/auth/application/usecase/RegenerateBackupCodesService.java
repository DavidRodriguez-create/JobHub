package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.RegenerateBackupCodesUseCase;
import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidTotpCodeException;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorNotEnabledException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Regenerates the eight backup (recovery) codes for a 2FA-enabled account.
 * Per BR13, only a live TOTP code is accepted here, never a backup code
 * (regenerating with a backup code would let a stolen code mint more codes).
 */
@ApplicationScoped
@RequiredArgsConstructor
public class RegenerateBackupCodesService implements RegenerateBackupCodesUseCase {

    private final UserRepository userRepository;
    private final TotpSecretRepository totpSecretRepository;
    private final BackupCodeRepository backupCodeRepository;
    private final TotpCodeVerifier totpCodeVerifier;
    private final SecretEncryptor secretEncryptor;

    @Override
    @Transactional
    public List<String> regenerate(UUID userId, String totpCode) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isTwoFactorEnabled()) {
            throw new TwoFactorNotEnabledException();
        }

        TotpSecret secret = totpSecretRepository.findByUserId(userId)
                .orElseThrow(InvalidTotpCodeException::new);

        String base32Secret = secretEncryptor.decrypt(secret.getEncryptedSecret());
        if (!totpCodeVerifier.verify(base32Secret, totpCode)) {
            throw new InvalidTotpCodeException();
        }

        backupCodeRepository.removeAllByTotpSecretId(secret.getId());
        List<String> rawCodes = BackupCodeGenerator.generate();
        backupCodeRepository.saveAll(secret.getId(), rawCodes);
        return rawCodes;
    }
}
