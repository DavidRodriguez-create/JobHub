package com.davidcreate.jobhub.application.adapter.in.rest.mapper;

import com.davidcreate.jobhub.application.contract.model.ErrorResponse;
import com.davidcreate.jobhub.application.domain.exception.DuplicateApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DuplicateApplicationExceptionMapper
        implements ExceptionMapper<DuplicateApplicationException> {

    @Override
    public Response toResponse(DuplicateApplicationException ex) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse()
                        .error("Conflict")
                        .message(ex.getMessage()))
                .build();
    }
}
