package com.davidcreate.jobhub.auth.adapter.out.client.google;

import com.davidcreate.jobhub.auth.application.port.out.OAuthProviderClient;
import com.davidcreate.jobhub.auth.domain.exception.ProviderAuthorizationFailedException;
import com.davidcreate.jobhub.auth.domain.exception.ProviderNotConfiguredException;
import com.davidcreate.jobhub.auth.domain.exception.ProviderUnavailableException;
import com.davidcreate.jobhub.auth.domain.valueobject.ExternalIdentity;
import com.davidcreate.jobhub.google.contract.model.GoogleTokenResponse;
import com.davidcreate.jobhub.google.contract.model.GoogleUserInfoResponse;
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
import java.util.Optional;

@ApplicationScoped
@Startup
@RequiredArgsConstructor
public class GoogleOAuthProviderClient implements OAuthProviderClient {

    private static final Logger LOG = Logger.getLogger(GoogleOAuthProviderClient.class);
    private static final String PROVIDER = "google";

    private final GoogleIdentityMapper mapper;

    @Inject
    @RestClient
    GoogleTokenClient tokenClient;

    @Inject
    @RestClient
    GoogleUserInfoClient userInfoClient;

    // Optional<String>, no defaultValue (ADR 0028, Decision 1): a plain `String`
    // injection point with an empty defaultValue is treated by SmallRye Config as
    // NO default at all, so an unset GOOGLE_OAUTH_CLIENT_ID fails config
    // validation at startup and crash-loops the whole service. Optional collapses
    // "absent" and "present but blank" into the same non-throwing state.
    @ConfigProperty(name = "auth.oauth.google.client-id")
    Optional<String> clientId;

    @ConfigProperty(name = "auth.oauth.google.client-secret")
    Optional<String> clientSecret;

    @ConfigProperty(name = "auth.oauth.redirect-base-url", defaultValue = "http://localhost:5173")
    String redirectBaseUrl;

    @ConfigProperty(name = "auth.oauth.google.authorize-url", defaultValue = "https://accounts.google.com/o/oauth2/v2/auth")
    String authorizeUrl;

    @ConfigProperty(name = "auth.oauth.google.scope", defaultValue = "openid email profile")
    String scope;

    @PostConstruct
    void logConfigurationState() {
        LOG.infof("OAuth provider %s: %s", PROVIDER, isConfigured() ? "configured" : "not configured");
    }

    @Override
    public boolean supports(String provider) {
        return "google".equalsIgnoreCase(provider);
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
                + "&response_type=code"
                + "&scope=" + encode(scope)
                + "&state=" + encode(state);
    }

    @Override
    public ExternalIdentity exchange(String code) {
        String resolvedClientId = requireClientId();
        String resolvedClientSecret = requireClientSecret();

        GoogleTokenResponse token;
        try {
            token = tokenClient.exchange(code, resolvedClientId, resolvedClientSecret, redirectUri(), "authorization_code");
        } catch (WebApplicationException ex) {
            throw mapTokenFailure(ex);
        } catch (ProcessingException ex) {
            throw new ProviderUnavailableException(ex);
        }

        GoogleUserInfoResponse userInfo;
        try {
            userInfo = userInfoClient.userInfo("Bearer " + token.getAccessToken());
        } catch (WebApplicationException | ProcessingException ex) {
            throw new ProviderUnavailableException(ex);
        }
        return mapper.toExternalIdentity(userInfo);
    }

    private String redirectUri() {
        return redirectBaseUrl + "/oauth/google/callback";
    }

    private RuntimeException mapTokenFailure(WebApplicationException ex) {
        if (ex.getResponse().getStatus() >= 500) {
            return new ProviderUnavailableException(ex);
        }
        return new ProviderAuthorizationFailedException(ex);
    }

    private String requireClientId() {
        return clientId.filter(GoogleOAuthProviderClient::notBlank)
                .orElseThrow(() -> new ProviderNotConfiguredException(PROVIDER));
    }

    private String requireClientSecret() {
        return clientSecret.filter(GoogleOAuthProviderClient::notBlank)
                .orElseThrow(() -> new ProviderNotConfiguredException(PROVIDER));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean notBlank(Optional<String> value) {
        return value.filter(GoogleOAuthProviderClient::notBlank).isPresent();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
