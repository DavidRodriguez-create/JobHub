package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import jakarta.annotation.Priority;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.Map;

@Provider
@Priority(Integer.MAX_VALUE)
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        LOG.error("Unhandled exception while processing request", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of(
                        "error", "Internal Server Error",
                        "message", "An unexpected error occurred"))
                .build();
    }
}
