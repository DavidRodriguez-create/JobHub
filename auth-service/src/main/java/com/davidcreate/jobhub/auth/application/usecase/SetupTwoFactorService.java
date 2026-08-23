package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.SetupTwoFactorUseCase;
import com.davidcreate.jobhub.auth.application.port.in.TwoFactorSetupResult;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorAlreadyEnabledException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class SetupTwoFactorService implements SetupTwoFactorUseCase {

    private final UserRepository userRepository;
    private final TotpSecretRepository totpSecretRepository;
    private final TotpCodeVerifier totpCodeVerifier;
    private final SecretEncryptor secretEncryptor;

    @Override
    @Transactional
    public TwoFactorSetupResult setup(UUID userId) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.isTwoFactorEnabled()) {
            throw new TwoFactorAlreadyEnabledException();
        }

        String base32Secret = totpCodeVerifier.generateSecret();
        String otpauthUri = totpCodeVerifier.buildOtpAuthUri(base32Secret, user.getEmail());
        String encrypted = secretEncryptor.encrypt(base32Secret);

        TotpSecret pending = totpSecretRepository.findByUserId(userId).orElse(null);
        TotpSecret toSave = (pending != null ? pending.toBuilder() : TotpSecret.builder().userId(userId))
                .encryptedSecret(encrypted)
                .verified(false)
                .verifiedAt(null)
                .build();
        totpSecretRepository.save(toSave);

        return new TwoFactorSetupResult(otpauthUri, base32Secret);
    }
}
