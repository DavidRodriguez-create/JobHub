package com.davidcreate.jobhub.auth.adapter.out.client.github;

import com.davidcreate.jobhub.auth.application.port.out.OAuthProviderClient;
import com.davidcreate.jobhub.auth.domain.exception.ProviderAuthorizationFailedException;
import com.davidcreate.jobhub.auth.domain.exception.ProviderNotConfiguredException;
import com.davidcreate.jobhub.auth.domain.exception.ProviderUnavailableException;
import com.davidcreate.jobhub.auth.domain.valueobject.ExternalIdentity;
import com.davidcreate.jobhub.github.contract.model.GithubEmailResponse;
import com.davidcreate.jobhub.github.contract.model.GithubTokenResponse;
import com.davidcreate.jobhub.github.contract.model.GithubUserResponse;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Startup
@RequiredArgsConstructor
public class GithubOAuthProviderClient implements OAuthProviderClient {

    private static final Logger LOG = Logger.getLogger(GithubOAuthProviderClient.class);
    private static final String PROVIDER = "github";

    private final GithubIdentityMapper mapper;

    @Inject
    @RestClient
    GithubTokenClient tokenClient;

    @Inject
    @RestClient
    GithubApiClient apiClient;

    // Optional<String>, no defaultValue (ADR 0028, Decision 1): see
    // GoogleOAuthProviderClient for the crash-path rationale.
    @ConfigProperty(name = "auth.oauth.github.client-id")
    Optional<String> clientId;

    @ConfigProperty(name = "auth.oauth.github.client-secret")
    Optional<String> clientSecret;

    @ConfigProperty(name = "auth.oauth.redirect-base-url", defaultValue = "http://localhost:5173")
    String redirectBaseUrl;

    @ConfigProperty(name = "auth.oauth.github.authorize-url", defaultValue = "https://github.com/login/oauth/authorize")
    String authorizeUrl;

    @ConfigProperty(name = "auth.oauth.github.scope", defaultValue = "read:user user:email")
    String scope;

    @PostConstruct
    void logConfigurationState() {
        LOG.infof("OAuth provider %s: %s", PROVIDER, isConfigured() ? "configured" : "not configured");
    }

    @Override
    public boolean supports(String provider) {
        return "github".equalsIgnoreCase(provider);
    }

    @Override
    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(clientSecret);
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return authorizeUrl
                + "?client_id=" + encode(requireClientId())
                + "&redirect_uri=" + encode(redirectUri())
                + "&scope=" + encode(scope)
                + "&state=" + encode(state);
    }

    @Override
    public ExternalIdentity exchange(String code) {
        String resolvedClientId = requireClientId();
        String resolvedClientSecret = requireClientSecret();

        GithubTokenResponse token;
        try {
            token = tokenClient.exchange("application/json", resolvedClientId, resolvedClientSecret, code, redirectUri());
        } catch (WebApplicationException ex) {
            throw mapTokenFailure(ex);
        } catch (ProcessingException ex) {
            throw new ProviderUnavailableException(ex);
        }

        // GH-EXCHANGE-1 (ADR 0028, Decision 4): GitHub answers a misconfigured
        // exchange (wrong secret, stale/reused code, mismatched callback URL) with
        // HTTP 200, an `error` field, and NO access token - never a 4xx. Without
        // this guard, a null access token becomes "Bearer null" against /user,
        // which 401s and gets wrapped into a misleading 502 "provider outage".
        String accessToken = token.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            LOG.warnf("GitHub token exchange failed: error=%s, description=%s",
                    token.getError(), token.getErrorDescription());
            throw new ProviderAuthorizationFailedException();
        }

        String bearer = "Bearer " + accessToken;
        GithubUserResponse user;
        List<GithubEmailResponse> emails;
        try {
            user = apiClient.user(bearer);
            emails = apiClient.emails(bearer);
        } catch (WebApplicationException | ProcessingException ex) {
            throw new ProviderUnavailableException(ex);
        }

        return mapper.toExternalIdentity(user, emails);
    }

    private String redirectUri() {
        return redirectBaseUrl + "/oauth/github/callback";
    }

    private RuntimeException mapTokenFailure(WebApplicationException ex) {
        if (ex.getResponse().getStatus() >= 500) {
            return new ProviderUnavailableException(ex);
        }
        return new ProviderAuthorizationFailedException(ex);
    }

    private String requireClientId() {
        return clientId.filter(GithubOAuthProviderClient::notBlank)
                .orElseThrow(() -> new ProviderNotConfiguredException(PROVIDER));
    }

    private String requireClientSecret() {
        return clientSecret.filter(GithubOAuthProviderClient::notBlank)
                .orElseThrow(() -> new ProviderNotConfiguredException(PROVIDER));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean notBlank(Optional<String> value) {
        return value.filter(GithubOAuthProviderClient::notBlank).isPresent();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
