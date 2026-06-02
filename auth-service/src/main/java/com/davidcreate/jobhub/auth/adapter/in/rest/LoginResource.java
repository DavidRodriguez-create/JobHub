package com.davidcreate.jobhub.auth.adapter.in.rest;

import com.davidcreate.jobhub.auth.adapter.in.rest.dto.AccountResponseMapper;
import com.davidcreate.jobhub.auth.application.port.in.LoginCommand;
import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.application.port.in.LoginUseCase;
import com.davidcreate.jobhub.auth.contract.api.LoginApi;
import com.davidcreate.jobhub.auth.contract.model.LoginRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@Path("/login")
@RequiredArgsConstructor
public class LoginResource implements LoginApi {

    private final LoginUseCase loginUseCase;

    @Override
    public Response login(LoginRequest req) {
        LoginResult result = loginUseCase.login(new LoginCommand(req.getEmail(), req.getPassword()));
        return Response.ok(AccountResponseMapper.toLogin(result)).build();
    }
}
