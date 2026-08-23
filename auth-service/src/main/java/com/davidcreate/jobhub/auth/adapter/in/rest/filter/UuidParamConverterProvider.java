package com.davidcreate.jobhub.auth.adapter.in.rest.filter;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.UUID;

/**
 * Overrides RESTEasy Reactive's default {@code UUID} query/path-param conversion.
 *
 * <p>By default, an {@link IllegalArgumentException} thrown by {@code UUID.fromString}
 * during param conversion is treated by the reactive router as "no matching route" and
 * surfaces as a bare 404. For endpoints like {@code GET /internal/users/emails} that take
 * a {@code List<UUID>} query parameter, an invalid UUID must be a 400 (ADR 0008 / TC-41),
 * so this converter throws {@link BadRequestException} instead.
 */
@Provider
public class UuidParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType != UUID.class) {
            return null;
        }
        return (ParamConverter<T>) new ParamConverter<UUID>() {
            @Override
            public UUID fromString(String value) {
                if (value == null) {
                    return null;
                }
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid UUID value: " + value, e);
                }
            }

            @Override
            public String toString(UUID value) {
                return value == null ? null : value.toString();
            }
        };
    }
}
