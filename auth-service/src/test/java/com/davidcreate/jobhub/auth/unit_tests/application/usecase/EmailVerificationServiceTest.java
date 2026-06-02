package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.out.EmailVerificationTokenRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.application.usecase.EmailVerificationService;
import com.davidcreate.jobhub.auth.domain.entity.EmailVerificationToken;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidVerificationException;
import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
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
@DisplayName("EmailVerificationService Unit Tests")
class EmailVerificationServiceTest {

    @Mock EmailVerificationTokenRepository tokenRepository;
    @Mock UserRepository userRepository;
    @Mock VerificationNotifier notifier;
    @InjectMocks EmailVerificationService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("sendFor persists a token and dispatches the email")
    void sendFor() {
        var user = User.builder().id(userId).email("a@b.com").build();
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendFor(user);

        ArgumentCaptor<EmailVerificationToken> cap = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(userId);
        assertThat(cap.getValue().getToken()).isNotBlank();
        verify(notifier).sendEmailVerification(eq("a@b.com"), anyString());
    }

    @Test
    @DisplayName("verify marks the user verified and consumes the token")
    void verifyHappy() {
        var token = EmailVerificationToken.builder()
                .id(UUID.randomUUID()).userId(userId).token("tok")
                .expiresAt(OffsetDateTime.now().plusHours(1)).build();
        when(tokenRepository.findByToken("tok")).thenReturn(Optional.of(token));
        when(userRepository.findUserById(userId))
                .thenReturn(Optional.of(User.builder().id(userId).emailVerified(false).build()));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.verify("tok");

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        assertThat(userCap.getValue().isEmailVerified()).isTrue();
        assertThat(userCap.getValue().getEmailVerifiedAt()).isNotNull();

        ArgumentCaptor<EmailVerificationToken> tokCap = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokCap.capture());
        assertThat(tokCap.getValue().getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("verify rejects an unknown token")
    void verifyUnknown() {
        when(tokenRepository.findByToken("bogus")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verify("bogus")).isInstanceOf(InvalidVerificationException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("verify rejects an expired token")
    void verifyExpired() {
        var token = EmailVerificationToken.builder()
                .id(UUID.randomUUID()).userId(userId).token("old")
                .expiresAt(OffsetDateTime.now().minusMinutes(1)).build();
        when(tokenRepository.findByToken("old")).thenReturn(Optional.of(token));
        assertThatThrownBy(() -> service.verify("old")).isInstanceOf(InvalidVerificationException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resend sends a fresh email for an unverified user")
    void resendUnverified() {
        when(userRepository.findByEmail("a@b.com"))
                .thenReturn(Optional.of(User.builder().id(userId).email("a@b.com").emailVerified(false).build()));
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resend("a@b.com");

        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(notifier).sendEmailVerification(eq("a@b.com"), anyString());
    }

    @Test
    @DisplayName("resend is a no-op for an already-verified user")
    void resendVerified() {
        when(userRepository.findByEmail("a@b.com"))
                .thenReturn(Optional.of(User.builder().id(userId).email("a@b.com").emailVerified(true).build()));

        service.resend("a@b.com");

        verify(tokenRepository, never()).save(any());
        verify(notifier, never()).sendEmailVerification(anyString(), anyString());
    }

    @Test
    @DisplayName("resend is a no-op for an unknown email")
    void resendUnknown() {
        when(userRepository.findByEmail("nobody@b.com")).thenReturn(Optional.empty());

        service.resend("nobody@b.com");

        verify(notifier, never()).sendEmailVerification(anyString(), anyString());
    }

    @Test
    @DisplayName("resend rejects a malformed email")
    void resendInvalidEmail() {
        assertThatThrownBy(() -> service.resend("not-an-email")).isInstanceOf(ValidationException.class);
    }
}
