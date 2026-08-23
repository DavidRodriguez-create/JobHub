package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.RegisterUserCommand;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.EmailVerificationService;
import com.davidcreate.jobhub.auth.application.usecase.RegisterUserService;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.EmailAlreadyRegisteredException;
import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
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
@DisplayName("RegisterUserService Unit Tests")
class RegisterUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordHasher passwordHasher;
    @Mock EmailVerificationService emailVerificationService;
    @InjectMocks RegisterUserService service;

    @Test
    @DisplayName("hashes password, saves user, and dispatches verify-email code when email is new")
    void registersNewUser() {
        var cmd = new RegisterUserCommand("Alice", "Martin", "Alice@Example.com", "test1234");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordHasher.hash("test1234")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return u.toBuilder().id(UUID.randomUUID()).build();
        });

        User saved = service.register(cmd);

        assertThat(saved.getId()).isNotNull();
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(captor.getValue().isEmailVerified()).isFalse();
        verify(emailVerificationService).sendFor(any(User.class));
    }

    @Test
    @DisplayName("throws EmailAlreadyRegistered when email exists")
    void rejectsDuplicateEmail() {
        var cmd = new RegisterUserCommand("Alice", "Martin", "alice@example.com", "test1234");
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(User.builder().email("alice@example.com").build()));

        assertThatThrownBy(() -> service.register(cmd))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws ValidationException when firstName blank")
    void rejectsBlankFirstName() {
        var cmd = new RegisterUserCommand("  ", "Martin", "alice@example.com", "test1234");
        assertThatThrownBy(() -> service.register(cmd)).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("throws ValidationException when password too short")
    void rejectsShortPassword() {
        var cmd = new RegisterUserCommand("Alice", "Martin", "alice@example.com", "short");
        assertThatThrownBy(() -> service.register(cmd)).isInstanceOf(ValidationException.class);
    }
}
