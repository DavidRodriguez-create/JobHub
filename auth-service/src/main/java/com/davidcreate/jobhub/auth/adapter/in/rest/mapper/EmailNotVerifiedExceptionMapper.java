package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.EmailNotVerifiedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class EmailNotVerifiedExceptionMapper implements ExceptionMapper<EmailNotVerifiedException> {

    @Override
    public Response toResponse(EmailNotVerifiedException ex) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse()
                        .error("Email Not Verified")
                        .message(ex.getMessage()))
                .build();
    }
}
