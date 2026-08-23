package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.application.port.in.OAuthCallbackCommand;
import com.davidcreate.jobhub.auth.application.port.out.OAuthProviderClient;
import com.davidcreate.jobhub.auth.application.port.out.TokenGenerator;
import com.davidcreate.jobhub.auth.application.port.out.TwoFactorChallengeRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserIdentityRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.usecase.CompleteOAuthLoginService;
import com.davidcreate.jobhub.auth.domain.entity.TwoFactorChallenge;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.entity.UserIdentity;
import com.davidcreate.jobhub.auth.domain.exception.OAuthStateMismatchException;
import com.davidcreate.jobhub.auth.domain.exception.ProviderEmailUnavailableException;
import com.davidcreate.jobhub.auth.domain.exception.ProviderNotConfiguredException;
import com.davidcreate.jobhub.auth.domain.exception.UnverifiedProviderEmailException;
import com.davidcreate.jobhub.auth.domain.valueobject.ExternalIdentity;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompleteOAuthLoginService Unit Tests")
class CompleteOAuthLoginServiceTest {

    @Mock Instance<OAuthProviderClient> clients;
    @Mock OAuthProviderClient googleClient;
    @Mock UserIdentityRepository userIdentityRepository;
    @Mock UserRepository userRepository;
    @Mock TokenGenerator tokenGenerator;
    @Mock TwoFactorChallengeRepository challengeRepository;

