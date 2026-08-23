package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import com.davidcreate.jobhub.job.domain.exception.VerificationRequiredException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class VerificationRequiredExceptionMapper implements ExceptionMapper<VerificationRequiredException> {

    @Override
    public Response toResponse(VerificationRequiredException exception) {
        return Response.status(422)
                .entity(Map.of(
                        "error", "Verification Required",
                        "message", exception.getMessage()))
                .build();
    }
}
