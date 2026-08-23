package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import com.davidcreate.jobhub.job.domain.exception.CrawlerUnavailableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class CrawlerUnavailableExceptionMapper implements ExceptionMapper<CrawlerUnavailableException> {

    @Override
    public Response toResponse(CrawlerUnavailableException exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of(
                        "error", "Crawler Unavailable",
                        "message", exception.getMessage()))
                .build();
    }
}
