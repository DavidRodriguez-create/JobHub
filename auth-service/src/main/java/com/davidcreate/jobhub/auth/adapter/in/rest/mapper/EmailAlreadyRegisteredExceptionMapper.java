package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.EmailAlreadyRegisteredException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class EmailAlreadyRegisteredExceptionMapper
        implements ExceptionMapper<EmailAlreadyRegisteredException> {

    @Override
    public Response toResponse(EmailAlreadyRegisteredException ex) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse()
                        .error("Email Already Registered")
                        .message(ex.getMessage()))
                .build();
    }
}
