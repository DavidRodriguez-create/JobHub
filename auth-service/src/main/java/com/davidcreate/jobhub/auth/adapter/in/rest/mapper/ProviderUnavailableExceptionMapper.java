package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.ProviderUnavailableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ProviderUnavailableExceptionMapper implements ExceptionMapper<ProviderUnavailableException> {

    @Override
    public Response toResponse(ProviderUnavailableException ex) {
        return Response.status(Response.Status.BAD_GATEWAY)
                .entity(new ErrorResponse()
                        .error("Provider Unavailable")
                        .message(ex.getMessage()))
                .build();
    }
}
