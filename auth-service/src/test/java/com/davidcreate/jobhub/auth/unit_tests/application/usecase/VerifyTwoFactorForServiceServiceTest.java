package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.VerifyTwoFactorForServiceCommand;
import com.davidcreate.jobhub.auth.application.port.in.VerifyTwoFactorOutcome;
import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.VerifyTwoFactorForServiceService;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.TooManyRequestsException;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorVerificationRequiredException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VerifyTwoFactorForServiceService} (ADR 0019, story #384/#388).
 * Covers TC-384-A4..A13.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerifyTwoFactorForServiceService Unit Tests (TC-384-A4..A13)")
class VerifyTwoFactorForServiceServiceTest {

    @Mock UserRepository userRepository;
    @Mock TotpSecretRepository totpSecretRepository;
    @Mock BackupCodeRepository backupCodeRepository;
    @Mock TotpCodeVerifier totpCodeVerifier;
    @Mock SecretEncryptor secretEncryptor;
    @Mock PasswordHasher passwordHasher;

    // Constructed manually so the @ConfigProperty int field (verifyMaxAttempts) gets a
    // deterministic value: @InjectMocks leaves non-final fields untouched, and there is
    // no CDI context in a plain unit test. Mirrors EmailVerificationServiceTest's pattern.
    VerifyTwoFactorForServiceService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID secretId = UUID.randomUUID();

    private final User enrolledUser = User.builder()
            .id(userId).email("admin@example.com").firstName("Admin").lastName("A")
            .passwordHash("hash").twoFactorEnabled(true).build();
    private final User notEnrolledUser = User.builder()
            .id(userId).email("plain@example.com").firstName("Plain").lastName("U")
            .passwordHash("hash").twoFactorEnabled(false).build();
    private final TotpSecret secret = TotpSecret.builder()
            .id(secretId).userId(userId).encryptedSecret("encrypted").verified(true).build();

    @BeforeEach
    void setUp() throws Exception {
        service = new VerifyTwoFactorForServiceService(
                userRepository, totpSecretRepository, backupCodeRepository,
                totpCodeVerifier, secretEncryptor, passwordHasher);
        setField("verifyMaxAttempts", 5);
    }

    @Test
    @DisplayName("TC-384-A4: not enrolled, code null -> not_enrolled")
    void notEnrolledNullCode() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(notEnrolledUser));

        VerifyTwoFactorOutcome outcome = service.verify(new VerifyTwoFactorForServiceCommand(userId, null));

        assertThat(outcome).isEqualTo(VerifyTwoFactorOutcome.NOT_ENROLLED);
    }

    @Test
    @DisplayName("TC-384-A5: not enrolled, arbitrary well-formed code -> not_enrolled, code ignored (zero TOTP/backup interactions)")
    void notEnrolledArbitraryCodeIgnored() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(notEnrolledUser));

        VerifyTwoFactorOutcome outcome = service.verify(new VerifyTwoFactorForServiceCommand(userId, "123456"));

        assertThat(outcome).isEqualTo(VerifyTwoFactorOutcome.NOT_ENROLLED);
        verifyNoInteractions(totpCodeVerifier);
        verifyNoInteractions(backupCodeRepository);
    }

    @Test
    @DisplayName("TC-384-A6: enabled + valid TOTP -> verified, no BackupCodeRepository interaction at all")
    void enabledValidTotpVerifiedNoBackupTouch() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(enrolledUser));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "123456")).thenReturn(true);

        VerifyTwoFactorOutcome outcome = service.verify(new VerifyTwoFactorForServiceCommand(userId, "123456"));

        assertThat(outcome).isEqualTo(VerifyTwoFactorOutcome.VERIFIED);
        verifyNoInteractions(backupCodeRepository);
    }

    @Test
    @DisplayName("TC-384-A7: enabled + code null -> TwoFactorVerificationRequiredException")
    void enabledNullCodeThrows() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(enrolledUser));

        assertThatThrownBy(() -> service.verify(new VerifyTwoFactorForServiceCommand(userId, null)))
                .isInstanceOf(TwoFactorVerificationRequiredException.class);
    }

    @Test
    @DisplayName("TC-384-A8: enabled + well-formed code matching neither TOTP nor backup -> TwoFactorVerificationRequiredException")
    void enabledWrongCodeThrows() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(enrolledUser));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "000000")).thenReturn(false);
        when(backupCodeRepository.findByTotpSecretId(secretId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.verify(new VerifyTwoFactorForServiceCommand(userId, "000000")))
                .isInstanceOf(TwoFactorVerificationRequiredException.class);
    }

    @Test
    @DisplayName("TC-384-A9: enabled + TOTP outside its valid window -> TwoFactorVerificationRequiredException")
    void enabledTotpOutsideWindowThrows() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(enrolledUser));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "654321")).thenReturn(false);
        when(backupCodeRepository.findByTotpSecretId(secretId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.verify(new VerifyTwoFactorForServiceCommand(userId, "654321")))
                .isInstanceOf(TwoFactorVerificationRequiredException.class);
    }

    @Test
    @DisplayName("TC-384-A10: enabled + unconsumed backup code -> verified, consumption persisted exactly once")
    void enabledUnconsumedBackupCodeVerifiedAndConsumed() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(enrolledUser));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "ABCD1234")).thenReturn(false);

        BackupCode backupCode = BackupCode.builder()
                .id(UUID.randomUUID()).totpSecretId(secretId).codeHash("hash-of-ABCD1234").build();
        when(backupCodeRepository.findByTotpSecretId(secretId)).thenReturn(List.of(backupCode));
        when(passwordHasher.matches("ABCD1234", "hash-of-ABCD1234")).thenReturn(true);

        VerifyTwoFactorOutcome outcome = service.verify(new VerifyTwoFactorForServiceCommand(userId, "ABCD1234"));

        assertThat(outcome).isEqualTo(VerifyTwoFactorOutcome.VERIFIED);
        ArgumentCaptor<BackupCode> captor = ArgumentCaptor.forClass(BackupCode.class);
        verify(backupCodeRepository).save(captor.capture());
        assertThat(captor.getValue().getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("TC-384-A11: enabled + already-consumed backup code -> TwoFactorVerificationRequiredException")
    void enabledConsumedBackupCodeRejected() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(enrolledUser));
        when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        when(totpCodeVerifier.verify("BASE32SECRET", "ABCD1234")).thenReturn(false);

        BackupCode consumedCode = BackupCode.builder()
                .id(UUID.randomUUID()).totpSecretId(secretId).codeHash("hash-of-ABCD1234")
                .consumedAt(java.time.OffsetDateTime.now()).build();
        // Consumed codes are excluded from matching (TwoFactorCodeMatcher.isUsable()==false),
        // mirroring the real repository's behaviour: it is simply not a candidate.
        when(backupCodeRepository.findByTotpSecretId(secretId)).thenReturn(List.of(consumedCode));

        assertThatThrownBy(() -> service.verify(new VerifyTwoFactorForServiceCommand(userId, "ABCD1234")))
                .isInstanceOf(TwoFactorVerificationRequiredException.class);
        verify(backupCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-384-A12: no user exists -> UserNotFoundException regardless of code")
    void unknownUserThrowsRegardlessOfCode() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(new VerifyTwoFactorForServiceCommand(userId, "123456")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("TC-384-A13: throttle after max failed attempts, even with an otherwise-valid code (fail fast, no crypto work)")
    void throttleAfterMaxAttempts() throws Exception {
        setField("verifyMaxAttempts", 2);
        lenient().when(userRepository.findUserById(userId)).thenReturn(Optional.of(enrolledUser));
        lenient().when(totpSecretRepository.findByUserId(userId)).thenReturn(Optional.of(secret));
        lenient().when(secretEncryptor.decrypt("encrypted")).thenReturn("BASE32SECRET");
        lenient().when(totpCodeVerifier.verify("BASE32SECRET", "wrong")).thenReturn(false);
        lenient().when(backupCodeRepository.findByTotpSecretId(secretId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.verify(new VerifyTwoFactorForServiceCommand(userId, "wrong")))
                .isInstanceOf(TwoFactorVerificationRequiredException.class);
        assertThatThrownBy(() -> service.verify(new VerifyTwoFactorForServiceCommand(userId, "wrong")))
                .isInstanceOf(TwoFactorVerificationRequiredException.class);

        // Third call: throttled even though "123456" would otherwise need checking.
        assertThatThrownBy(() -> service.verify(new VerifyTwoFactorForServiceCommand(userId, "123456")))
                .isInstanceOf(TooManyRequestsException.class);
        verify(totpCodeVerifier, never()).verify("BASE32SECRET", "123456");
    }

    private void setField(String name, Object value) throws Exception {
        Field f = VerifyTwoFactorForServiceService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }
}
