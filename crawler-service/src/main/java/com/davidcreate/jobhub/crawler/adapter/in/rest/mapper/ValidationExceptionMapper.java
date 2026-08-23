package com.davidcreate.jobhub.crawler.adapter.in.rest.mapper;

import com.davidcreate.jobhub.crawler.adapter.in.rest.dto.ErrorResponse;
import com.davidcreate.jobhub.crawler.domain.exception.ValidationException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ValidationExceptionMapper
        implements ExceptionMapper<ValidationException> {

    @Override
    public Response toResponse(ValidationException ex) {

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(
                        "Validation Error",
                        ex.getMessage()))
                .build();
    }
}
