package com.davidcreate.jobhub.auth.adapter.out.client.google;

import com.davidcreate.jobhub.auth.domain.valueobject.ExternalIdentity;
import com.davidcreate.jobhub.google.contract.model.GoogleUserInfoResponse;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Pure mapping, no I/O (BR3): {@code email_verified} is Google's own claim, honoured
 * as-is. Reports raw signals only (ADR 0028, Decision 3): {@code given_name}/
 * {@code family_name} pass through unchanged, and {@code name} is reported as
 * {@code fullName} so {@link com.davidcreate.jobhub.auth.domain.service.ProviderDisplayName}
 * has a fallback for the defensive case where Google supplies neither given nor
 * family name (DN-4). The adapter does not decide a display name itself.
 */
@ApplicationScoped
public class GoogleIdentityMapper {

    public ExternalIdentity toExternalIdentity(GoogleUserInfoResponse userInfo) {
        return ExternalIdentity.builder()
                .provider("google")
                .providerUserId(userInfo.getSub())
                .email(userInfo.getEmail())
                .emailVerified(Boolean.TRUE.equals(userInfo.getEmailVerified()))
                .firstName(userInfo.getGivenName())
                .lastName(userInfo.getFamilyName())
                .fullName(userInfo.getName())
                .build();
    }
}
