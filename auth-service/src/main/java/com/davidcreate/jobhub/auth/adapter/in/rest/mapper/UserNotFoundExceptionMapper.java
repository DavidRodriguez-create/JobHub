package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UserNotFoundExceptionMapper implements ExceptionMapper<UserNotFoundException> {

    @Override
    public Response toResponse(UserNotFoundException ex) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse()
                        .error("Not Found")
                        .message(ex.getMessage()))
                .build();
    }
}
