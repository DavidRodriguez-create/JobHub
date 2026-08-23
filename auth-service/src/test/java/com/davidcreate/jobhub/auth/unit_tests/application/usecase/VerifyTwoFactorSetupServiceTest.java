package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.VerifyTwoFactorSetupService;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorAlreadyEnabledException;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerifyTwoFactorSetupService Unit Tests — TC-VERIFY-U01..04")
class VerifyTwoFactorSetupServiceTest {

    @Mock UserRepository userRepository;
    @Mock TotpSecretRepository totpSecretRepository;
    @Mock BackupCodeRepository backupCodeRepository;
    @Mock TotpCodeVerifier totpCodeVerifier;
    @Mock SecretEncryptor secretEncryptor;
    @InjectMocks VerifyTwoFactorSetupService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID secretId = UUID.randomUUID();
    private final User user = User.builder()
            .id(userId).email("alice@example.com").firstName("Alice").lastName("M")
            .passwordHash("hash").twoFactorEnabled(false).build();

    @Test
    @DisplayName("TC-VERIFY-U01: valid first code enables 2FA and returns 8 backup codes")
    void validCodeEnables2fa() {
        TotpSecret pending = TotpSecret.builder()
                .id(secretId).userId(userId).encryptedSecret("encrypted").verified(false).build();
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(pending));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "123456")).thenReturn(true);
        when(totpSecretRepository.save(any(TotpSecret.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(backupCodeRepository.saveAll(eq(secretId), anyList()))
                .thenAnswer(inv -> {
                    List<String> codes = inv.getArgument(1);
                    return codes.stream().map(c -> BackupCode.builder().id(UUID.randomUUID())
                            .totpSecretId(secretId).codeHash("h-" + c).build()).toList();
                });

        List<String> backupCodes = service.verifySetup(userId, "123456");

        assertThat(backupCodes).hasSize(8);
        assertThat(backupCodes).doesNotHaveDuplicates();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isTwoFactorEnabled()).isTrue();

        ArgumentCaptor<TotpSecret> secretCaptor = ArgumentCaptor.forClass(TotpSecret.class);
        verify(totpSecretRepository).save(secretCaptor.capture());
        assertThat(secretCaptor.getValue().isVerified()).isTrue();
    }

    @Test
    @DisplayName("TC-VERIFY-U02: wrong code rejects and leaves 2FA disabled")
    void wrongCodeRejects() {
        TotpSecret pending = TotpSecret.builder()
                .id(secretId).userId(userId).encryptedSecret("encrypted").verified(false).build();
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(pending));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.verifySetup(userId, "000000"))
                .isInstanceOf(ValidationException.class);

        verify(userRepository, never()).save(any());
        verify(totpSecretRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-VERIFY-U03: no pending secret returns error")
    void noPendingSecretThrows() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifySetup(userId, "123456"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("TC-VERIFY-U04: already enabled rejects (409 case)")
    void alreadyEnabledRejects() {
        User enabledUser = user.toBuilder().twoFactorEnabled(true).build();
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(enabledUser));

        assertThatThrownBy(() -> service.verifySetup(userId, "123456"))
                .isInstanceOf(TwoFactorAlreadyEnabledException.class);
    }
}
