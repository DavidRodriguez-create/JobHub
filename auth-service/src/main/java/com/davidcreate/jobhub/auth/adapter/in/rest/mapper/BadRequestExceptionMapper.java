package com.davidcreate.jobhub.auth.adapter.in.rest.mapper;

import com.davidcreate.jobhub.auth.contract.model.ErrorResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps JAX-RS {@link BadRequestException} to a 400 response with the standard
 * {@code {error, message}} JSON body shape required by the contract's {@code ErrorResponse}.
 *
 * <p>Without this mapper, {@link BadRequestException} passes through
 * {@link GlobalExceptionMapper} as a raw {@link jakarta.ws.rs.WebApplicationException}
 * with no content-type and an empty body.
 */
@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {

    @Override
    public Response toResponse(BadRequestException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse()
                        .error("Bad Request")
                        .message(exception.getMessage() != null
                                ? exception.getMessage()
                                : "Invalid request parameter"))
                .build();
    }
}
