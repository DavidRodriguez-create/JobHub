package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.OAuthProviderAvailability;
import com.davidcreate.jobhub.auth.application.port.out.OAuthProviderClient;
import com.davidcreate.jobhub.auth.application.usecase.ListOAuthProvidersService;
import com.davidcreate.jobhub.auth.domain.valueobject.OAuthProvider;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@code Instance<OAuthProviderClient>} mocked, no I/O (ADR 0028, Decision 2).
 * Covers TC-506-A22..A26.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ListOAuthProvidersService Unit Tests")
class ListOAuthProvidersServiceTest {

    @Mock Instance<OAuthProviderClient> clients;
    @Mock OAuthProviderClient googleClient;
    @Mock OAuthProviderClient githubClient;

    private ListOAuthProvidersService service;

    private void setUp() {
        service = new ListOAuthProvidersService(clients);
        lenient().when(googleClient.supports("google")).thenReturn(true);
        lenient().when(googleClient.supports("github")).thenReturn(false);
        lenient().when(githubClient.supports("github")).thenReturn(true);
        lenient().when(githubClient.supports("google")).thenReturn(false);
    }

    // TC-506-A22: AVAIL-4/AVAIL-6.
    @Test
    @DisplayName("TC-506-A22: both clients configured -> [(google,true),(github,true)] in that order")
    void bothConfiguredReturnsBothTrueInStableOrder() {
        setUp();
        when(clients.stream()).thenAnswer(inv -> Stream.of(googleClient, githubClient));
        when(googleClient.isConfigured()).thenReturn(true);
        when(githubClient.isConfigured()).thenReturn(true);

        List<OAuthProviderAvailability> result = service.list();

        assertThat(result).containsExactly(
                new OAuthProviderAvailability(OAuthProvider.GOOGLE, true),
                new OAuthProviderAvailability(OAuthProvider.GITHUB, true));
    }

    // TC-506-A23: AVAIL-2.
    @Test
    @DisplayName("TC-506-A23: only google configured -> [(google,true),(github,false)]")
    void onlyGoogleConfigured() {
        setUp();
        when(clients.stream()).thenAnswer(inv -> Stream.of(googleClient, githubClient));
        when(googleClient.isConfigured()).thenReturn(true);
        when(githubClient.isConfigured()).thenReturn(false);

        List<OAuthProviderAvailability> result = service.list();

        assertThat(result).containsExactly(
                new OAuthProviderAvailability(OAuthProvider.GOOGLE, true),
                new OAuthProviderAvailability(OAuthProvider.GITHUB, false));
    }

    // TC-506-A24: AVAIL-3.
    @Test
    @DisplayName("TC-506-A24: only github configured -> [(google,false),(github,true)]")
    void onlyGithubConfigured() {
        setUp();
        when(clients.stream()).thenAnswer(inv -> Stream.of(googleClient, githubClient));
        when(googleClient.isConfigured()).thenReturn(false);
        when(githubClient.isConfigured()).thenReturn(true);

        List<OAuthProviderAvailability> result = service.list();

        assertThat(result).containsExactly(
                new OAuthProviderAvailability(OAuthProvider.GOOGLE, false),
                new OAuthProviderAvailability(OAuthProvider.GITHUB, true));
    }

    // TC-506-A25: AVAIL-1.
    @Test
    @DisplayName("TC-506-A25: neither configured -> [(google,false),(github,false)]")
    void neitherConfigured() {
        setUp();
        when(clients.stream()).thenAnswer(inv -> Stream.of(googleClient, githubClient));
        when(googleClient.isConfigured()).thenReturn(false);
        when(githubClient.isConfigured()).thenReturn(false);

        List<OAuthProviderAvailability> result = service.list();

        assertThat(result).containsExactly(
                new OAuthProviderAvailability(OAuthProvider.GOOGLE, false),
                new OAuthProviderAvailability(OAuthProvider.GITHUB, false));
    }

    // TC-506-A26: AVAIL-6 (order is not incidental - comes from OAuthProvider.values(),
    // not from CDI bean-injection/iteration order).
    @Test
    @DisplayName("TC-506-A26: github-first CDI iteration order still yields google-first result")
    void resultOrderIsAlwaysGoogleFirstRegardlessOfBeanIterationOrder() {
        setUp();
        when(clients.stream()).thenAnswer(inv -> Stream.of(githubClient, googleClient));
        when(googleClient.isConfigured()).thenReturn(true);
        when(githubClient.isConfigured()).thenReturn(true);

        List<OAuthProviderAvailability> result = service.list();

        assertThat(result).extracting(OAuthProviderAvailability::provider)
                .containsExactly(OAuthProvider.GOOGLE, OAuthProvider.GITHUB);
    }
}
