package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.application.usecase.EmailVerificationService;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.entity.VerificationCode;
import com.davidcreate.jobhub.auth.domain.exception.InvalidVerificationException;
import com.davidcreate.jobhub.auth.domain.exception.TooManyRequestsException;
import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EV-U-03: sendFor persists a hashed 6-digit code (not UUID) and dispatches via sendActionCode(VERIFY_EMAIL)
 * EV-U-04: verify happy path — email→user→active code→hash-match→consume→emailVerified=true
 * EV-U-05: verify rejects wrong code → InvalidVerificationException
 * EV-U-06: verify rejects expired code → InvalidVerificationException
 * EV-U-07: verify rejects already-consumed code → InvalidVerificationException
 * EV-U-08: login-before-verify: LoginService throws EmailNotVerifiedException for unverified user
 * EV-U-09: resend for unverified → invalidates prior + sends fresh code
 * EV-U-10: resend for verified → no-op, no dispatch
 * EV-U-11: resend for unknown email → no-op, no dispatch
 * EV-U-12: resend with malformed email → ValidationException
 * EV-U-13: VerificationCode.isUsable returns true for unconsumed unexpired, false otherwise
 * EV-U-14: VerificationCodeMapper maps VERIFY_EMAIL action correctly
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationService Unit Tests (code path) — EV-U-03..12")
class EmailVerificationServiceTest {

    @Mock
    VerificationCodeRepository codeRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    PasswordHasher passwordHasher;
    @Mock
    VerificationNotifier notifier;

    // Constructed manually so @ConfigProperty int fields (verifyMaxAttempts, resendMaxAttempts,
    // codeTtlSeconds) get proper values — @InjectMocks leaves them at 0 because they are not
    // final and therefore not part of the @RequiredArgsConstructor-generated constructor.
    EmailVerificationService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "test@example.com";
    private static final String HASHED = "hashed";
    private static final String CORRECT_CODE = "123456";

    private User unverifiedUser;
    private User verifiedUser;

    @BeforeEach
    void setUp() throws Exception {
        service = new EmailVerificationService(codeRepository, userRepository, passwordHasher, notifier);
        // @ConfigProperty fields are package-private and injected by CDI at runtime.
        // In a plain unit test there is no CDI context, so set them via reflection.
        setField("codeTtlSeconds", 900L);
        setField("verifyMaxAttempts", 5);
        setField("resendMaxAttempts", 5);

        unverifiedUser = User.builder().id(USER_ID).email(EMAIL).emailVerified(false).build();
        verifiedUser = User.builder().id(USER_ID).email(EMAIL).emailVerified(true).build();

        // Default: save returns the passed code with an ID.
        // Lenient because some tests (resendMalformedEmail) throw before reaching codeRepository.
        lenient().when(codeRepository.save(any(VerificationCode.class))).thenAnswer(inv -> {
            VerificationCode c = inv.getArgument(0);
            return c.toBuilder().id(UUID.randomUUID()).build();
        });
    }

    // EV-U-03
    @Test
    @DisplayName("EV-U-03: sendFor persists a hashed 6-digit code and dispatches via sendActionCode(VERIFY_EMAIL)")
    void sendForPersistsHashedCodeAndNotifies() {
        when(passwordHasher.hash(anyString())).thenReturn(HASHED);

        OffsetDateTime before = OffsetDateTime.now();
        service.sendFor(unverifiedUser);

        ArgumentCaptor<VerificationCode> cap = ArgumentCaptor.forClass(VerificationCode.class);
        verify(codeRepository).save(cap.capture());
        VerificationCode saved = cap.getValue();

        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getAction()).isEqualTo(VerificationAction.VERIFY_EMAIL);
        assertThat(saved.getCodeHash()).isEqualTo(HASHED);
        // expiresAt is set to now()+TTL inside sendFor; it must be strictly after the timestamp
        // captured before the call (TTL=900 s, so there is no race condition).
        assertThat(saved.getExpiresAt()).isAfter(before);

        // The plain code passed to hash() must be exactly 6 digits.
        ArgumentCaptor<String> rawCodeCap = ArgumentCaptor.forClass(String.class);
        verify(passwordHasher).hash(rawCodeCap.capture());
        assertThat(rawCodeCap.getValue()).matches("\\d{6}");

