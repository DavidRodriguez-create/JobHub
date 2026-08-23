package com.davidcreate.jobhub.notification.adapter.in.rest.exception;

import com.davidcreate.jobhub.notification.domain.exception.CustomReminderNotScheduledException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CustomReminderNotScheduledExceptionMapper implements ExceptionMapper<CustomReminderNotScheduledException> {

    @Override
    public Response toResponse(CustomReminderNotScheduledException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new GenericExceptionMapper.ErrorBody("Conflict", exception.getMessage()))
                .build();
    }
}
