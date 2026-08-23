package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Maps JAX-RS {@link BadRequestException} to a 400 response with the standard
 * {@code {error, message}} JSON body shape required by the contract's {@code ErrorResponse}.
 *
 * <p>Without this mapper, {@link BadRequestException} passes through
 * {@link GenericExceptionMapper} as a raw {@link jakarta.ws.rs.WebApplicationException}
 * with no content-type and an empty body.
 */
@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {

    @Override
    public Response toResponse(BadRequestException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                        "error", "Bad Request",
                        "message", exception.getMessage() != null
                                ? exception.getMessage()
                                : "Invalid request parameter"))
                .build();
    }
}
