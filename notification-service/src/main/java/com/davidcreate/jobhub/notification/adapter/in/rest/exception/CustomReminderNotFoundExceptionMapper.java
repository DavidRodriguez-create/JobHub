package com.davidcreate.jobhub.notification.adapter.in.rest.exception;

import com.davidcreate.jobhub.notification.domain.exception.CustomReminderNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CustomReminderNotFoundExceptionMapper implements ExceptionMapper<CustomReminderNotFoundException> {

    @Override
    public Response toResponse(CustomReminderNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new GenericExceptionMapper.ErrorBody("Not Found", exception.getMessage()))
                .build();
    }
}
