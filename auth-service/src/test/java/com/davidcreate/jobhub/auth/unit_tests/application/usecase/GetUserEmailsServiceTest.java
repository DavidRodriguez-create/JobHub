package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.UserEmailResult;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.GetUserEmailsService;
import com.davidcreate.jobhub.auth.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetUserEmailsService Unit Tests")
class GetUserEmailsServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    GetUserEmailsService service;

    @Test
    @DisplayName("returns email entries only for verified users, silently omitting unverified")
    void returnsOnlyVerifiedUsers() {
        UUID verifiedId = UUID.randomUUID();
        UUID unverifiedId = UUID.randomUUID();

        User verified = User.builder()
                .id(verifiedId).email("verified@example.com")
                .emailVerified(true).emailVerifiedAt(OffsetDateTime.now())
                .build();
        User unverified = User.builder()
                .id(unverifiedId).email("unverified@example.com")
                .emailVerified(false)
                .build();

        when(userRepository.findByIds(anyCollection())).thenReturn(List.of(verified, unverified));

        List<UserEmailResult> result = service.getEmails(List.of(verifiedId, unverifiedId));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(verifiedId);
        assertThat(result.get(0).email()).isEqualTo("verified@example.com");
    }

    @Test
    @DisplayName("silently omits user IDs that do not exist")
    void omitsNonExistentUsers() {
        UUID existingId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();

        User existing = User.builder()
                .id(existingId).email("existing@example.com")
                .emailVerified(true).emailVerifiedAt(OffsetDateTime.now())
                .build();

        when(userRepository.findByIds(anyCollection())).thenReturn(List.of(existing));

        List<UserEmailResult> result = service.getEmails(List.of(existingId, nonExistentId));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(existingId);
    }

    @Test
    @DisplayName("returns empty list when only unverified/non-existent users requested")
    void returnsEmptyListWhenNoneEligible() {
        when(userRepository.findByIds(anyCollection())).thenReturn(List.of());

        List<UserEmailResult> result = service.getEmails(List.of(UUID.randomUUID()));

        assertThat(result).isEmpty();
    }
}
