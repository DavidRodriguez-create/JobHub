package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.AdminCompanyTagsOnPostingTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Story #430 (PDA section F): curated {@code company.tags} reflected on {@code GET /jobs}
 * and {@code GET /jobs/{id}}. Only the curation {@code PUT} step needs the admin role; the
 * postings themselves are read anonymously ({@code GET /jobs}/{@code GET /jobs/{id}} are
 * public endpoints).
 *
 * <p>Its own isolated {@code @TestProfile}: this class seeds two brand-new {@code job_post}
 * rows for "Tags Demo Co" (the company row itself already lives in the shared
 * {@code test-seeds.sql}), and several pre-existing tests hardcode the unfiltered
 * job-posting total against the shared no-{@code @TestProfile} container.
 */
@QuarkusTest
@TestProfile(AdminCompanyTagsOnPostingTestProfile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Admin Company Tags On Posting Component Tests (Story #430)")
class AdminCompanyTagsOnPostingComponentTest {

    private static final String COMPANIES = "/jobs/admin/companies";
    private static final String JOBS = "/jobs";
    private static final String TAGS_DEMO_COMPANY_ID = "43000000-0000-0000-0000-000000000001";
    private static final String TAGS_DEMO_TARGET_ID = "43000000-0000-0000-0000-000000000002";
    private static final String TAGS_DEMO_JOB_A_ID = "43000000-0000-0000-0000-000000000003";
    private static final String TAGS_DEMO_JOB_B_ID = "43000000-0000-0000-0000-000000000004";
    private static final String ACME_ONLY_JOB_ID = "00000000-0000-0000-0000-000000000014";

    @Inject
    DataSource dataSource;

    @BeforeAll
    void seedTagsDemoPostings() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO crawler.pull_target (id, source_type, company_name, company_id) VALUES "
                            + "('" + TAGS_DEMO_TARGET_ID + "', 'greenhouse', 'Tags Demo Co', "
                            + "'" + TAGS_DEMO_COMPANY_ID + "')");
            statement.execute(
                    "INSERT INTO crawler.job_post (id, target_id, title, url, first_seen_at, last_seen_at) "
                            + "VALUES ('" + TAGS_DEMO_JOB_A_ID + "', '" + TAGS_DEMO_TARGET_ID + "', "
                            + "'Tags Demo Backend Engineer', 'https://example.com/jobs/tags-demo-1', NOW(), NOW())");
            statement.execute(
                    "INSERT INTO crawler.job_post (id, target_id, title, url, first_seen_at, last_seen_at) "
                            + "VALUES ('" + TAGS_DEMO_JOB_B_ID + "', '" + TAGS_DEMO_TARGET_ID + "', "
                            + "'Tags Demo Frontend Engineer', 'https://example.com/jobs/tags-demo-2', NOW(), NOW())");
        }
    }

    // ── QAE-430-T-01 (AC-430-27) ────────────────────────────────────────────────

    @Test
    @TestSecurity(user = "43000000-9993-0000-0000-000000000001", roles = "admin")
    @DisplayName("QAE-430-T-01: curated tags appear on company.tags for EVERY posting of that company")
    void curatedTagsAppearOnEveryPosting() {
        List<String> tags = List.of("remote-first", "b2b", "series-c");
        Map<String, Object> update = new HashMap<>();
        update.put("tags", tags);

        given().contentType(ContentType.JSON).body(update)
                .when().put(COMPANIES + "/" + TAGS_DEMO_COMPANY_ID)
                .then().statusCode(200).body("tags", equalTo(tags));

        given().when().get(JOBS + "/" + TAGS_DEMO_JOB_A_ID)
                .then().statusCode(200).body("company.tags", equalTo(tags));

        given().when().get(JOBS + "/" + TAGS_DEMO_JOB_B_ID)
                .then().statusCode(200).body("company.tags", equalTo(tags));

        var list = given().queryParam("keyword", "Tags Demo")
                .when().get(JOBS)
                .then().statusCode(200).extract().jsonPath();
        List<?> content = list.getList("content");
        org.assertj.core.api.Assertions.assertThat(content).hasSize(2);
        for (int i = 0; i < content.size(); i++) {
            org.assertj.core.api.Assertions.assertThat(list.getList("content[" + i + "].company.tags", String.class))
                    .isEqualTo(tags);
        }
    }

    // ── QAE-430-T-02 (AC-430-28) ────────────────────────────────────────────────

    @Test
    @TestSecurity(user = "43000000-9993-0000-0000-000000000002", roles = "admin")
    @DisplayName("QAE-430-T-02: tags is identical on list and detail; only description is nulled on the list")
    void tagsIdenticalOnListAndDetailUnlikeDescription() {
        List<String> tags = List.of("remote-first", "b2b");
        Map<String, Object> update = new HashMap<>();
        update.put("tags", tags);
        update.put("description", "A fixture company used only for the curated-tags proof.");

        given().contentType(ContentType.JSON).body(update)
                .when().put(COMPANIES + "/" + TAGS_DEMO_COMPANY_ID)
                .then().statusCode(200);

        var detail = given().when().get(JOBS + "/" + TAGS_DEMO_JOB_A_ID)
                .then().statusCode(200).extract().jsonPath();

        var list = given().queryParam("keyword", "Tags Demo Backend")
                .when().get(JOBS)
                .then().statusCode(200).extract().jsonPath();

        org.assertj.core.api.Assertions.assertThat(detail.getList("company.tags", String.class))
                .isEqualTo(tags);
        org.assertj.core.api.Assertions.assertThat(
                        list.getList("content[0].company.tags", String.class))
                .isEqualTo(tags);
        org.assertj.core.api.Assertions.assertThat(detail.getString("company.description"))
                .isEqualTo("A fixture company used only for the curated-tags proof.");
        org.assertj.core.api.Assertions.assertThat(list.getString("content[0].company.description"))
                .isNull();
    }

    // ── QAE-430-T-03 (AC-430-30, backend half) ──────────────────────────────────

    @Test
    @DisplayName("QAE-430-T-03: a company with no curated tags returns tags:null on its posting, never []")
    void uncuratedCompanyReturnsNullTagsNeverEmptyArray() {
        given().when().get(JOBS + "/" + ACME_ONLY_JOB_ID)
                .then()
                .statusCode(200)
                .body("company.name", equalTo("Acme Only"))
                .body("company.tags", nullValue());
    }
}
