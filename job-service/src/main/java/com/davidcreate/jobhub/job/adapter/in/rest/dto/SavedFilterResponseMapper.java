package com.davidcreate.jobhub.job.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.contract.model.FilterValues;
import com.davidcreate.jobhub.job.contract.model.SavedFilterResponse;
import com.davidcreate.jobhub.job.domain.model.SavedFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps between the domain {@link SavedFilter} (which stores filters as an opaque
 * JSON string) and the contract models. The {@link ObjectMapper} is the request-scoped
 * Quarkus bean, so JSON (de)serialisation stays in the adapter layer.
 */
public final class SavedFilterResponseMapper {

    private SavedFilterResponseMapper() {}

    public static String toJson(FilterValues filters, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise filter values", e);
        }
    }

    public static SavedFilterResponse toResponse(SavedFilter filter, ObjectMapper objectMapper) {
        return new SavedFilterResponse()
                .id(filter.getId())
                .name(filter.getName())
                .filters(fromJson(filter.getFiltersJson(), objectMapper))
                .createdAt(filter.getCreatedAt())
                .updatedAt(filter.getUpdatedAt());
    }

    private static FilterValues fromJson(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, FilterValues.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored filter values", e);
        }
    }
}
