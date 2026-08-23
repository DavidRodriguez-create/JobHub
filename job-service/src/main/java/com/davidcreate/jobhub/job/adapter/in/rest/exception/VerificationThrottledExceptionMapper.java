package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import com.davidcreate.jobhub.job.domain.exception.VerificationThrottledException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class VerificationThrottledExceptionMapper implements ExceptionMapper<VerificationThrottledException> {

    @Override
    public Response toResponse(VerificationThrottledException exception) {
        return Response.status(Response.Status.TOO_MANY_REQUESTS)
                .entity(Map.of(
                        "error", "Too Many Requests",
                        "message", exception.getMessage()))
                .build();
    }
}
