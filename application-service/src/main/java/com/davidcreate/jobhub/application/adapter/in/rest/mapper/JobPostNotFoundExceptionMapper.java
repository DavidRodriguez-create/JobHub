package com.davidcreate.jobhub.application.adapter.in.rest.mapper;

import com.davidcreate.jobhub.application.contract.model.ErrorResponse;
import com.davidcreate.jobhub.application.domain.exception.JobPostNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class JobPostNotFoundExceptionMapper
        implements ExceptionMapper<JobPostNotFoundException> {

    @Override
    public Response toResponse(JobPostNotFoundException ex) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse()
                        .error("Not Found")
                        .message(ex.getMessage()))
                .build();
    }
}