        verify(notifier).sendActionCode(eq(EMAIL), eq(VerificationAction.VERIFY_EMAIL), anyString());
    }

    // EV-U-04
    @Test
    @DisplayName("EV-U-04: verify happy path marks user emailVerified=true and consumes code")
    void verifyHappyPath() {
        UUID codeId = UUID.randomUUID();
        VerificationCode activeCode = VerificationCode.builder()
                .id(codeId).userId(USER_ID).action(VerificationAction.VERIFY_EMAIL)
                .codeHash(HASHED).expiresAt(OffsetDateTime.now().plusMinutes(10)).build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser));
        when(codeRepository.findActiveByUserAndAction(USER_ID, VerificationAction.VERIFY_EMAIL))
                .thenReturn(Optional.of(activeCode));
        when(passwordHasher.matches(CORRECT_CODE, HASHED)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.verify(EMAIL, CORRECT_CODE);

        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getEmailVerifiedAt()).isNotNull();

        ArgumentCaptor<VerificationCode> codeCap = ArgumentCaptor.forClass(VerificationCode.class);
        verify(codeRepository).save(codeCap.capture());
        assertThat(codeCap.getValue().getConsumedAt()).isNotNull();
    }

    // EV-U-05
    @Test
    @DisplayName("EV-U-05: verify rejects wrong code → InvalidVerificationException")
    void verifyWrongCode() {
        VerificationCode activeCode = VerificationCode.builder()
                .id(UUID.randomUUID()).userId(USER_ID).action(VerificationAction.VERIFY_EMAIL)
                .codeHash(HASHED).expiresAt(OffsetDateTime.now().plusMinutes(10)).build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser));
        when(codeRepository.findActiveByUserAndAction(USER_ID, VerificationAction.VERIFY_EMAIL))
                .thenReturn(Optional.of(activeCode));
        when(passwordHasher.matches("999999", HASHED)).thenReturn(false);

        assertThatThrownBy(() -> service.verify(EMAIL, "999999"))
                .isInstanceOf(InvalidVerificationException.class);
        verify(userRepository, never()).save(any());
    }

    // EV-U-06
    @Test
    @DisplayName("EV-U-06: verify rejects expired code → InvalidVerificationException")
    void verifyExpiredCode() {
        VerificationCode expiredCode = VerificationCode.builder()
                .id(UUID.randomUUID()).userId(USER_ID).action(VerificationAction.VERIFY_EMAIL)
                .codeHash(HASHED).expiresAt(OffsetDateTime.now().minusMinutes(1)).build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser));
        when(codeRepository.findActiveByUserAndAction(USER_ID, VerificationAction.VERIFY_EMAIL))
                .thenReturn(Optional.of(expiredCode));

        assertThatThrownBy(() -> service.verify(EMAIL, CORRECT_CODE))
                .isInstanceOf(InvalidVerificationException.class);
        verify(userRepository, never()).save(any());
    }

    // EV-U-07
    @Test
    @DisplayName("EV-U-07: verify rejects already-consumed code → InvalidVerificationException")
    void verifyConsumedCode() {
        // findActiveByUserAndAction must NOT return consumed codes — the repository
        // filters them out. Returning empty simulates that correctly.
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser));
        when(codeRepository.findActiveByUserAndAction(USER_ID, VerificationAction.VERIFY_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(EMAIL, CORRECT_CODE))
                .isInstanceOf(InvalidVerificationException.class);
        verify(userRepository, never()).save(any());
    }

    // EV-U-09
    @Test
    @DisplayName("EV-U-09: resend for unverified user → invalidates prior codes then sends fresh")
    void resendUnverified() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser));
        when(passwordHasher.hash(anyString())).thenReturn("hashed2");

        service.resend(EMAIL);

        verify(codeRepository).consumeAllActiveByUserAndAction(USER_ID, VerificationAction.VERIFY_EMAIL);
        verify(codeRepository).save(any(VerificationCode.class));
        verify(notifier).sendActionCode(eq(EMAIL), eq(VerificationAction.VERIFY_EMAIL), anyString());
    }

    // EV-U-10
    @Test
    @DisplayName("EV-U-10: resend for already-verified user → no-op, no dispatch")
    void resendVerified() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedUser));

        service.resend(EMAIL);

        verify(codeRepository, never()).save(any());
        verify(notifier, never()).sendActionCode(anyString(), any(), anyString());
    }

    // EV-U-11
    @Test
    @DisplayName("EV-U-11: resend for unknown email → no-op, no dispatch")
    void resendUnknownEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service.resend(EMAIL);

        verify(codeRepository, never()).save(any());
        verify(notifier, never()).sendActionCode(anyString(), any(), anyString());
    }

    // EV-U-12
    @Test
    @DisplayName("EV-U-12: resend with malformed email → ValidationException")
    void resendMalformedEmail() {
        assertThatThrownBy(() -> service.resend("not-an-email"))
                .isInstanceOf(ValidationException.class);
    }

    // EV-U-14 (throttle boundary): verify throws TooManyRequestsException after exhausting attempts
    @Test
    @DisplayName("EV-U-14 (throttle): verify throws TooManyRequestsException after max wrong attempts")
    void verifyThrowsTooManyAfterMaxAttempts() throws Exception {
        setField("verifyMaxAttempts", 2);

        VerificationCode activeCode = VerificationCode.builder()
                .id(UUID.randomUUID()).userId(USER_ID).action(VerificationAction.VERIFY_EMAIL)
                .codeHash(HASHED).expiresAt(OffsetDateTime.now().plusMinutes(10)).build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser));
        when(codeRepository.findActiveByUserAndAction(USER_ID, VerificationAction.VERIFY_EMAIL))
                .thenReturn(Optional.of(activeCode));
        when(passwordHasher.matches("wrong", HASHED)).thenReturn(false);

        // First two wrong attempts consume the allowance.
        assertThatThrownBy(() -> service.verify(EMAIL, "wrong")).isInstanceOf(InvalidVerificationException.class);
        assertThatThrownBy(() -> service.verify(EMAIL, "wrong")).isInstanceOf(InvalidVerificationException.class);
        // Third attempt must hit the throttle.
        assertThatThrownBy(() -> service.verify(EMAIL, "wrong")).isInstanceOf(TooManyRequestsException.class);
    }

    // Helper: set a @ConfigProperty field on the service via reflection (CDI is absent in unit tests).
    private void setField(String name, Object value) throws Exception {
        Field f = EmailVerificationService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }
}
