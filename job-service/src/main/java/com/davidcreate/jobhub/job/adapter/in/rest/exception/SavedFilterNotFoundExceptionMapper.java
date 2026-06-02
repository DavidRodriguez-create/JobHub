package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import com.davidcreate.jobhub.job.domain.exception.SavedFilterNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class SavedFilterNotFoundExceptionMapper implements ExceptionMapper<SavedFilterNotFoundException> {

    @Override
    public Response toResponse(SavedFilterNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of(
                        "error", "Not Found",
                        "message", exception.getMessage()))
                .build();
    }
}
