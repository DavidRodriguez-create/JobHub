package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.OAuthAuthorizationResult;
import com.davidcreate.jobhub.auth.application.port.out.OAuthProviderClient;
import com.davidcreate.jobhub.auth.application.usecase.StartOAuthAuthorizationService;
import com.davidcreate.jobhub.auth.domain.exception.ProviderNotConfiguredException;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartOAuthAuthorizationService Unit Tests")
class StartOAuthAuthorizationServiceTest {

    @Mock Instance<OAuthProviderClient> clients;
    @Mock OAuthProviderClient googleClient;
    @Mock OAuthProviderClient githubClient;

    private StartOAuthAuthorizationService service;

    private void withClients(OAuthProviderClient... available) {
        service = new StartOAuthAuthorizationService(clients);
        lenient().when(clients.stream()).thenAnswer(inv -> Stream.of(available));
    }

    // TC-459-A1: OAUTH-START-1. Configured google provider returns an authorization
    // URL and a state value the REST layer can bind as the cookie.
    @Test
    @DisplayName("TC-459-A1: configured google provider returns authorizationUrl + state")
    void startsGoogleFlow() {
        withClients(googleClient, githubClient);
        when(googleClient.supports("google")).thenReturn(true);
        when(googleClient.isConfigured()).thenReturn(true);
        when(googleClient.buildAuthorizationUrl(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> "https://accounts.google.com/o/oauth2/v2/auth?client_id=abc"
                        + "&redirect_uri=http://localhost:5173/oauth/google/callback"
                        + "&scope=openid+email+profile&state=" + inv.getArgument(0));

        OAuthAuthorizationResult result = service.start("google");

        assertThat(result.authorizationUrl()).contains("accounts.google.com");
        assertThat(result.authorizationUrl()).contains("state=" + result.state());
        assertThat(result.state()).isNotBlank();
    }

    // TC-459-A2: OAUTH-START-2. GitHub start requests user:email scope.
    @Test
    @DisplayName("TC-459-A2: configured github provider returns authorizationUrl requesting user:email scope")
    void startsGithubFlow() {
        withClients(googleClient, githubClient);
        when(githubClient.supports("github")).thenReturn(true);
        when(githubClient.isConfigured()).thenReturn(true);
        when(githubClient.buildAuthorizationUrl(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> "https://github.com/login/oauth/authorize?client_id=abc"
                        + "&scope=read:user+user:email&state=" + inv.getArgument(0));

        OAuthAuthorizationResult result = service.start("github");

        assertThat(result.authorizationUrl()).contains("github.com");
        assertThat(result.authorizationUrl()).contains("user:email");
        assertThat(result.state()).isNotBlank();
    }

    // TC-459-A3: OAUTH-START-4/BR10. Two calls in a row never reuse the same state.
    @Test
    @DisplayName("TC-459-A3: two start() calls in a row return different state values")
    void generatesFreshStateEveryCall() {
        withClients(googleClient);
        when(googleClient.supports("google")).thenReturn(true);
        when(googleClient.isConfigured()).thenReturn(true);
        when(googleClient.buildAuthorizationUrl(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> "https://accounts.google.com/authorize?state=" + inv.getArgument(0));

        OAuthAuthorizationResult first = service.start("google");
        OAuthAuthorizationResult second = service.start("google");

        assertThat(first.state()).isNotEqualTo(second.state());
    }

    // TC-459-A4: OAUTH-START-3. No client configured for the provider -> 404-mapped exception.
    @Test
    @DisplayName("TC-459-A4: unconfigured provider throws ProviderNotConfiguredException")
    void throwsWhenProviderNotConfigured() {
        withClients(googleClient);
        when(googleClient.supports("google")).thenReturn(true);
        when(googleClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.start("google"))
                .isInstanceOf(ProviderNotConfiguredException.class);
    }

    // TC-459-A4 (unknown provider variant)
    @Test
    @DisplayName("TC-459-A4: unknown provider (not google/github) throws ProviderNotConfiguredException")
    void throwsWhenProviderUnknown() {
        service = new StartOAuthAuthorizationService(clients);

        assertThatThrownBy(() -> service.start("facebook"))
                .isInstanceOf(ProviderNotConfiguredException.class);
    }
}
