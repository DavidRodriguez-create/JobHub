package com.davidcreate.jobhub.auth.adapter.out.client.github;

import com.davidcreate.jobhub.auth.domain.valueobject.ExternalIdentity;
import com.davidcreate.jobhub.github.contract.model.GithubEmailResponse;
import com.davidcreate.jobhub.github.contract.model.GithubUserResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Pure mapping, no I/O (BR3). Email/verified come from the {@code primary}
 * entry in {@code /user/emails}: {@code verified} reflects whether that same
 * entry is ALSO {@code verified}; entries that are verified but not primary, or
 * primary but not verified, never independently satisfy the "verified" outcome
 * (only a primary+verified entry does). Reports raw signals only (ADR 0028,
 * Decision 3): GitHub has no first/last-name fields, so those stay null; its
 * public display name maps to {@code fullName} and its handle to {@code username}.
 * The adapter does not decide a display name or split anything itself -
 * {@link com.davidcreate.jobhub.auth.domain.service.ProviderDisplayName} owns
 * the fallback chain (DN-BR1) for both providers.
 */
@ApplicationScoped
public class GithubIdentityMapper {

    public ExternalIdentity toExternalIdentity(GithubUserResponse user, List<GithubEmailResponse> emails) {
        String email = null;
        boolean emailVerified = false;

        GithubEmailResponse primaryAndVerified = findMatch(emails, e -> isPrimary(e) && isVerified(e));
        if (primaryAndVerified != null) {
            email = primaryAndVerified.getEmail();
            emailVerified = true;
        } else {
            GithubEmailResponse primaryOnly = findMatch(emails, GithubIdentityMapper::isPrimary);
            if (primaryOnly != null) {
                email = primaryOnly.getEmail();
            }
        }

        return ExternalIdentity.builder()
                .provider("github")
                .providerUserId(String.valueOf(user.getId()))
                .email(email)
                .emailVerified(emailVerified)
                .fullName(user.getName())
                .username(user.getLogin())
                .build();
    }

    private static GithubEmailResponse findMatch(List<GithubEmailResponse> emails,
                                                  java.util.function.Predicate<GithubEmailResponse> predicate) {
        if (emails == null) {
            return null;
        }
        return emails.stream().filter(predicate).findFirst().orElse(null);
    }

    private static boolean isPrimary(GithubEmailResponse e) {
        return Boolean.TRUE.equals(e.getPrimary());
    }

    private static boolean isVerified(GithubEmailResponse e) {
        return Boolean.TRUE.equals(e.getVerified());
    }
}
