package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.TooManyRequestsException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TooManyRequestsExceptionMapper implements ExceptionMapper<TooManyRequestsException> {

    private static final int HTTP_429 = 429;

    @Override
    public Response toResponse(TooManyRequestsException ex) {
        return Response.status(HTTP_429)
                .entity(new ErrorResponse()
                        .error("Too Many Requests")
                        .message(ex.getMessage()))
                .build();
    }
}
