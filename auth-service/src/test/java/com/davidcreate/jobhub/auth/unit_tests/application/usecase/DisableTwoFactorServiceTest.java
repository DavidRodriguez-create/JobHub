package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.DisableTwoFactorService;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidTotpCodeException;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorNotEnabledException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
@DisplayName("DisableTwoFactorService Unit Tests — TC-DIS-U01..04")
class DisableTwoFactorServiceTest {

    @Mock UserRepository userRepository;
    @Mock TotpSecretRepository totpSecretRepository;
    @Mock BackupCodeRepository backupCodeRepository;
    @Mock TotpCodeVerifier totpCodeVerifier;
    @Mock SecretEncryptor secretEncryptor;
    @Mock PasswordHasher passwordHasher;
    @InjectMocks DisableTwoFactorService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID secretId = UUID.randomUUID();
    private final User user = User.builder()
            .id(userId).email("alice@example.com").firstName("Alice").lastName("M")
            .passwordHash("hash").twoFactorEnabled(true).build();
    private final TotpSecret secret = TotpSecret.builder()
            .id(secretId).userId(userId).encryptedSecret("encrypted").verified(true).build();

    @Test
    @DisplayName("TC-DIS-U01: valid TOTP code disables 2FA, deletes secret and backup codes")
    void validCodeDisables() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "123456")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.disable(userId, "123456");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isTwoFactorEnabled()).isFalse();
        verify(backupCodeRepository).removeAllByTotpSecretId(secretId);
        verify(totpSecretRepository).removeByUserId(userId);
    }

    @Test
    @DisplayName("TC-DIS-U02: wrong TOTP code rejected, 2FA stays enabled")
    void wrongCodeRejected() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "000000")).thenReturn(false);
        when(backupCodeRepository.findByTotpSecretId(secretId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.disable(userId, "000000"))
                .isInstanceOf(InvalidTotpCodeException.class);

        verify(userRepository, never()).save(any());
        verify(totpSecretRepository, never()).removeByUserId(any());
    }

    @Test
    @DisplayName("TC-DIS-U03: backup code accepted to disable")
    void backupCodeAccepted() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "ABCD1234")).thenReturn(false);

        BackupCode backupCode = BackupCode.builder()
                .id(UUID.randomUUID()).totpSecretId(secretId).codeHash("hash-of-ABCD1234").build();
        when(backupCodeRepository.findByTotpSecretId(secretId)).thenReturn(List.of(backupCode));
        when(passwordHasher.matches("ABCD1234", "hash-of-ABCD1234")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.disable(userId, "ABCD1234");

        verify(totpSecretRepository).removeByUserId(userId);
        verify(backupCodeRepository).removeAllByTotpSecretId(secretId);
    }

    @Test
    @DisplayName("TC-DIS-U04: disable rejected when 2FA not enabled")
    void rejectedWhenNotEnabled() {
        User disabledUser = user.toBuilder().twoFactorEnabled(false).build();
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> service.disable(userId, "123456"))
                .isInstanceOf(TwoFactorNotEnabledException.class);
        verify(totpSecretRepository, never()).removeByUserId(any());
    }
}
