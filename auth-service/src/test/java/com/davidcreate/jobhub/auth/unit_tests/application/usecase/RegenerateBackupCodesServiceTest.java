package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.RegenerateBackupCodesService;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidTotpCodeException;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorNotEnabledException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegenerateBackupCodesService Unit Tests — TC-REGEN-U01..03")
class RegenerateBackupCodesServiceTest {

    @Mock UserRepository userRepository;
    @Mock TotpSecretRepository totpSecretRepository;
    @Mock BackupCodeRepository backupCodeRepository;
    @Mock TotpCodeVerifier totpCodeVerifier;
    @Mock SecretEncryptor secretEncryptor;
    @InjectMocks RegenerateBackupCodesService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID secretId = UUID.randomUUID();
    private final User user = User.builder()
            .id(userId).email("alice@example.com").firstName("Alice").lastName("M")
            .passwordHash("hash").twoFactorEnabled(true).build();
    private final TotpSecret secret = TotpSecret.builder()
            .id(secretId).userId(userId).encryptedSecret("encrypted").verified(true).build();

    @Test
    @DisplayName("TC-REGEN-U01: valid TOTP code regenerates 8 new backup codes")
    void regeneratesCodes() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "123456")).thenReturn(true);
        when(backupCodeRepository.saveAll(eq(secretId), anyList()))
                .thenAnswer(inv -> {
                    List<String> codes = inv.getArgument(1);
                    return codes.stream().map(c -> BackupCode.builder().id(UUID.randomUUID())
                            .totpSecretId(secretId).codeHash("h-" + c).build()).toList();
                });

        List<String> codes = service.regenerate(userId, "123456");

        assertThat(codes).hasSize(8);
        assertThat(codes).doesNotHaveDuplicates();
        verify(backupCodeRepository).removeAllByTotpSecretId(secretId);
    }

    @Test
    @DisplayName("TC-REGEN-U02: backup code rejected (not a TOTP code)")
    void backupCodeRejected() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "ABCD1234")).thenReturn(false);

        assertThatThrownBy(() -> service.regenerate(userId, "ABCD1234"))
                .isInstanceOf(InvalidTotpCodeException.class);

        verify(backupCodeRepository, never()).removeAllByTotpSecretId(secretId);
    }

    @Test
    @DisplayName("TC-REGEN-U03: regeneration rejected when 2FA not enabled")
    void rejectedWhenNotEnabled() {
        User disabledUser = user.toBuilder().twoFactorEnabled(false).build();
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> service.regenerate(userId, "123456"))
                .isInstanceOf(TwoFactorNotEnabledException.class);
    }
}
