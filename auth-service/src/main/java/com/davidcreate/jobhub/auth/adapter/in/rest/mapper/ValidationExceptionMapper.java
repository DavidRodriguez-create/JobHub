package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {

    @Override
    public Response toResponse(ValidationException ex) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse()
                        .error("Validation Failed")
                        .message(ex.getMessage()))
                .build();
    }
}
