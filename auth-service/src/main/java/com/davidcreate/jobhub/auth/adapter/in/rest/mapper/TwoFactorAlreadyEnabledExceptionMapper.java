package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorAlreadyEnabledException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TwoFactorAlreadyEnabledExceptionMapper implements ExceptionMapper<TwoFactorAlreadyEnabledException> {

    @Override
    public Response toResponse(TwoFactorAlreadyEnabledException ex) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse()
                        .error("Two-Factor Already Enabled")
                        .message(ex.getMessage()))
                .build();
    }
}
