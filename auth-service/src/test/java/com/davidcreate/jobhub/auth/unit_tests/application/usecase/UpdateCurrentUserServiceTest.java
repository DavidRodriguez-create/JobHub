package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.UpdateCurrentUserCommand;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.UpdateCurrentUserService;
import com.davidcreate.jobhub.auth.domain.entity.User;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateCurrentUserService Unit Tests")
class UpdateCurrentUserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UpdateCurrentUserService service;

    private final UUID id = UUID.randomUUID();
    private final User existing = User.builder()
            .id(id).email("a@b.com").firstName("Alice").lastName("M")
            .passwordHash("oldHash").build();

    @Test
    @DisplayName("updates firstName and lastName, leaves password untouched")
    void updatesNames() {
        when(userRepository.findUserById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, new UpdateCurrentUserCommand("Alicia", "Marin"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFirstName()).isEqualTo("Alicia");
        assertThat(captor.getValue().getLastName()).isEqualTo("Marin");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("oldHash");
    }

    @Test
    @DisplayName("keeps prior values when fields are null")
    void leavesFieldsAlone() {
        when(userRepository.findUserById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, new UpdateCurrentUserCommand(null, null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFirstName()).isEqualTo("Alice");
        assertThat(captor.getValue().getLastName()).isEqualTo("M");
    }

    @Test
    @DisplayName("throws UserNotFound when target missing")
    void throwsWhenMissing() {
        when(userRepository.findUserById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UpdateCurrentUserCommand("a", null)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
