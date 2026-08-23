package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.TwoFactorVerificationRequiredException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TwoFactorVerificationRequiredExceptionMapper
        implements ExceptionMapper<TwoFactorVerificationRequiredException> {

    private static final int HTTP_422 = 422;

    @Override
    public Response toResponse(TwoFactorVerificationRequiredException ex) {
        return Response.status(HTTP_422)
                .entity(new ErrorResponse()
                        .error("Verification Required")
                        .message(ex.getMessage()))
                .build();
    }
}
