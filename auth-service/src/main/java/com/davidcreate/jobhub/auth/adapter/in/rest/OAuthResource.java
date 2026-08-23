package com.davidcreate.jobhub.auth.adapter.in.rest;

import com.davidcreate.jobhub.auth.adapter.in.rest.dto.AccountResponseMapper;
import com.davidcreate.jobhub.auth.application.port.in.CompleteOAuthLoginUseCase;
import com.davidcreate.jobhub.auth.application.port.in.ListOAuthProvidersUseCase;
import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.application.port.in.OAuthAuthorizationResult;
import com.davidcreate.jobhub.auth.application.port.in.OAuthCallbackCommand;
import com.davidcreate.jobhub.auth.application.port.in.StartOAuthAuthorizationUseCase;
import com.davidcreate.jobhub.auth.contract.api.OauthApi;
import com.davidcreate.jobhub.auth.contract.model.OAuthAuthorizationResponse;
import com.davidcreate.jobhub.auth.contract.model.OAuthCallbackRequest;
import com.davidcreate.jobhub.auth.contract.model.OAuthProvidersResponse;
import com.davidcreate.jobhub.auth.domain.valueobject.AdminAllowlist;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Path("/oauth")
@RequiredArgsConstructor
public class OAuthResource implements OauthApi {
    // The class-level path MUST stay in sync with the generated OauthApi base path.
    // Adding GET /oauth/providers shortened the generated common prefix from
    // "/oauth/{provider}" to "/oauth", so the {provider} segment now arrives from the
    // interface's method-level @Path ("/{provider}/start", "/{provider}/callback").
    // Effective routes are unchanged (ADR 0028).

    static final String STATE_COOKIE_NAME = "oauth_state";

    private final StartOAuthAuthorizationUseCase startOAuthAuthorizationUseCase;
    private final CompleteOAuthLoginUseCase completeOAuthLoginUseCase;
    private final ListOAuthProvidersUseCase listOAuthProvidersUseCase;

    @Context
    HttpHeaders httpHeaders;

    @ConfigProperty(name = "auth.admin.emails")
    Optional<String> adminEmailsConfig;

    @Override
    public Response listOAuthProviders() {
        OAuthProvidersResponse response = new OAuthProvidersResponse();
        listOAuthProvidersUseCase.list().forEach(availability -> response.addProvidersItem(
                new com.davidcreate.jobhub.auth.contract.model.OAuthProviderAvailability()
                        .provider(com.davidcreate.jobhub.auth.contract.model.OAuthProviderAvailability.ProviderEnum
                                .fromValue(availability.provider().value()))
                        .available(availability.available())));
        return Response.ok(response).build();
    }

    @Override
    public Response startOAuthAuthorization(String provider) {
        OAuthAuthorizationResult result = startOAuthAuthorizationUseCase.start(provider);
        NewCookie stateCookie = new NewCookie.Builder(STATE_COOKIE_NAME)
                .value(result.state())
                .path("/")
                .httpOnly(true)
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(600)
                .build();
        return Response.ok(new OAuthAuthorizationResponse().authorizationUrl(URI.create(result.authorizationUrl())))
                .cookie(stateCookie)
                .build();
    }

    @Override
    public Response completeOAuthLogin(String provider, OAuthCallbackRequest oauthCallbackRequest) {
        OAuthCallbackCommand command = new OAuthCallbackCommand(
                provider, oauthCallbackRequest.getCode(), oauthCallbackRequest.getState(), boundState());
        LoginResult result = completeOAuthLoginUseCase.handle(command);
        NewCookie clearedStateCookie = new NewCookie.Builder(STATE_COOKIE_NAME)
                .value("")
                .path("/")
                .httpOnly(true)
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(0)
                .build();
        return Response.ok(toLoginResponse(result)).cookie(clearedStateCookie).build();
    }

    private String boundState() {
        Cookie cookie = httpHeaders.getCookies().get(STATE_COOKIE_NAME);
        return cookie == null ? null : cookie.getValue();
    }

    private com.davidcreate.jobhub.auth.contract.model.LoginResponse toLoginResponse(LoginResult result) {
        if (result.isTwoFactorRequired()) {
            return AccountResponseMapper.toLogin(result, false, List.of());
        }
        String email = result.user().getEmail();
        String allowlist = adminEmailsConfig.orElse("");
        boolean isAdmin = AdminAllowlist.isAdmin(allowlist, email);
        return AccountResponseMapper.toLogin(result, isAdmin, AdminAllowlist.groupsFor(allowlist, email));
    }
}
