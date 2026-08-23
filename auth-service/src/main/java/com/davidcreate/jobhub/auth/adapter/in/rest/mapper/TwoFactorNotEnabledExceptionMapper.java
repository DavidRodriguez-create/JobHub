package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorNotEnabledException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TwoFactorNotEnabledExceptionMapper implements ExceptionMapper<TwoFactorNotEnabledException> {

    @Override
    public Response toResponse(TwoFactorNotEnabledException ex) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse()
                        .error("Two-Factor Not Enabled")
                        .message(ex.getMessage()))
                .build();
    }
}
