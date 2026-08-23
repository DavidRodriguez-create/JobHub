package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.LoginCommand;
import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.TokenGenerator;
import com.davidcreate.jobhub.auth.application.port.out.TwoFactorChallengeRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.LoginService;
import com.davidcreate.jobhub.auth.domain.entity.TwoFactorChallenge;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.EmailNotVerifiedException;
import com.davidcreate.jobhub.auth.domain.exception.InvalidCredentialsException;
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

/**
 * EV-U-08: LoginService checks email-verified AFTER password match.
 * Bad creds → InvalidCredentialsException (401), correct creds + unverified → EmailNotVerifiedException (403).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginService Unit Tests — EV-U-08 + existing cases")
class LoginServiceTest {

    private static final String EMAIL = "alice@example.com";
    private static final String HASH = "hashed";

    @Mock UserRepository userRepository;
    @Mock PasswordHasher passwordHasher;
    @Mock TokenGenerator tokenGenerator;
    @Mock TwoFactorChallengeRepository challengeRepository;
    @InjectMocks LoginService service;

    private User user(boolean verified) {
        return user(verified, false);
    }

    private User user(boolean verified, boolean twoFactorEnabled) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .passwordHash(HASH)
                .firstName("Alice").lastName("M")
                .emailVerified(verified)
                .twoFactorEnabled(twoFactorEnabled)
                .build();
    }

    @Test
    @DisplayName("returns token + user on valid credentials + verified email")
    void loginsValid() {
        User verified = user(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verified));
        when(passwordHasher.matches("test1234", HASH)).thenReturn(true);
        when(tokenGenerator.generate(verified)).thenReturn("jwt-token");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        LoginResult result = service.login(new LoginCommand(EMAIL, "test1234"));

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.expiresInSeconds()).isEqualTo(3600L);
        assertThat(result.user()).isEqualTo(verified);
    }

    // TC-LOGIN-U01: non-2FA login returns token directly (no regression, 2FA-LOGIN-6)
    @Test
    @DisplayName("TC-LOGIN-U01: non-2FA user login returns token directly, twoFactorRequired is false")
    void non2faLoginReturnsTokenDirectly() {
        User verified = user(true, false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verified));
        when(passwordHasher.matches("test1234", HASH)).thenReturn(true);
        when(tokenGenerator.generate(verified)).thenReturn("jwt-token");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        LoginResult result = service.login(new LoginCommand(EMAIL, "test1234"));

        assertThat(result.isTwoFactorRequired()).isFalse();
        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.user()).isEqualTo(verified);
        verify(challengeRepository, never()).save(any());
    }

    // TC-LOGIN-U02: 2FA user login returns challenge token, no JWT (2FA-LOGIN-1)
    @Test
    @DisplayName("TC-LOGIN-U02: 2FA user login returns challenge token, token is null")
    void twoFactorUserLoginReturnsChallenge() {
        User verified = user(true, true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(verified));
        when(passwordHasher.matches("test1234", HASH)).thenReturn(true);
        when(challengeRepository.save(any(TwoFactorChallenge.class))).thenAnswer(inv -> {
            TwoFactorChallenge c = inv.getArgument(0);
            return c.getId() != null ? c : c.toBuilder().id(UUID.randomUUID()).build();
        });

        LoginResult result = service.login(new LoginCommand(EMAIL, "test1234"));

        assertThat(result.isTwoFactorRequired()).isTrue();
        assertThat(result.twoFactorToken()).isNotBlank();
        assertThat(result.token()).isNull();
        assertThat(result.user()).isNull();
        verify(tokenGenerator, never()).generate(any());

        ArgumentCaptor<TwoFactorChallenge> captor = ArgumentCaptor.forClass(TwoFactorChallenge.class);
        verify(challengeRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(verified.getId());
    }

    // TC-LOGIN-U03: wrong password returns 401 regardless of 2FA status (2FA-LOGIN-7)
    @Test
    @DisplayName("TC-LOGIN-U03: wrong password on a 2FA-enabled account still throws InvalidCredentials")
    void wrongPasswordOnTwoFactorAccountThrowsInvalidCredentials() {
        User twoFactorUser = user(true, true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(twoFactorUser));
        when(passwordHasher.matches("wrong", HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginCommand(EMAIL, "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(challengeRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws InvalidCredentials on unknown email")
    void throwsOnUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginCommand("nobody@example.com", "test1234")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("throws InvalidCredentials on wrong password")
    void throwsOnWrongPassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(false)));
        when(passwordHasher.matches("wrong", HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginCommand(EMAIL, "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("throws InvalidCredentials when email is null")
    void throwsOnNullEmail() {
        assertThatThrownBy(() -> service.login(new LoginCommand(null, "test1234")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // EV-U-08b
    @Test
    @DisplayName("EV-U-08: correct password but unverified email → EmailNotVerifiedException (403 path)")
    void correctPasswordUnverifiedThrows403Exception() {
        User unverified = user(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverified));
        when(passwordHasher.matches("test1234", HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginCommand(EMAIL, "test1234")))
                .isInstanceOf(EmailNotVerifiedException.class);
        verify(tokenGenerator, never()).generate(unverified);
    }

    // EV-U-08a: bad creds always 401, never leaks verified state
    @Test
    @DisplayName("EV-U-08: wrong password on unverified account → InvalidCredentialsException (401 not 403)")
    void wrongPasswordNeverLeaksVerifiedState() {
        User unverified = user(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverified));
        when(passwordHasher.matches("wrong", HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginCommand(EMAIL, "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // TC-459-N1 (ADR 0027 follow-up): a social-only account has passwordHash == null.
    // Guard it before the BCrypt matcher, same 401 as a wrong password (no enumeration, no NPE).
    @Test
    @DisplayName("TC-459-N1: social-only account (null password hash) → InvalidCredentialsException, no NPE")
    void nullPasswordHashThrowsInvalidCredentialsNotNpe() {
        User socialOnly = user(true).toBuilder().passwordHash(null).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(socialOnly));

        assertThatThrownBy(() -> service.login(new LoginCommand(EMAIL, "anyPassword1")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordHasher, never()).matches(any(), any());
        verify(tokenGenerator, never()).generate(any());
    }
}
