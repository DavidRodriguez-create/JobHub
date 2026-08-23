package com.davidcreate.jobhub.crawler.adapter.in.rest.mapper;

import com.davidcreate.jobhub.crawler.adapter.in.rest.dto.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps a bodyless 400 {@link WebApplicationException} (e.g. RESTEasy Reactive's Jackson
 * reader rejecting an unknown enum value such as {@code "kind":"bogus"}, before a resource
 * method even runs) to a 400 response with the standard {@code {error, message}} JSON body
 * shape. Without this mapper, such an exception passes through {@link GlobalExceptionMapper}
 * unchanged, producing a bodyless 400.
 *
 * <p>Any 400 that already carries an entity (built explicitly elsewhere, e.g. a
 * {@code ParamConverter} failure) is passed through unchanged, as is any non-400
 * {@link WebApplicationException}.
 */
@Provider
public class JsonDeserializationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        Response response = exception.getResponse();
        if (response.getStatus() == Response.Status.BAD_REQUEST.getStatusCode() && !response.hasEntity()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse("Bad Request", "Malformed request body"))
                    .build();
        }
        return response;
    }
}
