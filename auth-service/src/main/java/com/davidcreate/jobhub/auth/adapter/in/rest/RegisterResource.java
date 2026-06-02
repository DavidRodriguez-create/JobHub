package com.davidcreate.jobhub.auth.adapter.in.rest;

import com.davidcreate.jobhub.auth.adapter.in.rest.dto.AccountResponseMapper;
import com.davidcreate.jobhub.auth.application.port.in.RegisterUserCommand;
import com.davidcreate.jobhub.auth.application.port.in.RegisterUserUseCase;
import com.davidcreate.jobhub.auth.contract.api.RegisterApi;
import com.davidcreate.jobhub.auth.contract.model.RegisterRequest;
import com.davidcreate.jobhub.auth.domain.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@Path("/register")
@RequiredArgsConstructor
public class RegisterResource implements RegisterApi {

    private final RegisterUserUseCase registerUserUseCase;

    @Override
    public Response register(RegisterRequest req) {
        User user = registerUserUseCase.register(new RegisterUserCommand(
                req.getFirstName(), req.getLastName(), req.getEmail(), req.getPassword()));
        return Response.status(Response.Status.CREATED)
                .entity(AccountResponseMapper.toAccount(user))
                .build();
    }
}
