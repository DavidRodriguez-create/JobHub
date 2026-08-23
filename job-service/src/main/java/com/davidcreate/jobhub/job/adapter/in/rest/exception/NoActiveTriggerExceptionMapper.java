package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import com.davidcreate.jobhub.job.domain.exception.NoActiveTriggerException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class NoActiveTriggerExceptionMapper implements ExceptionMapper<NoActiveTriggerException> {

    @Override
    public Response toResponse(NoActiveTriggerException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of(
                        "error", "No Active Trigger",
                        "message", exception.getMessage()))
                .build();
    }
}
