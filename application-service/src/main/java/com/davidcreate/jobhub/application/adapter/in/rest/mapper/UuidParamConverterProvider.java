package com.davidcreate.jobhub.application.adapter.in.rest.mapper;

import com.davidcreate.jobhub.application.contract.model.ErrorResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.UUID;

/**
 * Without this, RESTEasy Reactive responds 404 (not 400) when a {@code @PathParam UUID}
 * segment fails to parse, because the failed conversion is treated as "no matching
 * resource" rather than a bad request. Converts that case into a {@link BadRequestException}
 * (400) with the standard {@code ErrorResponse} body, caught as-is by
 * {@link GlobalExceptionMapper}.
 */
@Provider
public class UuidParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType != UUID.class) {
            return null;
        }
        return (ParamConverter<T>) new UuidParamConverter();
    }

    private static final class UuidParamConverter implements ParamConverter<UUID> {

        @Override
        public UUID fromString(String value) {
            if (value == null) {
                return null;
            }
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                throw new WebApplicationException(
                        Response.status(Response.Status.BAD_REQUEST)
                                .type(MediaType.APPLICATION_JSON)
                                .entity(new ErrorResponse()
                                        .error("Bad Request")
                                        .message("Invalid UUID format: '" + value + "'"))
                                .build());
            }
        }

        @Override
        public String toString(UUID value) {
            return value == null ? null : value.toString();
        }
    }
}
