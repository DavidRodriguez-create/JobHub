package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorChallengeInvalidException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TwoFactorChallengeInvalidExceptionMapper implements ExceptionMapper<TwoFactorChallengeInvalidException> {

    @Override
    public Response toResponse(TwoFactorChallengeInvalidException ex) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse()
                        .error("Two-Factor Challenge Invalid")
                        .message(ex.getMessage()))
                .build();
    }
}
