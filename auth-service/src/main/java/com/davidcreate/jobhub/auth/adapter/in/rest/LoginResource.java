package com.davidcreate.jobhub.auth.adapter.in.rest;

import com.davidcreate.jobhub.auth.adapter.in.rest.dto.AccountResponseMapper;
import com.davidcreate.jobhub.auth.application.port.in.LoginCommand;
import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.application.port.in.LoginUseCase;
import com.davidcreate.jobhub.auth.application.port.in.VerifyLoginTwoFactorCommand;
import com.davidcreate.jobhub.auth.application.port.in.VerifyLoginTwoFactorUseCase;
import com.davidcreate.jobhub.auth.contract.api.LoginApi;
import com.davidcreate.jobhub.auth.contract.model.LoginRequest;
import com.davidcreate.jobhub.auth.contract.model.TwoFactorLoginRequest;
import com.davidcreate.jobhub.auth.domain.valueobject.AdminAllowlist;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@ApplicationScoped
@Path("/login")
@RequiredArgsConstructor
public class LoginResource implements LoginApi {

    private final LoginUseCase loginUseCase;
    private final VerifyLoginTwoFactorUseCase verifyLoginTwoFactorUseCase;

    @ConfigProperty(name = "auth.admin.emails")
    Optional<String> adminEmailsConfig;

    @Override
    public Response login(LoginRequest req) {
        LoginResult result = loginUseCase.login(new LoginCommand(req.getEmail(), req.getPassword()));
        return Response.ok(toLoginResponse(result)).build();
    }

    @Override
    public Response loginTwoFactor(TwoFactorLoginRequest req) {
        LoginResult result = verifyLoginTwoFactorUseCase.verify(
                new VerifyLoginTwoFactorCommand(req.getTwoFactorToken(), req.getTotpCode()));
        return Response.ok(toLoginResponse(result)).build();
    }

    private com.davidcreate.jobhub.auth.contract.model.LoginResponse toLoginResponse(LoginResult result) {
        if (result.isTwoFactorRequired()) {
            return AccountResponseMapper.toLogin(result, false, java.util.List.of());
        }
        String email = result.user().getEmail();
        String allowlist = adminEmailsConfig.orElse("");
        boolean isAdmin = AdminAllowlist.isAdmin(allowlist, email);
        return AccountResponseMapper.toLogin(result, isAdmin, AdminAllowlist.groupsFor(allowlist, email));
    }
}
