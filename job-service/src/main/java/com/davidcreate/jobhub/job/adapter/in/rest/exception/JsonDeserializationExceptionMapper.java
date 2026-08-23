package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Maps a {@link WebApplicationException} caused by a Jackson body-deserialization failure
 * (e.g. an unknown enum value such as {@code "kind":"bogus"} for {@code TriggerKind}) to a
 * 400 response with the standard {@code {error, message}} JSON body shape.
 *
 * <p>RESTEasy Reactive's Jackson reader throws a bare {@link WebApplicationException}
 * (status 400, no body/content-type) wrapping a {@link ValueInstantiationException} for
 * enum {@code @JsonCreator fromValue()} failures. Without this mapper, that exception
 * passes through {@link GenericExceptionMapper} unchanged, producing a bodyless 400.
 *
 * <p>Any other {@link WebApplicationException} (e.g. a {@code @RolesAllowed} policy
 * denial) is passed through unchanged, preserving {@link GenericExceptionMapper}'s
 * existing behaviour.
 */
@Provider
public class JsonDeserializationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        if (exception.getResponse().getStatus() == Response.Status.BAD_REQUEST.getStatusCode()
                && exception.getCause() instanceof ValueInstantiationException cause) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                            "error", "Bad Request",
                            "message", cause.getOriginalMessage() != null
                                    ? cause.getOriginalMessage()
                                    : "Malformed request body"))
                    .build();
        }
        return exception.getResponse();
    }
}
