package com.davidcreate.jobhub.application.adapter.in.rest.mapper;

import com.davidcreate.jobhub.application.contract.model.ErrorResponse;
import com.davidcreate.jobhub.application.domain.exception.AlreadyTerminalException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AlreadyTerminalExceptionMapper
        implements ExceptionMapper<AlreadyTerminalException> {

    @Override
    public Response toResponse(AlreadyTerminalException ex) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse()
                        .error("Conflict")
                        .message(ex.getMessage()))
                .build();
    }
}
