package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Maps a Jackson body-deserialization {@link InvalidFormatException} (e.g. a
 * {@code website}/{@code logoUrl} value that is not a well-formed {@code java.net.URI},
 * story #430 AC-430-35) to the standard {@code {error, message}} shape (CLAUDE.md REST
 * layer convention).
 *
 * <p>Without this mapper, Quarkus's own built-in default for this exception type returns
 * a differently-shaped body ({@code objectName}/{@code attributeName}/{@code line}/
 * {@code column}/{@code value}), which is not the {@code ErrorResponse} shape every other
 * 400 in this service already returns (see also {@link JsonDeserializationExceptionMapper},
 * which handles the sibling case of an unrecognised enum value).
 */
@Provider
public class InvalidFormatExceptionMapper implements ExceptionMapper<InvalidFormatException> {

    @Override
    public Response toResponse(InvalidFormatException exception) {
        String field = exception.getPath().isEmpty()
                ? "request body"
                : exception.getPath().get(exception.getPath().size() - 1).getFieldName();
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                        "error", "Bad Request",
                        "message", "Malformed value for '" + field + "': " + exception.getValue()))
                .build();
    }
}
