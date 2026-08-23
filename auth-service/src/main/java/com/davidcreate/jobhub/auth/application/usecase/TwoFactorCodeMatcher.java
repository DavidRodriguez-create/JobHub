package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared matching logic for "TOTP code or backup code" verification used by
 * change-password, disable, and login-step-2 flows (ADR 0012, BR13).
 */
final class TwoFactorCodeMatcher {

    private TwoFactorCodeMatcher() {
    }

    /**
     * Finds the first unconsumed backup code whose hash matches {@code rawCode}.
     * Does not consume it: callers decide whether to persist consumption.
     */
    static Optional<BackupCode> findMatchingBackupCode(BackupCodeRepository backupCodeRepository,
                                                         PasswordHasher passwordHasher,
                                                         UUID totpSecretId,
                                                         String rawCode) {
        List<BackupCode> codes = backupCodeRepository.findByTotpSecretId(totpSecretId);
        for (BackupCode code : codes) {
            if (code.isUsable() && passwordHasher.matches(rawCode, code.getCodeHash())) {
                return Optional.of(code);
            }
        }
        return Optional.empty();
    }
}
