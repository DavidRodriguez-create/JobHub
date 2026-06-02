package com.davidcreate.jobhub.crawler.adapter.in.rest.mapper;

import com.davidcreate.jobhub.crawler.adapter.in.rest.dto.ErrorResponse;
import com.davidcreate.jobhub.crawler.domain.exception.ResourceNotFoundException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ResourceNotFoundExceptionMapper
        implements ExceptionMapper<ResourceNotFoundException> {

    @Override
    public Response toResponse(ResourceNotFoundException ex) {

        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(
                        404,
                        ex.getMessage()))
                .build();
    }
}
