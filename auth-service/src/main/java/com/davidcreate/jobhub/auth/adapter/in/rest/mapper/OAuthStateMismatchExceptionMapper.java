package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.OAuthStateMismatchException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class OAuthStateMismatchExceptionMapper implements ExceptionMapper<OAuthStateMismatchException> {

    @Override
    public Response toResponse(OAuthStateMismatchException ex) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse()
                        .error("Invalid OAuth State")
                        .message(ex.getMessage()))
                .build();
    }
}
