package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import com.davidcreate.jobhub.job.domain.exception.TriggerInProgressException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class TriggerInProgressExceptionMapper implements ExceptionMapper<TriggerInProgressException> {

    @Override
    public Response toResponse(TriggerInProgressException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of(
                        "error", "Trigger In Progress",
                        "message", exception.getMessage()))
                .build();
    }
}
