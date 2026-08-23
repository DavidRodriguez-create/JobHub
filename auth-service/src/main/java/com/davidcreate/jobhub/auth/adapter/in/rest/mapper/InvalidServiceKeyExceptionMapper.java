package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import com.davidcreate.jobhub.auth.domain.exception.InvalidServiceKeyException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidServiceKeyExceptionMapper implements ExceptionMapper<InvalidServiceKeyException> {

    @Override
    public Response toResponse(InvalidServiceKeyException ex) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse()
                        .error("Unauthorized")
                        .message(ex.getMessage()))
                .build();
    }
}
