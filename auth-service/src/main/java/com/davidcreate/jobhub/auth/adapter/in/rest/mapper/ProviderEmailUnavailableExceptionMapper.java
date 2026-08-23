package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.ProviderEmailUnavailableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ProviderEmailUnavailableExceptionMapper
        implements ExceptionMapper<ProviderEmailUnavailableException> {

    @Override
    public Response toResponse(ProviderEmailUnavailableException ex) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse()
                        .error("Provider Email Unavailable")
                        .message(ex.getMessage()))
                .build();
    }
}
