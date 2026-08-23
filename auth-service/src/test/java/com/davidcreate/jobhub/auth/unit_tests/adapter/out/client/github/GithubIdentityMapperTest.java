package com.davidcreate.jobhub.auth.unit_tests.adapter.out.client.github;

import com.davidcreate.jobhub.auth.adapter.out.client.github.GithubIdentityMapper;
import com.davidcreate.jobhub.auth.domain.valueobject.ExternalIdentity;
import com.davidcreate.jobhub.github.contract.model.GithubEmailResponse;
import com.davidcreate.jobhub.github.contract.model.GithubUserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GithubIdentityMapper Unit Tests")
class GithubIdentityMapperTest {

    private final GithubIdentityMapper mapper = new GithubIdentityMapper();

    private GithubUserResponse user(String login, String name) {
        return new GithubUserResponse().id(42L).login(login).name(name);
    }

    private GithubEmailResponse email(String address, boolean primary, boolean verified) {
        return new GithubEmailResponse().email(address).primary(primary).verified(verified);
    }

    // TC-459-A20: BR3, primary+verified selection ignores individually-primary or
    // individually-verified entries.
    @Test
    @DisplayName("TC-459-A20: only the primary+verified entry is reflected, others ignored")
    void selectsOnlyPrimaryAndVerifiedEntry() {
        List<GithubEmailResponse> emails = List.of(
                email("nonprimary.verified@example.com", false, true),
                email("primary.unverified@example.com", true, false),
                email("primary.verified@example.com", true, true));

        ExternalIdentity identity = mapper.toExternalIdentity(user("octocat", "Ada Lovelace"), emails);

        assertThat(identity.getEmail()).isEqualTo("primary.verified@example.com");
        assertThat(identity.isEmailVerified()).isTrue();
    }

    // TC-459-A21: BR3/OAUTH-NEW-GITHUB-2, primary but no verified entry at all ->
    // emailVerified false, not an error.
    @Test
    @DisplayName("TC-459-A21: primary but unverified, no other verified entry -> emailVerified false")
    void primaryUnverifiedNoOtherVerifiedEntry() {
        List<GithubEmailResponse> emails = List.of(email("primary.unverified@example.com", true, false));

        ExternalIdentity identity = mapper.toExternalIdentity(user("octocat", "Ada Lovelace"), emails);

        assertThat(identity.getEmail()).isEqualTo("primary.unverified@example.com");
        assertThat(identity.isEmailVerified()).isFalse();
    }

    // TC-459-A22: OAUTH-NEW-GITHUB-3/BR7, no usable email at all -> null/blank, no invented placeholder.
    @Test
    @DisplayName("TC-459-A22: empty emails list -> email is null, mapper invents nothing")
    void emptyEmailsListYieldsNullEmail() {
        ExternalIdentity identity = mapper.toExternalIdentity(user("octocat", "Ada Lovelace"), List.of());

        assertThat(identity.getEmail()).isNull();
        assertThat(identity.isEmailVerified()).isFalse();
        assertThat(identity.hasUsableEmail()).isFalse();
    }

    // TC-459-A23, superseded by ADR 0028 Decision 3: the mapper no longer decides a
    // display name itself (that fallback now lives in ProviderDisplayName, DN-7,
    // TC-506-A17). It reports the raw username signal and leaves firstName/lastName
    // null, exactly like a name-only profile would leave username null.
    @Test
    @DisplayName("TC-459-A23 (superseded): no public name -> raw username/fullName reported, mapper decides nothing")
    void blankNameReportsRawUsernameSignal() {
        List<GithubEmailResponse> emails = List.of(email("octocat@example.com", true, true));

        ExternalIdentity identity = mapper.toExternalIdentity(user("octocat", null), emails);

        assertThat(identity.getUsername()).isEqualTo("octocat");
        assertThat(identity.getFullName()).isNull();
        assertThat(identity.getFirstName()).isNull();
        assertThat(identity.getLastName()).isNull();
    }

    // TC-506 regression: GitHub has no first/last-name fields at all; a public
    // two-word name is reported as the raw fullName signal, not pre-split.
    @Test
    @DisplayName("GitHub two-word public name is reported as raw fullName, not split by the mapper")
    void publicNameReportsRawFullNameSignal() {
        List<GithubEmailResponse> emails = List.of(email("ada@example.com", true, true));

        ExternalIdentity identity = mapper.toExternalIdentity(user("octocat", "Ada Lovelace"), emails);

        assertThat(identity.getFullName()).isEqualTo("Ada Lovelace");
        assertThat(identity.getUsername()).isEqualTo("octocat");
        assertThat(identity.getFirstName()).isNull();
        assertThat(identity.getLastName()).isNull();
    }
}
