package com.davidcreate.jobhub.notification.adapter.in.rest.exception;

import com.davidcreate.jobhub.notification.domain.exception.CustomReminderTriggerInPastException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CustomReminderTriggerInPastExceptionMapper implements ExceptionMapper<CustomReminderTriggerInPastException> {

    @Override
    public Response toResponse(CustomReminderTriggerInPastException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new GenericExceptionMapper.ErrorBody("Invalid Trigger Time", exception.getMessage()))
                .build();
    }
}
