package com.davidcreate.jobhub.crawler.adapter.in.rest.mapper;

import com.davidcreate.jobhub.crawler.adapter.in.rest.dto.ErrorResponse;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * A malformed field value (e.g. {@code "requestedBy":"not-a-uuid"}) surfaces as a
 * {@link MismatchedInputException} thrown directly by Jackson, not wrapped in a
 * {@code WebApplicationException}: {@link JsonDeserializationExceptionMapper} never sees it.
 * Quarkus registers its own unannotated {@code ExceptionMapper<MismatchedInputException>}
 * (default priority {@link Priorities#USER}) that, outside dev/test mode, does not carry the
 * project's {@code {error, message}} shape either. A lower {@link Priority} value here wins
 * the tie for the same exact exception type, so this mapper is selected instead.
 *
 * <p>The response body is a stable, static message, never {@link MismatchedInputException#getMessage()}:
 * Jackson's own message embeds the model class name and the raw submitted value, which is the
 * same class of leak this project's log masking convention exists to prevent.
 */
@Provider
@Priority(Priorities.USER - 1000)
public class MismatchedJsonInputExceptionMapper implements ExceptionMapper<MismatchedInputException> {

    @Override
    public Response toResponse(MismatchedInputException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("Bad Request", "Malformed request body"))
                .build();
    }
}
