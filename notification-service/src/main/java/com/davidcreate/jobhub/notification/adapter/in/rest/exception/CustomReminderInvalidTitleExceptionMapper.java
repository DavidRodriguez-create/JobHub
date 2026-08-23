package com.davidcreate.jobhub.notification.adapter.in.rest.exception;

import com.davidcreate.jobhub.notification.domain.exception.CustomReminderInvalidTitleException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CustomReminderInvalidTitleExceptionMapper implements ExceptionMapper<CustomReminderInvalidTitleException> {

    @Override
    public Response toResponse(CustomReminderInvalidTitleException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new GenericExceptionMapper.ErrorBody("Invalid Title", exception.getMessage()))
                .build();
    }
}
