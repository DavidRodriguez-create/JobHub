package com.davidcreate.jobhub.auth.unit_tests.adapter.out.client.google;

import com.davidcreate.jobhub.auth.adapter.out.client.google.GoogleIdentityMapper;
import com.davidcreate.jobhub.auth.adapter.out.client.google.GoogleOAuthProviderClient;
import com.davidcreate.jobhub.auth.adapter.out.client.google.GoogleTokenClient;
import com.davidcreate.jobhub.auth.domain.exception.ProviderAuthorizationFailedException;
import com.davidcreate.jobhub.auth.domain.exception.ProviderNotConfiguredException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code Optional<String>} credential shape (ADR 0028, Decision 1), the crash-path
 * fix. Covers TC-506-A1..A5, A37.
 */
@DisplayName("GoogleOAuthProviderClient Unit Tests")
class GoogleOAuthProviderClientTest {

    private GoogleOAuthProviderClient newClient(Optional<String> clientId, Optional<String> clientSecret)
            throws Exception {
        GoogleOAuthProviderClient client = new GoogleOAuthProviderClient(new GoogleIdentityMapper());
        setField(client, "clientId", clientId);
        setField(client, "clientSecret", clientSecret);
        setField(client, "authorizeUrl", "https://accounts.google.com/o/oauth2/v2/auth");
        setField(client, "redirectBaseUrl", "http://localhost:5173");
        setField(client, "scope", "openid email profile");
        return client;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = GoogleOAuthProviderClient.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    // TC-506-A1: AVAIL-BOOT-3 (regression baseline).
    @Test
    @DisplayName("TC-506-A1: both credentials present and non-blank -> isConfigured() true")
    void bothCredentialsPresentIsConfiguredTrue() throws Exception {
        GoogleOAuthProviderClient client = newClient(Optional.of("id"), Optional.of("secret"));

        assertThat(client.isConfigured()).isTrue();
    }

    // TC-506-A2: AVAIL-BOOT-1/AVAIL-1 (the crash-path fix, load-bearing).
    @Test
    @DisplayName("TC-506-A2: clientId absent (Optional.empty()) -> isConfigured() false, no throw")
    void clientIdAbsentIsConfiguredFalseNoThrow() throws Exception {
        GoogleOAuthProviderClient client = newClient(Optional.empty(), Optional.of("secret"));

        assertThatCode(client::isConfigured).doesNotThrowAnyException();
        assertThat(client.isConfigured()).isFalse();
    }

    // TC-506-A3: AVAIL-7 (partial credentials count as unconfigured).
    @Test
    @DisplayName("TC-506-A3: clientId present but blank -> isConfigured() false")
    void clientIdPresentButBlankIsConfiguredFalse() throws Exception {
        GoogleOAuthProviderClient client = newClient(Optional.of("   "), Optional.of("secret"));

        assertThat(client.isConfigured()).isFalse();
    }

    // TC-506-A4: AVAIL-7.
    @Test
    @DisplayName("TC-506-A4: clientSecret absent -> isConfigured() false")
    void clientSecretAbsentIsConfiguredFalse() throws Exception {
        GoogleOAuthProviderClient client = newClient(Optional.of("id"), Optional.empty());

        assertThat(client.isConfigured()).isFalse();
    }

    // TC-506-A5: AVAIL-BOOT-1 (defensive, "never a half-formed request").
    @Test
    @DisplayName("TC-506-A5: unconfigured client throws ProviderNotConfiguredException from buildAuthorizationUrl/exchange")
    void unconfiguredClientThrowsOnUrlBuildAndExchange() throws Exception {
        GoogleOAuthProviderClient client = newClient(Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> client.buildAuthorizationUrl("state"))
                .isInstanceOf(ProviderNotConfiguredException.class);
        assertThatThrownBy(() -> client.exchange("code"))
                .isInstanceOf(ProviderNotConfiguredException.class);
    }

    // TC-506-A37: GH-EXCHANGE-5 (Google unaffected, regression). Google's own
    // mapTokenFailure() (4xx -> ProviderAuthorizationFailedException) is re-run
    // unmodified to confirm the github-only blank-token guard didn't touch it.
    @Test
    @DisplayName("TC-506-A37: an actual 4xx from Google's token endpoint still maps to ProviderAuthorizationFailedException")
    void tokenEndpoint4xxStillMapsToAuthorizationFailure() throws Exception {
        GoogleOAuthProviderClient client = newClient(Optional.of("id"), Optional.of("secret"));
        GoogleTokenClient tokenClient = mock(GoogleTokenClient.class);
        when(tokenClient.exchange(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new WebApplicationException(Response.status(400).build()));
        setField(client, "tokenClient", tokenClient);

        assertThatThrownBy(() -> client.exchange("code"))
                .isInstanceOf(ProviderAuthorizationFailedException.class);
    }
}
