package com.davidcreate.jobhub.auth.unit_tests.adapter.out.client.google;

import com.davidcreate.jobhub.auth.adapter.out.client.google.GoogleIdentityMapper;
import com.davidcreate.jobhub.auth.domain.valueobject.ExternalIdentity;
import com.davidcreate.jobhub.google.contract.model.GoogleUserInfoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GoogleIdentityMapper Unit Tests")
class GoogleIdentityMapperTest {

    private final GoogleIdentityMapper mapper = new GoogleIdentityMapper();

    // TC-459-A18: BR3, Google verified-email source.
    @Test
    @DisplayName("TC-459-A18: email_verified true maps to emailVerified true")
    void mapsVerifiedEmail() {
        GoogleUserInfoResponse userInfo = new GoogleUserInfoResponse()
                .sub("google-sub-1")
                .email("new.google.user@example.com")
                .emailVerified(true)
                .givenName("Ada")
                .familyName("Lovelace");

        ExternalIdentity identity = mapper.toExternalIdentity(userInfo);

        assertThat(identity.getProvider()).isEqualTo("google");
        assertThat(identity.getProviderUserId()).isEqualTo("google-sub-1");
        assertThat(identity.getEmail()).isEqualTo("new.google.user@example.com");
        assertThat(identity.isEmailVerified()).isTrue();
    }

    // TC-459-A19: BR3, unverified Google email.
    @Test
    @DisplayName("TC-459-A19: email_verified false maps to emailVerified false")
    void mapsUnverifiedEmail() {
        GoogleUserInfoResponse userInfo = new GoogleUserInfoResponse()
                .sub("google-sub-2")
                .email("unverified@example.com")
                .emailVerified(false)
                .givenName("Ada")
                .familyName("Lovelace");

        ExternalIdentity identity = mapper.toExternalIdentity(userInfo);

        assertThat(identity.isEmailVerified()).isFalse();
        assertThat(identity.getEmail()).isEqualTo("unverified@example.com");
    }

    // TC-459-A24: BR8 regression guard - Google's given/family name pass through
    // unchanged, no GitHub-style username fallback ever applies here.
    @Test
    @DisplayName("TC-459-A24: given/family name pass through unchanged, no fallback logic applied")
    void nameFieldsPassThroughUnchanged() {
        GoogleUserInfoResponse userInfo = new GoogleUserInfoResponse()
                .sub("google-sub-3")
                .email("full.name@example.com")
                .emailVerified(true)
                .givenName("Grace")
                .familyName("Hopper");

        ExternalIdentity identity = mapper.toExternalIdentity(userInfo);

        assertThat(identity.getFirstName()).isEqualTo("Grace");
        assertThat(identity.getLastName()).isEqualTo("Hopper");
    }

    // ADR 0028, Decision 3: `name` is reported as the raw `fullName` signal so
    // ProviderDisplayName has a fallback for the defensive DN-4 case (no given/
    // family name supplied at all). The mapper decides nothing itself.
    @Test
    @DisplayName("Google's full display name is reported as the raw fullName signal")
    void fullNameIsReportedAsRawSignal() {
        GoogleUserInfoResponse userInfo = new GoogleUserInfoResponse()
                .sub("google-sub-4")
                .email("alex@example.com")
                .emailVerified(true)
                .name("Alex Morales")
                .givenName("Alex");

        ExternalIdentity identity = mapper.toExternalIdentity(userInfo);

        assertThat(identity.getFullName()).isEqualTo("Alex Morales");
        assertThat(identity.getFirstName()).isEqualTo("Alex");
        assertThat(identity.getLastName()).isNull();
    }
}
