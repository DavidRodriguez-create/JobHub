package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.TwoFactorSetupResult;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.SetupTwoFactorService;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorAlreadyEnabledException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SetupTwoFactorService Unit Tests — TC-SETUP-U01..03")
class SetupTwoFactorServiceTest {

    @Mock UserRepository userRepository;
    @Mock TotpSecretRepository totpSecretRepository;
    @Mock TotpCodeVerifier totpCodeVerifier;
    @Mock SecretEncryptor secretEncryptor;
    @InjectMocks SetupTwoFactorService service;

    private final UUID userId = UUID.randomUUID();
    private final User user = User.builder()
            .id(userId).email("alice@example.com").firstName("Alice").lastName("M")
            .passwordHash("hash").twoFactorEnabled(false).build();

    @Test
    @DisplayName("TC-SETUP-U01: generates secret and returns otpauth URI for user without 2FA")
    void generatesSecretForUserWithout2fa() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(totpCodeVerifier.generateSecret()).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.buildOtpAuthUri("BASE32SECRET", "alice@example.com"))
                .thenReturn("otpauth://totp/JobHub:alice@example.com?secret=BASE32SECRET&issuer=JobHub");
        when(secretEncryptor.encrypt("BASE32SECRET")).thenReturn("encrypted");
        when(totpSecretRepository.save(any(TotpSecret.class))).thenAnswer(inv -> inv.getArgument(0));

        TwoFactorSetupResult result = service.setup(userId);

        assertThat(result.setupKey()).isEqualTo("BASE32SECRET");
        assertThat(result.otpauthUri()).contains("BASE32SECRET");

        ArgumentCaptor<TotpSecret> captor = ArgumentCaptor.forClass(TotpSecret.class);
        verify(totpSecretRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedSecret()).isEqualTo("encrypted");
        assertThat(captor.getValue().isVerified()).isFalse();
    }

    @Test
    @DisplayName("TC-SETUP-U02: rejects setup when 2FA already enabled")
    void rejectsWhenAlreadyEnabled() {
        User enabledUser = user.toBuilder().twoFactorEnabled(true).build();
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(enabledUser));

        assertThatThrownBy(() -> service.setup(userId))
                .isInstanceOf(TwoFactorAlreadyEnabledException.class);
        verify(totpSecretRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-SETUP-U03: overwrites unverified pending secret on retry")
    void overwritesUnverifiedSecretOnRetry() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));
        TotpSecret pending = TotpSecret.builder()
                .id(UUID.randomUUID()).userId(userId).encryptedSecret("old-encrypted").verified(false).build();
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(pending));
        when(totpCodeVerifier.generateSecret()).thenReturn("NEWSECRET");
        when(totpCodeVerifier.buildOtpAuthUri("NEWSECRET", "alice@example.com")).thenReturn("otpauth://new");
        when(secretEncryptor.encrypt("NEWSECRET")).thenReturn("new-encrypted");
        when(totpSecretRepository.save(any(TotpSecret.class))).thenAnswer(inv -> inv.getArgument(0));

        TwoFactorSetupResult result = service.setup(userId);

        assertThat(result.setupKey()).isEqualTo("NEWSECRET");
        ArgumentCaptor<TotpSecret> captor = ArgumentCaptor.forClass(TotpSecret.class);
        verify(totpSecretRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(pending.getId());
        assertThat(captor.getValue().getEncryptedSecret()).isEqualTo("new-encrypted");
    }

    @Test
    @DisplayName("throws UserNotFound when user gone")
    void throwsWhenUserMissing() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setup(userId))
                .isInstanceOf(UserNotFoundException.class);
    }
}
