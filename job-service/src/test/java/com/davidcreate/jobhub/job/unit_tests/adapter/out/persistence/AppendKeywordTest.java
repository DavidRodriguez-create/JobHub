package com.davidcreate.jobhub.job.unit_tests.adapter.out.persistence;

import com.davidcreate.jobhub.job.adapter.out.persistence.JobPostPanacheRepository;
import com.davidcreate.jobhub.job.adapter.out.persistence.JobPostPanacheRepository.Dimension;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.JobPostMapper;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #52 (ADR 0005) — unit tests for {@code appendKeyword(jpql, params, query)}.
 *
 * <p>Verifies the JPQL fragment + parameter binding produced for the full-text-search
 * keyword filter, which replaced the LIKE-based implementation. Pure string/map
 * inspection — no SQL/DB round-trip. Mirrors the style of {@code AppendFiltersExceptTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Story #52: appendKeyword composes the FTS plainto_tsquery clause")
class AppendKeywordTest {

    private static final String FTS_FRAGMENT =
            "cast(sql('? @@ plainto_tsquery(''english'', ?)', j.searchVector, :keyword) as boolean) = true";

    @Mock
    JobPostMapper mapper;

    @Mock
    EntityManager em;

    JobPostPanacheRepository repo;

    @BeforeEach
    void setUp() {
        repo = new JobPostPanacheRepository(mapper, em);
    }

    @Test
    @DisplayName("T1-U1: keyword present → JPQL contains the sql(...) @@ plainto_tsquery(...) fragment")
    void keywordPresentAddsFtsFragment() {
        StringBuilder jpql = new StringBuilder("WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        JobSearchQuery query = JobSearchQuery.builder().keyword("java developer").build();

        repo.appendKeyword(jpql, params, query);

        assertThat(jpql.toString()).contains(FTS_FRAGMENT);
    }

    @Test
    @DisplayName("T1-U2: keyword present → params.get(\"keyword\") is the raw, non-lowercased, non-wrapped string")
    void keywordPresentBindsRawValue() {
        StringBuilder jpql = new StringBuilder("WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        JobSearchQuery query = JobSearchQuery.builder().keyword("Java Developer").build();

        repo.appendKeyword(jpql, params, query);

        assertThat(params).containsEntry("keyword", "Java Developer");
        assertThat(params.get("keyword")).isNotEqualTo("java developer");
        assertThat(params.get("keyword")).isNotEqualTo("%Java Developer%");
        assertThat(params.get("keyword")).isNotEqualTo("%java developer%");
    }

    @Test
    @DisplayName("T1-U3: null keyword → no clause appended, no param bound")
    void nullKeywordAppendsNothing() {
        StringBuilder jpql = new StringBuilder("WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        JobSearchQuery query = JobSearchQuery.builder().keyword(null).build();

        repo.appendKeyword(jpql, params, query);

        assertThat(jpql.toString()).isEqualTo("WHERE 1=1");
        assertThat(jpql.toString()).doesNotContain("@@");
        assertThat(jpql.toString()).doesNotContain("plainto_tsquery");
        assertThat(jpql.toString()).doesNotContain("searchVector");
        assertThat(params).doesNotContainKey("keyword");
    }

    @Test
    @DisplayName("T1-U4: blank/whitespace keyword → no clause appended, no param bound")
    void blankKeywordAppendsNothing() {
        StringBuilder jpql = new StringBuilder("WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        JobSearchQuery query = JobSearchQuery.builder().keyword("   ").build();

        repo.appendKeyword(jpql, params, query);

        assertThat(jpql.toString()).isEqualTo("WHERE 1=1");
        assertThat(params).doesNotContainKey("keyword");
    }

    @Test
    @DisplayName("T1-U5: empty string keyword → no clause appended, no param bound")
    void emptyKeywordAppendsNothing() {
        StringBuilder jpql = new StringBuilder("WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        JobSearchQuery query = JobSearchQuery.builder().keyword("").build();

        repo.appendKeyword(jpql, params, query);

        assertThat(jpql.toString()).isEqualTo("WHERE 1=1");
        assertThat(params).doesNotContainKey("keyword");
    }

    @Test
    @DisplayName("T1-U6: appendFiltersExcept includes the FTS keyword fragment + raw param for every Dimension")
    void appendFiltersExceptIncludesFtsKeywordForEveryDimension() {
        JobSearchQuery query = JobSearchQuery.builder().keyword("java").build();

        for (Dimension dim : Dimension.values()) {
            StringBuilder jpql = new StringBuilder("WHERE 1=1");
            Map<String, Object> params = new HashMap<>();

            repo.appendFiltersExcept(jpql, params, query, dim);

            assertThat(jpql.toString())
                    .as("FTS keyword fragment must be present when excluding %s", dim)
                    .contains("plainto_tsquery")
                    .contains("j.searchVector");
            assertThat(params)
                    .as("keyword param must be bound to the raw value when excluding %s", dim)
                    .containsEntry("keyword", "java");
        }
    }
}
