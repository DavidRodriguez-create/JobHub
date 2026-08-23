package com.davidcreate.jobhub.notification.adapter.in.rest.exception;

import jakarta.annotation.Priority;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
@Priority(Integer.MAX_VALUE)
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        LOG.errorf(exception, "Unhandled exception");
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorBody("Internal Server Error", exception.getMessage()))
                .build();
    }

    public record ErrorBody(String error, String message) {}
}
