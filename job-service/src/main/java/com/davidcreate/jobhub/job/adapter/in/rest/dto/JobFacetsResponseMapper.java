package com.davidcreate.jobhub.job.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.contract.model.FacetValue;

import java.util.List;

/**
 * Maps the domain {@link com.davidcreate.jobhub.job.domain.model.JobFacets} into the
 * generated contract {@link com.davidcreate.jobhub.job.contract.model.JobFacets} model.
 */
public final class JobFacetsResponseMapper {

    private JobFacetsResponseMapper() {
    }

    public static com.davidcreate.jobhub.job.contract.model.JobFacets toResponse(
            com.davidcreate.jobhub.job.domain.model.JobFacets facets) {
        return new com.davidcreate.jobhub.job.contract.model.JobFacets()
                .companies(toFacetValues(facets.companies()))
                .locations(toFacetValues(facets.locations()))
                .languages(toFacetValues(facets.languages()))
                .employmentTypes(toFacetValues(facets.employmentTypes()))
                .compensationMin(facets.compensationMin())
                .compensationMax(facets.compensationMax());
    }

    private static List<FacetValue> toFacetValues(List<com.davidcreate.jobhub.job.domain.model.FacetValue> values) {
        return values.stream()
                .map(v -> new FacetValue().value(v.value()).count(v.count()))
                .toList();
    }
}
