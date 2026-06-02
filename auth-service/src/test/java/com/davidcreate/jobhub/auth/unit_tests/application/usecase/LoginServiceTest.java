package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.LoginCommand;
import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.TokenGenerator;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.LoginService;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidCredentialsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginService Unit Tests")
class LoginServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordHasher passwordHasher;
    @Mock TokenGenerator tokenGenerator;
    @InjectMocks LoginService service;

    private final User alice = User.builder()
            .id(UUID.randomUUID())
            .email("alice@example.com")
            .passwordHash("hashed")
            .firstName("Alice").lastName("M").build();

    @Test
    @DisplayName("returns token + user on valid credentials")
    void loginsValid() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordHasher.matches("test1234", "hashed")).thenReturn(true);
        when(tokenGenerator.generate(alice)).thenReturn("jwt-token");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        LoginResult result = service.login(new LoginCommand("alice@example.com", "test1234"));

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.expiresInSeconds()).isEqualTo(3600L);
        assertThat(result.user()).isEqualTo(alice);
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
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordHasher.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginCommand("alice@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("throws InvalidCredentials when email is null")
    void throwsOnNullEmail() {
        assertThatThrownBy(() -> service.login(new LoginCommand(null, "test1234")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
