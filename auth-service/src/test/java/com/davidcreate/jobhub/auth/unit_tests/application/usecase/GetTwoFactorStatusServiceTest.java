package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.GetTwoFactorStatusService;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
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

/**
 * Unit tests for {@link GetTwoFactorStatusService} (ADR 0019, story #384/#388).
 * Covers TC-384-A1..A3.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetTwoFactorStatusService Unit Tests (TC-384-A1..A3)")
class GetTwoFactorStatusServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks GetTwoFactorStatusService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("TC-384-A1: user with 2FA enabled -> twoFactorEnabled true")
    void enabledUserReturnsTrue() {
        User user = User.builder()
                .id(userId).email("alice@example.com").firstName("Alice").lastName("M")
                .passwordHash("hash").twoFactorEnabled(true).build();
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));

        assertThat(service.getStatus(userId)).isTrue();
    }

    @Test
    @DisplayName("TC-384-A2: user with no 2FA -> twoFactorEnabled false")
    void disabledUserReturnsFalse() {
        User user = User.builder()
                .id(userId).email("bob@example.com").firstName("Bob").lastName("M")
                .passwordHash("hash").twoFactorEnabled(false).build();
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(user));

        assertThat(service.getStatus(userId)).isFalse();
    }

    @Test
    @DisplayName("TC-384-A3: no user exists -> UserNotFoundException")
    void unknownUserThrows() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(userId))
                .isInstanceOf(UserNotFoundException.class);
    }
}
