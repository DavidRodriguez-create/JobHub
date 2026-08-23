package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.ChangePasswordCommand;
import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.ChangePasswordService;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidCredentialsException;
import com.davidcreate.jobhub.auth.domain.exception.InvalidTotpCodeException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
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
@DisplayName("ChangePasswordService Unit Tests")
class ChangePasswordServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordHasher passwordHasher;
    @Mock TotpSecretRepository totpSecretRepository;
    @Mock BackupCodeRepository backupCodeRepository;
    @Mock TotpCodeVerifier totpCodeVerifier;
    @Mock SecretEncryptor secretEncryptor;
    @InjectMocks ChangePasswordService service;

    private final UUID id = UUID.randomUUID();
    private final User existing = User.builder()
            .id(id).email("a@b.com").firstName("Alice").lastName("M")
            .passwordHash("oldHash").build();

    private final UUID secretId = UUID.randomUUID();
    private final User twoFactorUser = User.builder()
            .id(id).email("a@b.com").firstName("Alice").lastName("M")
            .passwordHash("oldHash").twoFactorEnabled(true).build();
    private final TotpSecret secret = TotpSecret.builder()
            .id(secretId).userId(id).encryptedSecret("encrypted").verified(true).build();

    @Test
    @DisplayName("verifies current password and re-hashes the new one")
    void changesPassword() {
        when(userRepository.findUserById(id)).thenReturn(Optional.of(existing));
        when(passwordHasher.matches("oldPlain", "oldHash")).thenReturn(true);
        when(passwordHasher.hash("newpass1")).thenReturn("newHash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword(id, new ChangePasswordCommand("oldPlain", "newpass1"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("newHash");
    }

    @Test
    @DisplayName("throws InvalidCredentials when current password is wrong")
    void rejectsWrongCurrent() {
        when(userRepository.findUserById(id)).thenReturn(Optional.of(existing));
        when(passwordHasher.matches("wrong", "oldHash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordCommand("wrong", "newpass1")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws InvalidCredentials when current password is missing")
    void rejectsBlankCurrent() {
        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordCommand("", "newpass1")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(userRepository, never()).findUserById(any());
    }

    @Test
    @DisplayName("throws UserNotFound when user gone")
    void throwsWhenMissing() {
        when(userRepository.findUserById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordCommand("any", "newpass1")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("throws ValidationException when new password is too short")
    void rejectsShortNewPassword() {
        when(userRepository.findUserById(id)).thenReturn(Optional.of(existing));
        when(passwordHasher.matches("oldPlain", "oldHash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordCommand("oldPlain", "short")))
                .isInstanceOf(ValidationException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-CHPWD-U01: 2FA user with valid TOTP code changes password")
    void twoFactorUserWithValidTotpChangesPassword() {
        when(userRepository.findUserById(id)).thenReturn(Optional.of(twoFactorUser));
        when(passwordHasher.matches("oldPlain", "oldHash")).thenReturn(true);
        when(totpSecretRepository.findByUserId(id)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "123456")).thenReturn(true);
        when(passwordHasher.hash("newpass1")).thenReturn("newHash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword(id, new ChangePasswordCommand("oldPlain", "newpass1", "123456"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("newHash");
    }

    @Test
    @DisplayName("TC-CHPWD-U02: 2FA user with missing totpCode is rejected")
    void twoFactorUserWithMissingTotpRejected() {
        when(userRepository.findUserById(id)).thenReturn(Optional.of(twoFactorUser));
        when(passwordHasher.matches("oldPlain", "oldHash")).thenReturn(true);
        when(totpSecretRepository.findByUserId(id)).thenReturn(Optional.of(secret));

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordCommand("oldPlain", "newpass1", null)))
                .isInstanceOf(InvalidTotpCodeException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-CHPWD-U03: 2FA user with wrong totpCode is rejected")
    void twoFactorUserWithWrongTotpRejected() {
        when(userRepository.findUserById(id)).thenReturn(Optional.of(twoFactorUser));
        when(passwordHasher.matches("oldPlain", "oldHash")).thenReturn(true);
        when(totpSecretRepository.findByUserId(id)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "000000")).thenReturn(false);
        when(backupCodeRepository.findByTotpSecretId(secretId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordCommand("oldPlain", "newpass1", "000000")))
                .isInstanceOf(InvalidTotpCodeException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-CHPWD-U04: 2FA user with valid backup code changes password, code is consumed")
    void twoFactorUserWithValidBackupCodeChangesPassword() {
        when(userRepository.findUserById(id)).thenReturn(Optional.of(twoFactorUser));
        when(passwordHasher.matches("oldPlain", "oldHash")).thenReturn(true);
        when(totpSecretRepository.findByUserId(id)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "ABCD1234")).thenReturn(false);

        BackupCode backupCode = BackupCode.builder()
                .id(UUID.randomUUID()).totpSecretId(secretId).codeHash("hash-of-ABCD1234").build();
        when(backupCodeRepository.findByTotpSecretId(secretId)).thenReturn(List.of(backupCode));
        when(passwordHasher.matches("ABCD1234", "hash-of-ABCD1234")).thenReturn(true);
        when(passwordHasher.hash("newpass1")).thenReturn("newHash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(backupCodeRepository.save(any(BackupCode.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword(id, new ChangePasswordCommand("oldPlain", "newpass1", "ABCD1234"));

        ArgumentCaptor<BackupCode> codeCaptor = ArgumentCaptor.forClass(BackupCode.class);
        verify(backupCodeRepository).save(codeCaptor.capture());
        assertThat(codeCaptor.getValue().getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("TC-CHPWD-U05: non-2FA user changes password without totpCode (no regression)")
    void non2faUserChangesPasswordWithoutTotpCode() {
        when(userRepository.findUserById(id)).thenReturn(Optional.of(existing));
        when(passwordHasher.matches("oldPlain", "oldHash")).thenReturn(true);
        when(passwordHasher.hash("newpass1")).thenReturn("newHash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword(id, new ChangePasswordCommand("oldPlain", "newpass1", null));

        verify(totpSecretRepository, never()).findByUserId(any());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("newHash");
    }

    @Test
    @DisplayName("TC-CHPWD-U06: non-2FA user with totpCode present: field is ignored")
    void non2faUserWithTotpCodePresentIgnoresField() {
        when(userRepository.findUserById(id)).thenReturn(Optional.of(existing));
        when(passwordHasher.matches("oldPlain", "oldHash")).thenReturn(true);
        when(passwordHasher.hash("newpass1")).thenReturn("newHash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword(id, new ChangePasswordCommand("oldPlain", "newpass1", "999999"));

        verify(totpSecretRepository, never()).findByUserId(any());
        verify(totpCodeVerifier, never()).verify(any(), any());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("newHash");
    }

    // TC-459-N2 (ADR 0027 follow-up): a social-only account has no password to change
    // against. Guard the null hash instead of letting the BCrypt matcher NPE.
    @Test
    @DisplayName("TC-459-N2: social-only account (null password hash) → InvalidCredentialsException, no NPE")
    void nullPasswordHashThrowsInvalidCredentialsNotNpe() {
        User socialOnly = existing.toBuilder().passwordHash(null).build();
        when(userRepository.findUserById(id)).thenReturn(Optional.of(socialOnly));

        assertThatThrownBy(() -> service.changePassword(id,
                new ChangePasswordCommand("anyCurrentPassword", "newpass1")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordHasher, never()).matches(any(), any());
        verify(userRepository, never()).save(any());
    }
}
