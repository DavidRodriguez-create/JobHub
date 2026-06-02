package com.davidcreate.jobhub.application.adapter.in.rest.mapper;

import com.davidcreate.jobhub.application.contract.model.ErrorResponse;
import com.davidcreate.jobhub.application.domain.exception.UserJobPostNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UserJobPostNotFoundExceptionMapper
        implements ExceptionMapper<UserJobPostNotFoundException> {

    @Override
    public Response toResponse(UserJobPostNotFoundException ex) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse()
                        .error("Not Found")
                        .message(ex.getMessage()))
                .build();
    }
}
