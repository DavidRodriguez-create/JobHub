package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.ChangePasswordCommand;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.ChangePasswordService;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidCredentialsException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
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
@DisplayName("ChangePasswordService Unit Tests")
class ChangePasswordServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordHasher passwordHasher;
    @InjectMocks ChangePasswordService service;

    private final UUID id = UUID.randomUUID();
    private final User existing = User.builder()
            .id(id).email("a@b.com").firstName("Alice").lastName("M")
            .passwordHash("oldHash").build();

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
}
