package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import com.davidcreate.jobhub.job.domain.exception.SavedFilterLimitException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class SavedFilterLimitExceptionMapper implements ExceptionMapper<SavedFilterLimitException> {

    @Override
    public Response toResponse(SavedFilterLimitException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(
                        "error", "Bad Request",
                        "message", exception.getMessage()))
                .build();
    }
}
