package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import com.davidcreate.jobhub.job.domain.exception.CompanyNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class CompanyNotFoundExceptionMapper implements ExceptionMapper<CompanyNotFoundException> {

    @Override
    public Response toResponse(CompanyNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of(
                        "error", "Not Found",
                        "message", exception.getMessage()))
                .build();
    }
}
