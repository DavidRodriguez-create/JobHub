package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.CompleteOAuthLoginUseCase;
import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.application.port.in.OAuthCallbackCommand;
import com.davidcreate.jobhub.auth.application.port.out.OAuthProviderClient;
import com.davidcreate.jobhub.auth.application.port.out.TokenGenerator;
import com.davidcreate.jobhub.auth.application.port.out.TwoFactorChallengeRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserIdentityRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.TwoFactorChallenge;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.entity.UserIdentity;
import com.davidcreate.jobhub.auth.domain.exception.OAuthStateMismatchException;
import com.davidcreate.jobhub.auth.domain.exception.ProviderEmailUnavailableException;
import com.davidcreate.jobhub.auth.domain.exception.ProviderNotConfiguredException;
import com.davidcreate.jobhub.auth.domain.exception.UnverifiedProviderEmailException;
import com.davidcreate.jobhub.auth.domain.service.ProviderDisplayName;
import com.davidcreate.jobhub.auth.domain.valueobject.ExternalIdentity;
import com.davidcreate.jobhub.auth.domain.valueobject.OAuthProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class CompleteOAuthLoginService implements CompleteOAuthLoginUseCase {

    private final Instance<OAuthProviderClient> clients;
    private final UserIdentityRepository userIdentityRepository;
    private final UserRepository userRepository;
    private final TokenGenerator tokenGenerator;
    private final TwoFactorChallengeRepository challengeRepository;

    @ConfigProperty(name = "auth.totp.challenge-ttl-minutes", defaultValue = "5")
    long challengeTtlMinutes;

    @Override
    @Transactional
    public LoginResult handle(OAuthCallbackCommand command) {
        OAuthProvider.fromValue(command.provider())
                .orElseThrow(() -> new ProviderNotConfiguredException(command.provider()));

        if (command.cookieState() == null || !command.cookieState().equals(command.submittedState())) {
            throw new OAuthStateMismatchException();
        }

        OAuthProviderClient client = findClient(command.provider());
        ExternalIdentity identity = client.exchange(command.code());

        User user = resolveUser(identity);

        if (user.isTwoFactorEnabled()) {
            return issueChallenge(user);
        }

        String token = tokenGenerator.generate(user);
        return new LoginResult(token, tokenGenerator.lifespanSeconds(), user);
    }

    /**
     * BR1's fixed resolution order: existing link, then verified-email auto-link,
     * then just-in-time provisioning; an unverified-email collision (or no usable
     * email at all, BR7) refuses before any write.
     */
    private User resolveUser(ExternalIdentity identity) {
        Optional<UserIdentity> existingLink = userIdentityRepository.findByProviderAndSubject(
                identity.getProvider(), identity.getProviderUserId());
        if (existingLink.isPresent()) {
            return userRepository.findUserById(existingLink.get().getUserId())
                    .orElseThrow(() -> new IllegalStateException(
                            "user_identity references a missing user: " + existingLink.get().getUserId()));
        }

        if (!identity.hasUsableEmail()) {
            throw new ProviderEmailUnavailableException();
        }

        String normalizedEmail = identity.getEmail().trim().toLowerCase();
        Optional<User> matchingAccount = userRepository.findByEmail(normalizedEmail);
        if (matchingAccount.isPresent()) {
            if (!identity.isEmailVerified()) {
                throw new UnverifiedProviderEmailException();
            }
            User linkedUser = userRepository.save(matchingAccount.get().toBuilder()
                    .emailVerified(true)
                    .build());
            linkIdentity(identity, linkedUser.getId());
            return linkedUser;
        }

        // DN-BR1/DN-BR2 (ADR 0028, Decision 3): derivation runs ONLY on this
        // just-in-time provisioning branch, never on the existing-link or
        // auto-link branches above, so a provider can never overwrite a name the
        // user has already edited (or that auto-linking deliberately left alone).
        ProviderDisplayName.Name displayName = ProviderDisplayName.derive(
                identity.getFirstName(), identity.getLastName(), identity.getFullName(),
                identity.getUsername(), normalizedEmail);
        User newUser = userRepository.save(User.builder()
                .firstName(displayName.firstName())
                .lastName(displayName.lastName())
                .email(normalizedEmail)
                .passwordHash(null)
                .emailVerified(identity.isEmailVerified())
                .twoFactorEnabled(false)
                .build());
        linkIdentity(identity, newUser.getId());
        return newUser;
    }

    private void linkIdentity(ExternalIdentity identity, UUID userId) {
        userIdentityRepository.save(UserIdentity.builder()
                .userId(userId)
                .provider(identity.getProvider())
                .providerUserId(identity.getProviderUserId())
                .email(identity.getEmail())
                .build());
    }

    private OAuthProviderClient findClient(String provider) {
        return clients.stream()
                .filter(c -> c.supports(provider))
                .filter(OAuthProviderClient::isConfigured)
                .findFirst()
                .orElseThrow(() -> new ProviderNotConfiguredException(provider));
    }

    private LoginResult issueChallenge(User user) {
        TwoFactorChallenge challenge = TwoFactorChallenge.builder()
                .userId(user.getId())
                .tokenHash(hash(user.getId().toString() + OffsetDateTime.now()))
                .expiresAt(OffsetDateTime.now().plusMinutes(challengeTtlMinutes))
                .build();
        TwoFactorChallenge saved = challengeRepository.save(challenge);
        return new LoginResult(null, 0, null, saved.getId().toString());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
