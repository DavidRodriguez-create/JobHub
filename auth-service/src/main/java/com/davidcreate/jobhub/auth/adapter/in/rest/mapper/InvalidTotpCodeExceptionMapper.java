package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.InvalidTotpCodeException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidTotpCodeExceptionMapper implements ExceptionMapper<InvalidTotpCodeException> {

    @Override
    public Response toResponse(InvalidTotpCodeException ex) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse()
                        .error("Invalid Totp Code")
                        .message(ex.getMessage()))
                .build();
    }
}
