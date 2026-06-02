package com.davidcreate.jobhub.crawler.adapter.in.rest.mapper;

import com.davidcreate.jobhub.crawler.adapter.in.rest.dto.ErrorResponse;
import com.davidcreate.jobhub.crawler.domain.exception.ConflictException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConflictExceptionMapper
        implements ExceptionMapper<ConflictException> {

    @Override
    public Response toResponse(ConflictException ex) {

        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(
                        409,
                        ex.getMessage()))
                .build();
    }
}
