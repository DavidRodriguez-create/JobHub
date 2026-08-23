package com.davidcreate.jobhub.notification.adapter.in.rest.exception;

import com.davidcreate.jobhub.notification.domain.exception.CustomReminderInvalidChannelsException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CustomReminderInvalidChannelsExceptionMapper implements ExceptionMapper<CustomReminderInvalidChannelsException> {

    @Override
    public Response toResponse(CustomReminderInvalidChannelsException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new GenericExceptionMapper.ErrorBody("Invalid Channels", exception.getMessage()))
                .build();
    }
}
