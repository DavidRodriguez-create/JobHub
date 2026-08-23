package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.VerifyTwoFactorSetupUseCase;
import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorAlreadyEnabledException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class VerifyTwoFactorSetupService implements VerifyTwoFactorSetupUseCase {

    private final UserRepository userRepository;
    private final TotpSecretRepository totpSecretRepository;
    private final BackupCodeRepository backupCodeRepository;
    private final TotpCodeVerifier totpCodeVerifier;
    private final SecretEncryptor secretEncryptor;

    @Override
    @Transactional
    public List<String> verifySetup(UUID userId, String totpCode) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.isTwoFactorEnabled()) {
            throw new TwoFactorAlreadyEnabledException();
        }

        TotpSecret pending = totpSecretRepository.findByUserId(userId)
                .orElseThrow(() -> new ValidationException("No pending 2FA setup exists; call POST /account/2fa/setup first"));

        String base32Secret = secretEncryptor.decrypt(pending.getEncryptedSecret());
        if (!totpCodeVerifier.verify(base32Secret, totpCode)) {
            throw new ValidationException("TOTP code is incorrect");
        }

        TotpSecret verified = pending.toBuilder()
                .verified(true)
                .verifiedAt(OffsetDateTime.now())
                .build();
        TotpSecret saved = totpSecretRepository.save(verified);

        userRepository.save(user.toBuilder().twoFactorEnabled(true).build());

        List<String> rawCodes = BackupCodeGenerator.generate();
        backupCodeRepository.saveAll(saved.getId(), rawCodes);
        return rawCodes;
    }
}
