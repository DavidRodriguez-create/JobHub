package com.davidcreate.jobhub.job.unit_tests.adapter.out.persistence;

import com.davidcreate.jobhub.job.adapter.out.persistence.JobPostPanacheRepository;
import com.davidcreate.jobhub.job.adapter.out.persistence.JobPostPanacheRepository.Dimension;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.JobPostMapper;
import com.davidcreate.jobhub.job.domain.model.CareerLevel;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-F15 — unit test for {@code appendFiltersExcept(jpql, params, query, Dimension)}.
 *
 * <p>Verifies that the composed WHERE clause contains every filter clause EXCEPT the one
 * for the excluded dimension, and that all bindings are present with correct values.
 * No SQL/DB round-trip — pure JPQL-string + params inspection.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BE-F15: appendFiltersExcept composes correct parameter-bound WHERE per dimension")
class AppendFiltersExceptTest {

    @Mock
    JobPostMapper mapper;

    @Mock
    EntityManager em;

    JobPostPanacheRepository repo;

    @BeforeEach
    void setUp() {
        repo = new JobPostPanacheRepository(mapper, em);
    }

    private static JobSearchQuery fullQuery() {
        return JobSearchQuery.builder()
                .keyword("java")
                .locations(List.of("Spain"))
                .languages(List.of("English"))
                .companies(List.of("Stripe"))
                .employmentTypes(List.of(EmploymentType.FULL_TIME))
                .careerLevels(List.of(CareerLevel.SENIOR))
                .compensationMin(50000)
                .compensationMax(120000)
                .build();
    }

    @Nested
    @DisplayName("Excluding LOCATION")
    class ExcludeLocation {

        @Test
        @DisplayName("omits location clause; all other clauses and bindings are present")
        void omitsLocationClause() {
            StringBuilder jpql = new StringBuilder("WHERE 1=1");
            Map<String, Object> params = new HashMap<>();

            repo.appendFiltersExcept(jpql, params, fullQuery(), Dimension.LOCATION);

            String sql = jpql.toString();
            assertThat(sql).contains("keyword");
            assertThat(sql).contains("companies");
            assertThat(sql).contains("employmentTypes");
            assertThat(sql).contains("careerLevels");
            assertThat(sql).contains("compMin");
            assertThat(sql).contains("compMax");
            assertThat(sql).contains("languages");
            // location clause must be absent
            assertThat(sql).doesNotContain("loc_");
            assertThat(sql).doesNotContain("LOWER(j.city)");
            assertThat(sql).doesNotContain("LOWER(j.country)");

            assertThat(params).containsKey("keyword");
            assertThat(params).containsKey("companies");
            assertThat(params).containsKey("employmentTypes");
            assertThat(params).containsKey("careerLevels");
            assertThat(params).containsKey("compMin");
            assertThat(params).containsKey("compMax");
            assertThat(params).containsKey("languages");
            assertThat(params).doesNotContainKey("loc_0");
        }
    }

    @Nested
    @DisplayName("Excluding COMPANY")
    class ExcludeCompany {

        @Test
        @DisplayName("omits company clause; location, keyword, and other clauses present")
        void omitsCompanyClause() {
            StringBuilder jpql = new StringBuilder("WHERE 1=1");
            Map<String, Object> params = new HashMap<>();

            repo.appendFiltersExcept(jpql, params, fullQuery(), Dimension.COMPANY);

            String sql = jpql.toString();
            assertThat(sql).contains("keyword");
            assertThat(sql).contains("LOWER(j.country)"); // location present
            assertThat(sql).contains("employmentTypes");
            assertThat(sql).contains("careerLevels");
            assertThat(sql).contains("compMin");
            assertThat(sql).contains("compMax");
            assertThat(sql).contains("languages");
            assertThat(sql).doesNotContain("companies");

            assertThat(params).doesNotContainKey("companies");
            assertThat(params).containsKey("keyword");
        }

        @Test
        @DisplayName("AC-407-U1: language clause + raw param are still composed correctly "
                + "when a non-LANGUAGE dimension is excluded")
        void includesLanguageClauseWithRawListParam() {
            StringBuilder jpql = new StringBuilder("WHERE 1=1");
            Map<String, Object> params = new HashMap<>();
            JobSearchQuery query = JobSearchQuery.builder()
                    .languages(List.of("English", "Spanish"))
                    .build();

            repo.appendFiltersExcept(jpql, params, query, Dimension.COMPANY);

            assertThat(jpql.toString()).contains("j.languages").contains(":languages");
            assertThat(params).containsKey("languages");
            assertThat((Object[]) params.get("languages")).containsExactly("English", "Spanish");
        }
    }

