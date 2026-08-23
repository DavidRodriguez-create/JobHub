package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.model.CareerLevel;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.job.domain.model.JobSortOrder;
import com.davidcreate.jobhub.job.domain.model.PostedWithin;
import com.davidcreate.jobhub.job.domain.service.CountCacheKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache key is derived from the FILTER set only: page, size, and sort are
 * deliberately excluded (ADR 0018). No DB involved.
 */
@DisplayName("CountCacheKey Unit Tests")
class CountCacheKeyTest {

    private static JobSearchQuery.JobSearchQueryBuilder baseFilters() {
        return JobSearchQuery.builder()
                .keyword("java")
                .locations(List.of("Spain"))
                .languages(List.of("English"))
                .companies(List.of("Stripe"))
                .employmentTypes(List.of(EmploymentType.FULL_TIME))
                .careerLevels(List.of(CareerLevel.SENIOR))
                .compensationMin(60000)
                .compensationMax(100000)
                .postedWithin(PostedWithin.WEEK);
    }

    @Test
    @DisplayName("TC-331-8 (AC-331-12): differing only in page -> equal keys")
    void differingPageOnlyYieldsEqualKeys() {
        JobSearchQuery a = baseFilters().page(0).build();
        JobSearchQuery b = baseFilters().page(3).build();

        assertThat(CountCacheKey.from(a)).isEqualTo(CountCacheKey.from(b));
    }

    @Test
    @DisplayName("TC-331-9 (AC-331-12): differing only in size -> equal keys")
    void differingSizeOnlyYieldsEqualKeys() {
        JobSearchQuery a = baseFilters().size(20).build();
        JobSearchQuery b = baseFilters().size(50).build();

        assertThat(CountCacheKey.from(a)).isEqualTo(CountCacheKey.from(b));
    }

    @Test
    @DisplayName("TC-331-10 (AC-331-12): differing only in sort -> equal keys")
    void differingSortOnlyYieldsEqualKeys() {
        JobSearchQuery a = baseFilters().sort(JobSortOrder.NEWEST).build();
        JobSearchQuery b = baseFilters().sort(JobSortOrder.OLDEST).build();

        assertThat(CountCacheKey.from(a)).isEqualTo(CountCacheKey.from(b));
    }

    @Test
    @DisplayName("TC-331-11 (AC-331-11): differing keyword -> unequal keys")
    void differingKeywordYieldsUnequalKeys() {
        JobSearchQuery a = baseFilters().keyword("java").build();
        JobSearchQuery b = baseFilters().keyword("python").build();

        assertThat(CountCacheKey.from(a)).isNotEqualTo(CountCacheKey.from(b));
    }

    @Test
    @DisplayName("TC-331-12 (AC-331-11): differing locations -> unequal keys")
    void differingLocationsYieldsUnequalKeys() {
        JobSearchQuery a = baseFilters().locations(List.of("Spain")).build();
        JobSearchQuery b = baseFilters().locations(List.of("Spain", "Remote")).build();

        assertThat(CountCacheKey.from(a)).isNotEqualTo(CountCacheKey.from(b));
    }

    @ParameterizedTest(name = "TC-331-13 (AC-331-11): differing {0} -> unequal keys")
    @MethodSource("remainingDimensions")
    void differingRemainingDimensionYieldsUnequalKeys(String dimension,
                                                        JobSearchQuery a, JobSearchQuery b) {
        assertThat(CountCacheKey.from(a)).isNotEqualTo(CountCacheKey.from(b));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> remainingDimensions() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("languages",
                        baseFilters().languages(List.of("English")).build(),
                        baseFilters().languages(List.of("German")).build()),
                org.junit.jupiter.params.provider.Arguments.of("companies",
                        baseFilters().companies(List.of("Stripe")).build(),
                        baseFilters().companies(List.of("Spotify")).build()),
                org.junit.jupiter.params.provider.Arguments.of("employmentTypes",
                        baseFilters().employmentTypes(List.of(EmploymentType.FULL_TIME)).build(),
                        baseFilters().employmentTypes(List.of(EmploymentType.CONTRACT)).build()),
                org.junit.jupiter.params.provider.Arguments.of("careerLevels",
                        baseFilters().careerLevels(List.of(CareerLevel.SENIOR)).build(),
                        baseFilters().careerLevels(List.of(CareerLevel.JUNIOR)).build()),
                org.junit.jupiter.params.provider.Arguments.of("compensationMin",
                        baseFilters().compensationMin(60000).build(),
                        baseFilters().compensationMin(70000).build()),
                org.junit.jupiter.params.provider.Arguments.of("compensationMax",
                        baseFilters().compensationMax(100000).build(),
                        baseFilters().compensationMax(120000).build()),
                org.junit.jupiter.params.provider.Arguments.of("postedWithin",
                        baseFilters().postedWithin(PostedWithin.WEEK).build(),
                        baseFilters().postedWithin(PostedWithin.MONTH).build()));
    }

    @Test
    @DisplayName("TC-331-14 (foundational): same filters built twice independently -> equal keys (value, not identity)")
    void independentlyBuiltIdenticalFiltersYieldEqualKeys() {
        JobSearchQuery a = baseFilters().build();
        JobSearchQuery b = baseFilters().build();

        assertThat(a).isNotSameAs(b);
        assertThat(CountCacheKey.from(a)).isEqualTo(CountCacheKey.from(b));
    }
}
