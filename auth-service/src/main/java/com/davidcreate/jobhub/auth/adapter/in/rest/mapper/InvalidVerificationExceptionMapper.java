package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.InvalidVerificationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidVerificationExceptionMapper implements ExceptionMapper<InvalidVerificationException> {

    @Override
    public Response toResponse(InvalidVerificationException ex) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse()
                        .error("Invalid Verification")
                        .message(ex.getMessage()))
                .build();
    }
}