    private CompleteOAuthLoginService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CompleteOAuthLoginService(
                clients, userIdentityRepository, userRepository, tokenGenerator, challengeRepository);
        Field f = CompleteOAuthLoginService.class.getDeclaredField("challengeTtlMinutes");
        f.setAccessible(true);
        f.set(service, 5L);
    }

    private void withGoogleClientAvailable() {
        lenient().when(clients.stream()).thenAnswer(inv -> Stream.of(googleClient));
        lenient().when(googleClient.supports("google")).thenReturn(true);
        lenient().when(googleClient.isConfigured()).thenReturn(true);
    }

    private OAuthCallbackCommand command(String submittedState, String cookieState) {
        return new OAuthCallbackCommand("google", "auth-code", submittedState, cookieState);
    }

    private ExternalIdentity identity(String email, boolean verified) {
        return ExternalIdentity.builder()
                .provider("google")
                .providerUserId("google-sub-1")
                .email(email)
                .emailVerified(verified)
                .firstName("Ada")
                .lastName("Lovelace")
                .build();
    }

    private User user(UUID id, String email, boolean twoFactorEnabled) {
        return User.builder()
                .id(id)
                .email(email)
                .firstName("Ada")
                .lastName("Lovelace")
                .passwordHash("hash")
                .emailVerified(true)
                .twoFactorEnabled(twoFactorEnabled)
                .build();
    }

    // TC-459-A5: OAUTH-ERR-3/BR10 (fail-fast, load-bearing). State mismatch never
    // touches the provider client.
    @Test
    @DisplayName("TC-459-A5: state mismatch throws before any provider client interaction")
    void stateMismatchFailsFastWithZeroProviderInteractions() {
        assertThatThrownBy(() -> service.handle(command("state-from-provider", "different-cookie-state")))
                .isInstanceOf(OAuthStateMismatchException.class);
        verifyNoInteractions(googleClient);
        verifyNoInteractions(userIdentityRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("TC-459-A5: missing cookie (null bound state) throws OAuthStateMismatchException")
    void missingCookieStateFailsFast() {
        assertThatThrownBy(() -> service.handle(command("state-from-provider", null)))
                .isInstanceOf(OAuthStateMismatchException.class);
        verifyNoInteractions(googleClient);
    }

    // TC-459-A6: OAUTH-RETURN-GOOGLE-1/GITHUB-1 (BR1 step 1, existing link).
    @Test
    @DisplayName("TC-459-A6: existing identity link resolves the same account, no identity re-save")
    void existingLinkResolvesSameAccount() {
        withGoogleClientAvailable();
        UUID userId = UUID.randomUUID();
        when(googleClient.exchange("auth-code")).thenReturn(identity("jane@example.com", true));
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1"))
                .thenReturn(Optional.of(UserIdentity.builder().userId(userId).provider("google").build()));
        User existing = user(userId, "jane@example.com", false);
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(existing));
        when(tokenGenerator.generate(existing)).thenReturn("jwt");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        LoginResult result = service.handle(command("s", "s"));

        assertThat(result.user().getId()).isEqualTo(userId);
        assertThat(result.token()).isEqualTo("jwt");
        verify(userIdentityRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
    }

    // TC-459-A7: OAUTH-RETURN-3/BR5. Changed provider email since linking does not resync.
    @Test
    @DisplayName("TC-459-A7: existing-link path never resyncs the account's stored email")
    void existingLinkNeverResyncsEmail() {
        withGoogleClientAvailable();
        UUID userId = UUID.randomUUID();
        when(googleClient.exchange("auth-code")).thenReturn(identity("new-email-at-provider@example.com", true));
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1"))
                .thenReturn(Optional.of(UserIdentity.builder().userId(userId).provider("google").build()));
        User existing = user(userId, "original@example.com", false);
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(existing));
        when(tokenGenerator.generate(existing)).thenReturn("jwt");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        LoginResult result = service.handle(command("s", "s"));

        assertThat(result.user().getEmail()).isEqualTo("original@example.com");
        verify(userRepository, never()).save(any());
    }

    // TC-459-A8: OAUTH-LINK-GOOGLE-1/GITHUB-1 (BR1 step 2, auto-link).
    @Test
    @DisplayName("TC-459-A8: verified email match auto-links a new identity and marks account verified")
    void verifiedEmailAutoLinksToExistingAccount() {
        withGoogleClientAvailable();
        UUID userId = UUID.randomUUID();
        when(googleClient.exchange("auth-code")).thenReturn(identity("jane@example.com", true));
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1")).thenReturn(Optional.empty());
        User existing = user(userId, "jane@example.com", false).toBuilder().emailVerified(false).build();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenGenerator.generate(any())).thenReturn("jwt");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        LoginResult result = service.handle(command("s", "s"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isEmailVerified()).isTrue();

        ArgumentCaptor<UserIdentity> identityCaptor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo("google");

        assertThat(result.user().getId()).isEqualTo(userId);
    }

    // TC-459-A9: OAUTH-LINK-3. Cites A6's assertions: once linked, resolution never
    // re-enters step 2 (covered by existingLinkResolvesSameAccount above).
    @Test
    @DisplayName("TC-459-A9: subsequent login after auto-link resolves via existing-link, not auto-link again")
    void subsequentLoginUsesExistingLinkNotAutoLinkAgain() {
        existingLinkResolvesSameAccount();
    }

    // TC-459-A10: OAUTH-LINK-4/BR4. A second provider auto-links to the same account.
    @Test
    @DisplayName("TC-459-A10: a second provider auto-links to the same already-linked account")
    void secondProviderAutoLinksToSameAccount() {
        withGoogleClientAvailable();
        UUID userId = UUID.randomUUID();
        when(googleClient.exchange("auth-code")).thenReturn(identity("jane@example.com", true));
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1")).thenReturn(Optional.empty());
        User existing = user(userId, "jane@example.com", false);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenGenerator.generate(any())).thenReturn("jwt");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        service.handle(command("s", "s"));

        ArgumentCaptor<UserIdentity> identityCaptor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo("google");
    }

    // TC-459-A11: OAUTH-NEW-GOOGLE-1/GITHUB-1 (BR1 step 3, JIT create, verified).
    @Test
    @DisplayName("TC-459-A11: no identity/account match, verified email -> JIT-creates a password-less account")
    void jitCreatesPasswordLessAccountOnVerifiedEmail() {
        withGoogleClientAvailable();
        when(googleClient.exchange("auth-code")).thenReturn(identity("new.user@example.com", true));
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new.user@example.com")).thenReturn(Optional.empty());
        UUID newId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return u.toBuilder().id(newId).build();
        });
        when(tokenGenerator.generate(any())).thenReturn("jwt");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        LoginResult result = service.handle(command("s", "s"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isNull();
        assertThat(userCaptor.getValue().isEmailVerified()).isTrue();
        assertThat(result.isTwoFactorRequired()).isFalse();
        assertThat(result.user().getId()).isEqualTo(newId);
    }

    // TC-459-A12: OAUTH-NEW-GOOGLE-2/GITHUB-2/BR9 (JIT create, unverified, no collision).
    @Test
    @DisplayName("TC-459-A12: unverified email + no collision still JIT-creates and logs in (BR9)")
    void jitCreatesAccountEvenWhenEmailUnverified() {
        withGoogleClientAvailable();
        when(googleClient.exchange("auth-code")).thenReturn(identity("new.unverified@example.com", false));
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new.unverified@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> ((User) inv.getArgument(0)).toBuilder().id(UUID.randomUUID()).build());
        when(tokenGenerator.generate(any())).thenReturn("jwt");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        LoginResult result = service.handle(command("s", "s"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isEmailVerified()).isFalse();
        assertThat(result.isTwoFactorRequired()).isFalse();
    }

    // TC-459-A13: OAUTH-REFUSE-GOOGLE-1/GITHUB-1 (BR1 step 4, refuse, load-bearing).
    @Test
    @DisplayName("TC-459-A13: unverified email colliding with an existing account is refused, zero writes")
    void unverifiedEmailCollisionRefusesWithZeroWrites() {
        withGoogleClientAvailable();
        when(googleClient.exchange("auth-code")).thenReturn(identity("jane@example.com", false));
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1")).thenReturn(Optional.empty());
        User existing = user(UUID.randomUUID(), "jane@example.com", false);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.handle(command("s", "s")))
                .isInstanceOf(UnverifiedProviderEmailException.class);

        verify(userRepository, never()).save(any());
        verify(userIdentityRepository, never()).save(any());
    }

    // TC-459-A14: OAUTH-NEW-GITHUB-3/BR7 (no usable email at all).
    @Test
    @DisplayName("TC-459-A14: no usable email at all throws a 401-family exception, zero writes")
    void noUsableEmailThrowsAndWritesNothing() {
        withGoogleClientAvailable();
        when(googleClient.exchange("auth-code")).thenReturn(identity(null, false));
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(command("s", "s")))
                .isInstanceOf(ProviderEmailUnavailableException.class);

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
        verify(userIdentityRepository, never()).save(any());
    }

    // TC-506-A32: provider-not-configured (fourth zero-write family, load-bearing).
    // findClient() filters on isConfigured() and throws before either repository
    // is touched.
    @Test
    @DisplayName("TC-506-A32: provider not configured throws before any repository interaction")
    void providerNotConfiguredFailsFastWithZeroRepositoryInteractions() {
        when(clients.stream()).thenAnswer(inv -> Stream.of(googleClient));
        when(googleClient.supports("google")).thenReturn(true);
        when(googleClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.handle(command("s", "s")))
                .isInstanceOf(ProviderNotConfiguredException.class);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(userIdentityRepository);
    }

    // TC-459-A15: OAUTH-2FA-4/BR6. Cites A11/A12: a freshly-built User naturally has
    // twoFactorEnabled=false, so the JIT path never challenges (already asserted above).
    @Test
    @DisplayName("TC-459-A15: JIT-created account never triggers a 2FA challenge")
    void jitAccountNeverTriggersChallenge() {
        jitCreatesPasswordLessAccountOnVerifiedEmail();
        verify(challengeRepository, never()).save(any());
    }

    // TC-459-A16: OAUTH-2FA-1 (existing-link + 2FA, highest-risk regression surface).
    @Test
    @DisplayName("TC-459-A16: existing-link account with 2FA enabled returns the challenge shape")
    void existingLinkWithTwoFactorReturnsChallenge() {
        withGoogleClientAvailable();
        UUID userId = UUID.randomUUID();
        when(googleClient.exchange("auth-code")).thenReturn(identity("jane@example.com", true));
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1"))
                .thenReturn(Optional.of(UserIdentity.builder().userId(userId).provider("google").build()));
        User twoFactorUser = user(userId, "jane@example.com", true);
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(twoFactorUser));
        when(challengeRepository.save(any(TwoFactorChallenge.class)))
                .thenAnswer(inv -> ((TwoFactorChallenge) inv.getArgument(0)).toBuilder().id(UUID.randomUUID()).build());

        LoginResult result = service.handle(command("s", "s"));

        assertThat(result.isTwoFactorRequired()).isTrue();
        assertThat(result.twoFactorToken()).isNotBlank();
        assertThat(result.token()).isNull();
        assertThat(result.user()).isNull();
        verify(userIdentityRepository, never()).save(any());
        verify(challengeRepository).save(any());
    }

    // TC-459-A17: OAUTH-2FA-2 (auto-link + 2FA, same PDA-flagged surface).
    @Test
    @DisplayName("TC-459-A17: auto-link + 2FA both apply on the same callback")
    void autoLinkWithTwoFactorLinksAndChallenges() {
        withGoogleClientAvailable();
        UUID userId = UUID.randomUUID();
        when(googleClient.exchange("auth-code")).thenReturn(identity("jane@example.com", true));
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1")).thenReturn(Optional.empty());
        User existing = user(userId, "jane@example.com", true).toBuilder().emailVerified(false).build();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(challengeRepository.save(any(TwoFactorChallenge.class)))
                .thenAnswer(inv -> ((TwoFactorChallenge) inv.getArgument(0)).toBuilder().id(UUID.randomUUID()).build());

        LoginResult result = service.handle(command("s", "s"));

        verify(userIdentityRepository).save(any());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isEmailVerified()).isTrue();

        assertThat(result.isTwoFactorRequired()).isTrue();
        assertThat(result.token()).isNull();
    }

    // TC-506-A27: DN-2/DN-3/DN-4 wiring (load-bearing). The JIT branch must call
    // ProviderDisplayName instead of passing raw firstName/lastName straight
    // through - the literal production bug (comment 1 on #506).
    @Test
    @DisplayName("TC-506-A27: JIT provisioning derives the name via ProviderDisplayName, not the raw identity fields")
    void jitProvisioningUsesDerivedNameNotRawIdentityFields() {
        withGoogleClientAvailable();
        ExternalIdentity identity = ExternalIdentity.builder()
                .provider("google")
                .providerUserId("google-sub-1")
                .email("alex.morales@example.com")
                .emailVerified(true)
                .firstName(null)
                .lastName(null)
                .fullName("Alex Morales")
                .username(null)
                .build();
        when(googleClient.exchange("auth-code")).thenReturn(identity);
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alex.morales@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenGenerator.generate(any())).thenReturn("jwt");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        service.handle(command("s", "s"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getFirstName()).isEqualTo("Alex");
        assertThat(userCaptor.getValue().getLastName()).isEqualTo("Morales");
    }

    // TC-506-A28: DN-12 (no silent overwrite, existing-link). Extends TC-459-A6/A9.
    @Test
    @DisplayName("TC-506-A28: existing-link login never re-saves/overwrites the stored name")
    void existingLinkNeverOverwritesStoredName() {
        withGoogleClientAvailable();
        UUID userId = UUID.randomUUID();
        ExternalIdentity identity = ExternalIdentity.builder()
                .provider("google")
                .providerUserId("google-sub-1")
                .email("jane@example.com")
                .emailVerified(true)
                .fullName("A Completely Different Fuller Name")
                .build();
        when(googleClient.exchange("auth-code")).thenReturn(identity);
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1"))
                .thenReturn(Optional.of(UserIdentity.builder().userId(userId).provider("google").build()));
        User existing = user(userId, "jane@example.com", false);
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(existing));
        when(tokenGenerator.generate(existing)).thenReturn("jwt");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        LoginResult result = service.handle(command("s", "s"));

        assertThat(result.user().getFirstName()).isEqualTo("Ada");
        assertThat(result.user().getLastName()).isEqualTo("Lovelace");
        verify(userRepository, never()).save(any());
    }

    // TC-506-A29: DN-13 (no silent overwrite, auto-link). Extends TC-459-A8.
    @Test
    @DisplayName("TC-506-A29: auto-link never overwrites the existing password account's own name")
    void autoLinkNeverOverwritesExistingAccountName() {
        withGoogleClientAvailable();
        UUID userId = UUID.randomUUID();
        ExternalIdentity identity = ExternalIdentity.builder()
                .provider("google")
                .providerUserId("google-sub-1")
                .email("jane@example.com")
                .emailVerified(true)
                .firstName("Provider")
                .lastName("Name")
                .fullName("Provider Name")
                .build();
        when(googleClient.exchange("auth-code")).thenReturn(identity);
        when(userIdentityRepository.findByProviderAndSubject("google", "google-sub-1")).thenReturn(Optional.empty());
        User existing = user(userId, "jane@example.com", false).toBuilder()
                .firstName("Jane").lastName("Doe").emailVerified(false).build();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenGenerator.generate(any())).thenReturn("jwt");
        when(tokenGenerator.lifespanSeconds()).thenReturn(3600L);

        LoginResult result = service.handle(command("s", "s"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getFirstName()).isEqualTo("Jane");
        assertThat(userCaptor.getValue().getLastName()).isEqualTo("Doe");
        assertThat(userCaptor.getValue().isEmailVerified()).isTrue();
        assertThat(result.user().getFirstName()).isEqualTo("Jane");
        assertThat(result.user().getLastName()).isEqualTo("Doe");
    }
}