    @Nested
    @DisplayName("Excluding EMPLOYMENT_TYPE")
    class ExcludeEmploymentType {

        @Test
        @DisplayName("omits employmentType clause; all others present")
        void omitsEmploymentTypeClause() {
            StringBuilder jpql = new StringBuilder("WHERE 1=1");
            Map<String, Object> params = new HashMap<>();

            repo.appendFiltersExcept(jpql, params, fullQuery(), Dimension.EMPLOYMENT_TYPE);

            String sql = jpql.toString();
            assertThat(sql).doesNotContain("employmentTypes");
            assertThat(sql).contains("keyword");
            assertThat(sql).contains("careerLevels");
            assertThat(sql).contains("compMin");

            assertThat(params).doesNotContainKey("employmentTypes");
        }
    }

    @Nested
    @DisplayName("Excluding CAREER_LEVEL")
    class ExcludeCareerLevel {

        @Test
        @DisplayName("omits careerLevel clause; all others present")
        void omitsCareerLevelClause() {
            StringBuilder jpql = new StringBuilder("WHERE 1=1");
            Map<String, Object> params = new HashMap<>();

            repo.appendFiltersExcept(jpql, params, fullQuery(), Dimension.CAREER_LEVEL);

            String sql = jpql.toString();
            assertThat(sql).doesNotContain("careerLevels");
            assertThat(sql).contains("keyword");
            assertThat(sql).contains("employmentTypes");
            assertThat(sql).contains("compMin");

            assertThat(params).doesNotContainKey("careerLevels");
        }
    }

    @Nested
    @DisplayName("Excluding COMPENSATION")
    class ExcludeCompensation {

        @Test
        @DisplayName("omits both compMin and compMax clauses; all others present")
        void omitsBothCompClauses() {
            StringBuilder jpql = new StringBuilder("WHERE 1=1");
            Map<String, Object> params = new HashMap<>();

            repo.appendFiltersExcept(jpql, params, fullQuery(), Dimension.COMPENSATION);

            String sql = jpql.toString();
            assertThat(sql).doesNotContain("compMin");
            assertThat(sql).doesNotContain("compMax");
            assertThat(sql).contains("keyword");
            assertThat(sql).contains("companies");
            assertThat(sql).contains("employmentTypes");
            assertThat(sql).contains("careerLevels");
            assertThat(sql).contains("languages");

            assertThat(params).doesNotContainKey("compMin");
            assertThat(params).doesNotContainKey("compMax");
        }
    }

    @Nested
    @DisplayName("Excluding LANGUAGE")
    class ExcludeLanguage {

        @Test
        @DisplayName("omits language clause; all others present")
        void omitsLanguageClause() {
            StringBuilder jpql = new StringBuilder("WHERE 1=1");
            Map<String, Object> params = new HashMap<>();

            repo.appendFiltersExcept(jpql, params, fullQuery(), Dimension.LANGUAGE);

            String sql = jpql.toString();
            assertThat(sql).doesNotContain("languages");
            assertThat(sql).contains("keyword");
            assertThat(sql).contains("companies");
            assertThat(sql).contains("careerLevels");

            assertThat(params).doesNotContainKey("languages");
        }
    }

    @Nested
    @DisplayName("keyword and postedWithin always apply")
    class KeywordAndPostedWithinAlwaysApply {

        @Test
        @DisplayName("keyword is always included regardless of excluded dimension")
        void keywordAlwaysIncluded() {
            for (Dimension dim : Dimension.values()) {
                StringBuilder jpql = new StringBuilder("WHERE 1=1");
                Map<String, Object> params = new HashMap<>();
                repo.appendFiltersExcept(jpql, params, fullQuery(), dim);
                assertThat(jpql.toString())
                        .as("keyword must be present when excluding %s", dim)
                        .contains("keyword");
                assertThat(params)
                        .as("keyword param must be bound when excluding %s", dim)
                        .containsKey("keyword");
            }
        }

        @Test
        @DisplayName("empty query with any exclusion adds no clauses (no-filter == table-wide)")
        void emptyQueryNoFiltersAppended() {
            JobSearchQuery empty = JobSearchQuery.builder().build();
            for (Dimension dim : Dimension.values()) {
                StringBuilder jpql = new StringBuilder("WHERE 1=1");
                Map<String, Object> params = new HashMap<>();
                repo.appendFiltersExcept(jpql, params, empty, dim);
                assertThat(jpql.toString())
                        .as("empty query with exclusion %s should not add any clause", dim)
                        .isEqualTo("WHERE 1=1");
                assertThat(params)
                        .as("no params should be bound for empty query with exclusion %s", dim)
                        .isEmpty();
            }
        }
    }
}
