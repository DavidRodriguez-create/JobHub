package com.davidcreate.jobhub.auth.unit_tests.adapter.out.client.github;

import com.davidcreate.jobhub.auth.adapter.out.client.github.GithubApiClient;
import com.davidcreate.jobhub.auth.adapter.out.client.github.GithubIdentityMapper;
import com.davidcreate.jobhub.auth.adapter.out.client.github.GithubOAuthProviderClient;
import com.davidcreate.jobhub.auth.adapter.out.client.github.GithubTokenClient;
import com.davidcreate.jobhub.auth.domain.exception.ProviderAuthorizationFailedException;
import com.davidcreate.jobhub.auth.domain.exception.ProviderNotConfiguredException;
import com.davidcreate.jobhub.auth.domain.valueobject.ExternalIdentity;
import com.davidcreate.jobhub.github.contract.model.GithubTokenResponse;
import com.davidcreate.jobhub.github.contract.model.GithubUserResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code Optional<String>} credential shape (ADR 0028, Decision 1) + the blank-
 * access-token guard (Decision 4, the "GitHub unavailable" defect). Covers
 * TC-506-A6..A10, A33..A37.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GithubOAuthProviderClient Unit Tests")
class GithubOAuthProviderClientTest {

    @Mock
    GithubTokenClient tokenClient;

    @Mock
    GithubApiClient apiClient;

    private GithubOAuthProviderClient newClient(Optional<String> clientId, Optional<String> clientSecret)
            throws Exception {
        GithubOAuthProviderClient client = new GithubOAuthProviderClient(new GithubIdentityMapper());
        setField(client, "tokenClient", tokenClient);
        setField(client, "apiClient", apiClient);
        setField(client, "clientId", clientId);
        setField(client, "clientSecret", clientSecret);
        setField(client, "authorizeUrl", "https://github.com/login/oauth/authorize");
        setField(client, "redirectBaseUrl", "http://localhost:5173");
        setField(client, "scope", "read:user user:email");
        return client;
    }

    private GithubOAuthProviderClient newConfiguredClient() throws Exception {
        return newClient(Optional.of("gh-client-id"), Optional.of("gh-client-secret"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = GithubOAuthProviderClient.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    // TC-506-A6: mirrors A1.
    @Test
    @DisplayName("TC-506-A6: both credentials present and non-blank -> isConfigured() true")
    void bothCredentialsPresentIsConfiguredTrue() throws Exception {
        assertThat(newConfiguredClient().isConfigured()).isTrue();
    }

    // TC-506-A7: mirrors A2 (clientId absent).
    @Test
    @DisplayName("TC-506-A7: clientId absent -> isConfigured() false, no throw")
    void clientIdAbsentIsConfiguredFalseNoThrow() throws Exception {
        GithubOAuthProviderClient client = newClient(Optional.empty(), Optional.of("secret"));

        assertThatCode(client::isConfigured).doesNotThrowAnyException();
        assertThat(client.isConfigured()).isFalse();
    }

    // TC-506-A8: AVAIL-7, clientSecret present-but-blank.
    @Test
    @DisplayName("TC-506-A8: clientSecret present but blank -> isConfigured() false")
    void clientSecretPresentButBlankIsConfiguredFalse() throws Exception {
        GithubOAuthProviderClient client = newClient(Optional.of("id"), Optional.of("   "));

        assertThat(client.isConfigured()).isFalse();
    }

    // TC-506-A9: mirrors A4 (clientSecret absent).
    @Test
    @DisplayName("TC-506-A9: clientSecret absent -> isConfigured() false")
    void clientSecretAbsentIsConfiguredFalse() throws Exception {
        GithubOAuthProviderClient client = newClient(Optional.of("id"), Optional.empty());

        assertThat(client.isConfigured()).isFalse();
    }

    // TC-506-A10: mirrors A5.
    @Test
    @DisplayName("TC-506-A10: unconfigured client throws ProviderNotConfiguredException from buildAuthorizationUrl/exchange")
    void unconfiguredClientThrowsOnUrlBuildAndExchange() throws Exception {
        GithubOAuthProviderClient client = newClient(Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> client.buildAuthorizationUrl("state"))
                .isInstanceOf(ProviderNotConfiguredException.class);
        assertThatThrownBy(() -> client.exchange("code"))
                .isInstanceOf(ProviderNotConfiguredException.class);
        verifyNoInteractions(tokenClient, apiClient);
    }

    // TC-506-A33: GH-EXCHANGE-1 (the defect fix, load-bearing).
    @Test
    @DisplayName("TC-506-A33: null access token -> ProviderAuthorizationFailedException, /user and /emails never called")
    void nullAccessTokenThrowsAndNeverCallsApi() throws Exception {
        GithubOAuthProviderClient client = newConfiguredClient();
        GithubTokenResponse response = new GithubTokenResponse()
                .accessToken(null)
                .error("bad_verification_code")
                .errorDescription("The code passed is incorrect or expired.");
        when(tokenClient.exchange(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(response);

        assertThatThrownBy(() -> client.exchange("code"))
                .isInstanceOf(ProviderAuthorizationFailedException.class);
        verifyNoInteractions(apiClient);
    }

    // TC-506-A34: GH-EXCHANGE-1 variant, empty (not null) access token.
    @Test
    @DisplayName("TC-506-A34: empty-string access token -> identical outcome to A33")
    void emptyAccessTokenThrowsAndNeverCallsApi() throws Exception {
        GithubOAuthProviderClient client = newConfiguredClient();
        GithubTokenResponse response = new GithubTokenResponse().accessToken("").error("bad_verification_code");
        when(tokenClient.exchange(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(response);

        assertThatThrownBy(() -> client.exchange("code"))
                .isInstanceOf(ProviderAuthorizationFailedException.class);
        verifyNoInteractions(apiClient);
    }

    // TC-506-A36: GH-EXCHANGE-5, guard doesn't fire on the happy path.
    @Test
    @DisplayName("TC-506-A36: non-blank access token -> exchange proceeds to call apiClient as before")
    void nonBlankAccessTokenProceedsToApiClient() throws Exception {
        GithubOAuthProviderClient client = newConfiguredClient();
        GithubTokenResponse response = new GithubTokenResponse().accessToken("gh-token");
        when(tokenClient.exchange(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(response);
        when(apiClient.user(anyString())).thenReturn(new GithubUserResponse().id(1L).login("octocat"));
        when(apiClient.emails(anyString())).thenReturn(List.of());

        ExternalIdentity identity = client.exchange("code");

        assertThat(identity.getUsername()).isEqualTo("octocat");
    }

    // Regression: GithubOAuthProviderClient's own mapTokenFailure() (4xx path,
    // pre-existing from #459) is unaffected by the new blank-token guard - they
    // are independent branches (the 4xx case never reaches the guard at all).
    @Test
    @DisplayName("An actual 4xx from the token endpoint still maps to ProviderAuthorizationFailedException")
    void tokenEndpoint4xxStillMapsToAuthorizationFailure() throws Exception {
        GithubOAuthProviderClient client = newConfiguredClient();
        WebApplicationException badRequest = new WebApplicationException(Response.status(400).build());
        when(tokenClient.exchange(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(badRequest);

        assertThatThrownBy(() -> client.exchange("code"))
                .isInstanceOf(ProviderAuthorizationFailedException.class);
        verifyNoInteractions(apiClient);
    }
}
