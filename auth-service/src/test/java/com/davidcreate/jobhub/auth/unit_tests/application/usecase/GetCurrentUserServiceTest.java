package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.GetCurrentUserService;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCurrentUserService Unit Tests")
class GetCurrentUserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks GetCurrentUserService service;

    @Test
    @DisplayName("returns user when found")
    void returnsUser() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).email("a@b.com").build();
        when(userRepository.findUserById(id)).thenReturn(Optional.of(user));

        assertThat(service.get(id)).isEqualTo(user);
    }

    @Test
    @DisplayName("throws UserNotFound when missing")
    void throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findUserById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(UserNotFoundException.class);
    }
}
