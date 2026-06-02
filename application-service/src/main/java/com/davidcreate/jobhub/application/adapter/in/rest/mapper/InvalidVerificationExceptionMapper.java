package com.davidcreate.jobhub.application.adapter.in.rest.mapper;

import com.davidcreate.jobhub.application.contract.model.ErrorResponse;
import com.davidcreate.jobhub.application.domain.exception.InvalidVerificationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidVerificationExceptionMapper implements ExceptionMapper<InvalidVerificationException> {

    @Override
    public Response toResponse(InvalidVerificationException ex) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse()
                        .error("Bad Request")
                        .message(ex.getMessage()))
                .build();
    }
}
