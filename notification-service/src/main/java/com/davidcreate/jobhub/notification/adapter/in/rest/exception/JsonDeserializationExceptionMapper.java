package com.davidcreate.jobhub.notification.adapter.in.rest.exception;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps a Jackson {@link MismatchedInputException} (e.g. a string value such as
 * {@code "ghostedAlert": "yes"} for a {@code Boolean} field) to a 400 response with the
 * standard {@code {error, message}} JSON body shape (BR-6 / TC-16b).
 *
 * <p>Quarkus REST Jackson registers a built-in {@code BuiltinMismatchedInputExceptionMapper}
 * that produces a {@code {objectName, attributeName, line, column, value}} body for this
 * exception. This mapper takes priority over that built-in (lower {@code @Priority} value
 * wins) so the contract's {@code ErrorResponse} shape ({@code error} + {@code message}) is
 * used consistently across all 4xx responses.
 */
@Provider
@Priority(1000)
public class JsonDeserializationExceptionMapper implements ExceptionMapper<MismatchedInputException> {

    @Override
    public Response toResponse(MismatchedInputException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new GenericExceptionMapper.ErrorBody(
                        "Bad Request",
                        exception.getOriginalMessage() != null
                                ? exception.getOriginalMessage()
                                : "Malformed request body"))
                .build();
    }
}
