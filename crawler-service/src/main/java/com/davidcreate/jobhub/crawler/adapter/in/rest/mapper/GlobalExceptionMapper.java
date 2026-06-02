package com.davidcreate.jobhub.crawler.adapter.in.rest.mapper;

import com.davidcreate.jobhub.crawler.adapter.in.rest.dto.ErrorResponse;

import jakarta.annotation.Priority;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
@Priority(Integer.MAX_VALUE)
public class GlobalExceptionMapper
        implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Throwable ex) {
        if (ex instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        LOG.errorf(ex, "Unhandled exception: %s", ex.getMessage());
        return Response.serverError()
                .entity(new ErrorResponse(
                        500,
                        "Internal server error"))
                .build();
    }

}
