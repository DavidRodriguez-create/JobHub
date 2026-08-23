package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.application.port.in.VerifyLoginTwoFactorCommand;
import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TokenGenerator;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.TwoFactorChallengeRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.VerifyLoginTwoFactorService;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.TwoFactorChallenge;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidTotpCodeException;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorChallengeInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerifyLoginTwoFactorService Unit Tests — TC-LOGIN2-U01..05")
class VerifyLoginTwoFactorServiceTest {

    @Mock UserRepository userRepository;
    @Mock TotpSecretRepository totpSecretRepository;
    @Mock BackupCodeRepository backupCodeRepository;
    @Mock TwoFactorChallengeRepository challengeRepository;
    @Mock TotpCodeVerifier totpCodeVerifier;
    @Mock SecretEncryptor secretEncryptor;
    @Mock PasswordHasher passwordHasher;
    @Mock TokenGenerator tokenGenerator;
    @InjectMocks VerifyLoginTwoFactorService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID secretId = UUID.randomUUID();
    private final UUID challengeId = UUID.randomUUID();
    private final User user = User.builder()
            .id(userId).email("alice@example.com").firstName("Alice").lastName("M")
            .passwordHash("hash").twoFactorEnabled(true).build();
    private final TotpSecret secret = TotpSecret.builder()
            .id(secretId).userId(userId).encryptedSecret("encrypted").verified(true).build();

    private TwoFactorChallenge usableChallenge() {
        return TwoFactorChallenge.builder()
                .id(challengeId).userId(userId).tokenHash("token-hash")
                .expiresAt(OffsetDateTime.now().plusMinutes(5)).build();
    }

    @Test
    @DisplayName("TC-LOGIN2-U01: valid challenge + valid TOTP code returns JWT")
    void validChallengeAndCodeReturnsJwt() {
        when(challengeRepository.findOneById(challengeId)).thenReturn(Optional.of(usableChallenge()));
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "123456")).thenReturn(true);
        when(tokenGenerator.generate(user)).thenReturn("jwt-token");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);
        when(challengeRepository.save(any(TwoFactorChallenge.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = service.verify(new VerifyLoginTwoFactorCommand(challengeId.toString(), "123456"));

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.isTwoFactorRequired()).isFalse();

        ArgumentCaptor<TwoFactorChallenge> captor = ArgumentCaptor.forClass(TwoFactorChallenge.class);
        verify(challengeRepository).save(captor.capture());
        assertThat(captor.getValue().getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("TC-LOGIN2-U02: wrong TOTP code does not consume challenge")
    void wrongCodeDoesNotConsumeChallenge() {
        when(challengeRepository.findOneById(challengeId)).thenReturn(Optional.of(usableChallenge()));
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "000000")).thenReturn(false);
        when(backupCodeRepository.findByTotpSecretId(secretId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.verify(new VerifyLoginTwoFactorCommand(challengeId.toString(), "000000")))
                .isInstanceOf(InvalidTotpCodeException.class);

        verify(challengeRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-LOGIN2-U03: backup code accepted in place of TOTP")
    void backupCodeAccepted() {
        when(challengeRepository.findOneById(challengeId)).thenReturn(Optional.of(usableChallenge()));
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "ABCD1234")).thenReturn(false);

        BackupCode backupCode = BackupCode.builder()
                .id(UUID.randomUUID()).totpSecretId(secretId).codeHash("hash-of-ABCD1234").build();
        when(backupCodeRepository.findByTotpSecretId(secretId)).thenReturn(List.of(backupCode));
        when(passwordHasher.matches("ABCD1234", "hash-of-ABCD1234")).thenReturn(true);
        when(tokenGenerator.generate(user)).thenReturn("jwt-token");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);
        when(challengeRepository.save(any(TwoFactorChallenge.class))).thenAnswer(inv -> inv.getArgument(0));
        when(backupCodeRepository.save(any(BackupCode.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResult result = service.verify(new VerifyLoginTwoFactorCommand(challengeId.toString(), "ABCD1234"));

        assertThat(result.token()).isEqualTo("jwt-token");
        ArgumentCaptor<BackupCode> codeCaptor = ArgumentCaptor.forClass(BackupCode.class);
        verify(backupCodeRepository).save(codeCaptor.capture());
        assertThat(codeCaptor.getValue().getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("TC-LOGIN2-U04: expired challenge rejected")
    void expiredChallengeRejected() {
        TwoFactorChallenge expired = TwoFactorChallenge.builder()
                .id(challengeId).userId(userId).tokenHash("token-hash")
                .expiresAt(OffsetDateTime.now().minusMinutes(1)).build();
        when(challengeRepository.findOneById(challengeId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.verify(new VerifyLoginTwoFactorCommand(challengeId.toString(), "123456")))
                .isInstanceOf(TwoFactorChallengeInvalidException.class);
        verify(userRepository, never()).findUserById(any());
    }

    @Test
    @DisplayName("TC-LOGIN2-U05: already-consumed challenge rejected")
    void consumedChallengeRejected() {
        TwoFactorChallenge consumed = TwoFactorChallenge.builder()
                .id(challengeId).userId(userId).tokenHash("token-hash")
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .consumedAt(OffsetDateTime.now().minusSeconds(10)).build();
        when(challengeRepository.findOneById(challengeId)).thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> service.verify(new VerifyLoginTwoFactorCommand(challengeId.toString(), "123456")))
                .isInstanceOf(TwoFactorChallengeInvalidException.class);
    }

    @Test
    @DisplayName("unknown challenge token rejected")
    void unknownChallengeRejected() {
        when(challengeRepository.findOneById(challengeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(new VerifyLoginTwoFactorCommand(challengeId.toString(), "123456")))
                .isInstanceOf(TwoFactorChallengeInvalidException.class);
    }

    @Test
    @DisplayName("malformed challenge token (not a UUID) rejected")
    void malformedChallengeRejected() {
        assertThatThrownBy(() -> service.verify(new VerifyLoginTwoFactorCommand("not-a-uuid", "123456")))
                .isInstanceOf(TwoFactorChallengeInvalidException.class);
    }
}
