package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.application.port.in.VerifyLoginTwoFactorCommand;
import com.davidcreate.jobhub.auth.application.port.in.VerifyLoginTwoFactorUseCase;
import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TokenGenerator;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.TwoFactorChallengeRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.TwoFactorChallenge;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidTotpCodeException;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorChallengeInvalidException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class VerifyLoginTwoFactorService implements VerifyLoginTwoFactorUseCase {

    private final UserRepository userRepository;
    private final TotpSecretRepository totpSecretRepository;
    private final BackupCodeRepository backupCodeRepository;
    private final TwoFactorChallengeRepository challengeRepository;
    private final TotpCodeVerifier totpCodeVerifier;
    private final SecretEncryptor secretEncryptor;
    private final PasswordHasher passwordHasher;
    private final TokenGenerator tokenGenerator;

    @Override
    @Transactional
    public LoginResult verify(VerifyLoginTwoFactorCommand command) {
        UUID challengeId = parseToken(command.twoFactorToken());

        TwoFactorChallenge challenge = challengeRepository.findOneById(challengeId)
                .orElseThrow(TwoFactorChallengeInvalidException::new);

        if (!challenge.isUsable(OffsetDateTime.now())) {
            throw new TwoFactorChallengeInvalidException();
        }

        User user = userRepository.findUserById(challenge.getUserId())
                .orElseThrow(() -> new UserNotFoundException(challenge.getUserId()));

        TotpSecret secret = totpSecretRepository.findByUserId(user.getId())
                .orElseThrow(InvalidTotpCodeException::new);

        if (!matchesTotp(secret, command.totpCode()) && !consumeBackupCodeIfMatches(secret, command.totpCode())) {
            throw new InvalidTotpCodeException();
        }

        challengeRepository.save(challenge.toBuilder()
                .consumedAt(OffsetDateTime.now())
                .build());

        String token = tokenGenerator.generate(user);
        return new LoginResult(token, tokenGenerator.lifespanSeconds(), user);
    }

    private UUID parseToken(String twoFactorToken) {
        try {
            return UUID.fromString(twoFactorToken);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new TwoFactorChallengeInvalidException();
        }
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
