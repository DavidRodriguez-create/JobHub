package com.davidcreate.jobhub.notification.adapter.in.rest.exception;

import com.davidcreate.jobhub.notification.domain.exception.ApplicationNotOwnedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApplicationNotOwnedExceptionMapper implements ExceptionMapper<ApplicationNotOwnedException> {

    @Override
    public Response toResponse(ApplicationNotOwnedException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new GenericExceptionMapper.ErrorBody("Not Found", exception.getMessage()))
                .build();
    }
}
