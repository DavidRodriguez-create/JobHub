package com.davidcreate.jobhub.crawler.adapter.in.rest.mapper;

import com.davidcreate.jobhub.crawler.adapter.in.rest.dto.ErrorResponse;
import com.davidcreate.jobhub.crawler.contract.model.TriggerKind;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Without this, RESTEasy Reactive responds 404 (not 400) when the {@code {kind}} path
 * segment of {@code POST /internal/trigger-requests/{kind}/cancel} fails to convert to
 * the generated {@link TriggerKind} enum, because the failed conversion is treated as
 * "no matching resource" rather than a bad request (story #582, TR-12).
 */
@Provider
public class TriggerKindParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType != TriggerKind.class) {
            return null;
        }
        return (ParamConverter<T>) new TriggerKindParamConverter();
    }

    private static final class TriggerKindParamConverter implements ParamConverter<TriggerKind> {

        @Override
        public TriggerKind fromString(String value) {
            try {
                return TriggerKind.fromValue(value);
            } catch (IllegalArgumentException e) {
                throw new WebApplicationException(
                        Response.status(Response.Status.BAD_REQUEST)
                                .type(MediaType.APPLICATION_JSON)
                                .entity(new ErrorResponse("Bad Request", "Unknown kind: '" + value + "'"))
                                .build());
            }
        }

        @Override
        public String toString(TriggerKind value) {
            return value == null ? null : value.toString();
        }
    }
}
