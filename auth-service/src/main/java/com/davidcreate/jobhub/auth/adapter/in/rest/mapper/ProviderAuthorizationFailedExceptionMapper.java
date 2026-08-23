package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.ProviderAuthorizationFailedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Distinct title from {@link UnverifiedProviderEmailExceptionMapper} (note 0.6, QA
 * doc): both map to 401, but the UI must be able to tell "the provider rejected the
 * code" (OAUTH-ERR-2) apart from "auto-link refused for account safety" (OAUTH-REFUSE).
 */
@Provider
public class ProviderAuthorizationFailedExceptionMapper
        implements ExceptionMapper<ProviderAuthorizationFailedException> {

    @Override
    public Response toResponse(ProviderAuthorizationFailedException ex) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse()
                        .error("Provider Authorization Failed")
                        .message(ex.getMessage()))
                .build();
    }
}
