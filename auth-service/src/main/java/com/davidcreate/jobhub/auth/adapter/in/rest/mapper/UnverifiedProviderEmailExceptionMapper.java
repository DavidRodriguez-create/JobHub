package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.UnverifiedProviderEmailException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Distinct title from {@link ProviderAuthorizationFailedExceptionMapper} (note 0.6,
 * QA doc): both map to 401, but this one is the account-safety refusal (OAUTH-REFUSE),
 * not a technical failure.
 */
@Provider
public class UnverifiedProviderEmailExceptionMapper
        implements ExceptionMapper<UnverifiedProviderEmailException> {

    @Override
    public Response toResponse(UnverifiedProviderEmailException ex) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse()
                        .error("Account Linking Refused")
                        .message(ex.getMessage()))
                .build();
    }
}
