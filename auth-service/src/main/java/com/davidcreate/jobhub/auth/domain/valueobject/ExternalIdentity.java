package com.davidcreate.jobhub.auth.domain.valueobject;

import lombok.Builder;
import lombok.Getter;

/**
 * The provider identity resolved from an OAuth token exchange + userinfo lookup
 * (ADR 0027). {@code emailVerified} reflects the PROVIDER's own verified claim
 * (Google's {@code email_verified}, GitHub's {@code /user/emails} primary+verified
 * entry) - never JobHub's own account {@code email_verified}, which this identity
 * may or may not confirm (BR2/BR3).
 *
 * <p>{@code firstName}/{@code lastName} are the provider's OWN first/last-name
 * fields when it has them (Google's {@code given_name}/{@code family_name}); GitHub
 * has none, so they are null there. {@code fullName}/{@code username} (ADR 0028,
 * Decision 3) are the raw signals adapters report without deciding anything:
 * {@code fullName} is the provider's full display name (Google's/GitHub's
 * {@code name}), {@code username} is the provider's handle (GitHub's
 * {@code login}; Google has none). {@link com.davidcreate.jobhub.auth.domain.service.ProviderDisplayName}
 * is the single place that turns these raw signals into a non-null display name.
 */
@Getter
@Builder
public class ExternalIdentity {

    private final String provider;
    private final String providerUserId;
    private final String email;
    private final boolean emailVerified;
    private final String firstName;
    private final String lastName;
    private final String fullName;
    private final String username;

    public boolean hasUsableEmail() {
        return email != null && !email.isBlank();
    }
}
