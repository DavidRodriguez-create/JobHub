package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.RequestVerificationUseCase.VerificationResult;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.application.usecase.AccountVerificationService;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.entity.VerificationCode;
import com.davidcreate.jobhub.auth.domain.exception.InvalidVerificationException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountVerificationService Unit Tests")
class AccountVerificationServiceTest {

    @Mock VerificationCodeRepository codeRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordHasher passwordHasher;
    @Mock VerificationNotifier notifier;
    @InjectMocks AccountVerificationService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("request hashes a code, persists it, and emails the user")
    void requestHappy() {
        UUID codeId = UUID.randomUUID();
        when(userRepository.findUserById(userId))
                .thenReturn(Optional.of(User.builder().id(userId).email("a@b.com").build()));
        when(passwordHasher.hash(anyString())).thenReturn("code-hash");
        when(codeRepository.save(any(VerificationCode.class))).thenAnswer(inv -> {
            VerificationCode c = inv.getArgument(0);
            return c.toBuilder().id(codeId).build();
        });

        VerificationResult result = service.request(userId, VerificationAction.DELETE_ACCOUNT);

        assertThat(result.verificationId()).isEqualTo(codeId);
        ArgumentCaptor<VerificationCode> cap = ArgumentCaptor.forClass(VerificationCode.class);
        verify(codeRepository).save(cap.capture());
        assertThat(cap.getValue().getCodeHash()).isEqualTo("code-hash");
        assertThat(cap.getValue().getAction()).isEqualTo(VerificationAction.DELETE_ACCOUNT);
        verify(notifier).sendActionCode(eq("a@b.com"), eq(VerificationAction.DELETE_ACCOUNT), anyString());
    }

    @Test
    @DisplayName("request throws when the user does not exist")
    void requestMissingUser() {
        when(userRepository.findUserById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request(userId, VerificationAction.DELETE_ACCOUNT))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("delete consumes a valid code and removes the user")
    void deleteHappy() {
        UUID codeId = UUID.randomUUID();
        var stored = VerificationCode.builder()
                .id(codeId).userId(userId).action(VerificationAction.DELETE_ACCOUNT)
                .codeHash("hash").expiresAt(OffsetDateTime.now().plusMinutes(10)).build();
        when(codeRepository.findOneById(codeId)).thenReturn(Optional.of(stored));
        when(passwordHasher.matches("123456", "hash")).thenReturn(true);
        when(codeRepository.save(any(VerificationCode.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(userId, codeId, "123456");

        verify(userRepository).removeById(userId);
        ArgumentCaptor<VerificationCode> cap = ArgumentCaptor.forClass(VerificationCode.class);
        verify(codeRepository).save(cap.capture());
        assertThat(cap.getValue().getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("delete rejects a wrong code")
    void deleteWrongCode() {
        UUID codeId = UUID.randomUUID();
        var stored = VerificationCode.builder()
                .id(codeId).userId(userId).action(VerificationAction.DELETE_ACCOUNT)
                .codeHash("hash").expiresAt(OffsetDateTime.now().plusMinutes(10)).build();
        when(codeRepository.findOneById(codeId)).thenReturn(Optional.of(stored));
        when(passwordHasher.matches("000000", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.delete(userId, codeId, "000000"))
                .isInstanceOf(InvalidVerificationException.class);
        verify(userRepository, never()).removeById(any());
    }

    @Test
    @DisplayName("delete rejects a code that belongs to another user")
    void deleteWrongOwner() {
        UUID codeId = UUID.randomUUID();
        var stored = VerificationCode.builder()
                .id(codeId).userId(UUID.randomUUID()).action(VerificationAction.DELETE_ACCOUNT)
                .codeHash("hash").expiresAt(OffsetDateTime.now().plusMinutes(10)).build();
        when(codeRepository.findOneById(codeId)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.delete(userId, codeId, "123456"))
                .isInstanceOf(InvalidVerificationException.class);
        verify(userRepository, never()).removeById(any());
    }

    @Test
    @DisplayName("delete rejects an expired code")
    void deleteExpired() {
        UUID codeId = UUID.randomUUID();
        var stored = VerificationCode.builder()
                .id(codeId).userId(userId).action(VerificationAction.DELETE_ACCOUNT)
                .codeHash("hash").expiresAt(OffsetDateTime.now().minusMinutes(1)).build();
        when(codeRepository.findOneById(codeId)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.delete(userId, codeId, "123456"))
                .isInstanceOf(InvalidVerificationException.class);
        verify(userRepository, never()).removeById(any());
    }

    @Test
    @DisplayName("delete rejects an unknown verificationId")
    void deleteUnknown() {
        UUID codeId = UUID.randomUUID();
        when(codeRepository.findOneById(codeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, codeId, "123456"))
                .isInstanceOf(InvalidVerificationException.class);
        verify(userRepository, never()).removeById(any());
    }

    @Test
    @DisplayName("consume marks a valid code used without deleting the user")
    void consumeHappy() {
        UUID codeId = UUID.randomUUID();
        var stored = VerificationCode.builder()
                .id(codeId).userId(userId).action(VerificationAction.DELETE_ALL_APPLICATIONS)
                .codeHash("hash").expiresAt(OffsetDateTime.now().plusMinutes(10)).build();
        when(codeRepository.findOneById(codeId)).thenReturn(Optional.of(stored));
        when(passwordHasher.matches("123456", "hash")).thenReturn(true);
        when(codeRepository.save(any(VerificationCode.class))).thenAnswer(inv -> inv.getArgument(0));

        service.consume(userId, codeId, "123456", VerificationAction.DELETE_ALL_APPLICATIONS);

        ArgumentCaptor<VerificationCode> cap = ArgumentCaptor.forClass(VerificationCode.class);
        verify(codeRepository).save(cap.capture());
        assertThat(cap.getValue().getConsumedAt()).isNotNull();
        verify(userRepository, never()).removeById(any());
    }

    @Test
    @DisplayName("consume rejects a code issued for a different action")
    void consumeActionMismatch() {
        UUID codeId = UUID.randomUUID();
        var stored = VerificationCode.builder()
                .id(codeId).userId(userId).action(VerificationAction.DELETE_ACCOUNT)
                .codeHash("hash").expiresAt(OffsetDateTime.now().plusMinutes(10)).build();
        when(codeRepository.findOneById(codeId)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.consume(userId, codeId, "123456", VerificationAction.DELETE_ALL_APPLICATIONS))
                .isInstanceOf(InvalidVerificationException.class);
        verify(codeRepository, never()).save(any());
    }
}
