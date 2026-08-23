package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.LocationNormalizationFacetProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * Story #408 (ADR 0021), section L: proves job-service's EXISTING (unchanged) facet-grouping
 * code collapses already-canonical values into one bucket per country, once crawler-service's
 * write path/backfill has done the canonicalization (QAE-408-C-04/05/06). No job-service
 * production code changes: this only seeds rows whose {@code country}/{@code city} VALUES are
 * written exactly as the crawler-service normalizer/backfill would produce them.
 *
 * <p>Runs in its own {@link LocationNormalizationFacetProfile} (fresh drop-and-create DB, {@code
 * test-seeds.sql} re-applied): the seeded rows below would otherwise perturb the exact
 * {@code totalElements}/per-country facet-count assertions every other component test class in
 * this module depends on in the shared default-profile instance.
 */
@QuarkusTest
@TestProfile(LocationNormalizationFacetProfile.class)
@DisplayName("Story #408 (ADR 0021): country facet collapses already-canonical values")
class LocationNormalizationFacetComponentTest {

    private static final String JOBS = "/jobs";
    private static final String FACETS = "/jobs/facets";
    private static final UUID STRIPE_TARGET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Inject
    EntityManager entityManager;

    private UUID insertJobPost(String city, String country) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                        "INSERT INTO crawler.job_post (id, target_id, title, url, description, content_hash,"
                                + " city, country, employment_type, career_level, languages, requirements,"
                                + " first_seen_at, last_seen_at)"
                                + " VALUES (:id, :targetId, :title, :url, :description, :hash, :city, :country,"
                                + " NULL, NULL, ARRAY[]::text[], ARRAY[]::text[], NOW(), NOW())")
                        .setParameter("id", id)
                        .setParameter("targetId", STRIPE_TARGET_ID)
                        .setParameter("title", "Story 408 Facet Role " + id)
                        .setParameter("url", "https://example.com/jobs/408-" + id)
                        .setParameter("description", "Story 408 facet-dedup fixture")
                        .setParameter("hash", "hash-408-facet-" + id)
                        .setParameter("city", city)
                        .setParameter("country", country)
                        .executeUpdate());
        return id;
    }

    private void insertPrimaryLocation(UUID jobPostId, String city, String country) {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                        "INSERT INTO crawler.job_post_location (job_post_id, country, city, is_primary, position)"
                                + " VALUES (:jobPostId, :country, :city, TRUE, 0)")
                        .setParameter("jobPostId", jobPostId)
                        .setParameter("country", country)
                        .setParameter("city", city)
                        .executeUpdate());
    }

    @Test
    @DisplayName("QAE-408-C-04: rows stored with the canonical 'United States' value collapse into one facet bucket (AC-408-36/37)")
    void canonicalUnitedStatesRowsCollapseIntoOneBucket() {
        insertJobPost(null, "United States");
        insertJobPost("Texas", "United States");

        given()
                .when().get(FACETS)
                .then()
                .statusCode(200)
                .body("locations.findAll { it.value == 'United States' }.size()", equalTo(1))
                .body("locations.find { it.value == 'United States' }.count", equalTo(2))
                .body("locations.value", not(hasItem("Us")))
                .body("locations.value", not(hasItem("Usa")))
                .body("locations.value", not(hasItem("us")))
                .body("locations.value", not(hasItem("Ca")))
                .body("locations.value", not(hasItem("Ny")))
                .body("locations.value", not(hasItem("Tx")))
                .body("locations.value", not(hasItem("Wa")))
                .body("locations.value", not(hasItem("es")))
                .body("locations.value", not(hasItem("fr")))
                .body("locations.value", not(hasItem("nl")))
                .body("locations.value", not(hasItem("ch")));
    }

    @Test
    @DisplayName("QAE-408-C-05: a preserved unmappable value never pollutes the country facet, and stays visible on the posting itself (AC-408-38)")
    void preservedUnmappableValueExcludedFromFacetButVisibleOnPosting() {
        UUID emeaRow = insertJobPost("Emea", null);
        insertPrimaryLocation(emeaRow, "Emea", null);

        given()
                .when().get(FACETS)
                .then()
                .statusCode(200)
                .body("locations.value", not(hasItem("Emea")))
                .body("locations.findAll { it.value == null }.size()", equalTo(0));

        given()
                .pathParam("id", emeaRow)
                .when().get(JOBS + "/{id}")
                .then()
                .statusCode(200)
                .body("location", equalTo("Emea"));
    }

    @Test
    @DisplayName("QAE-408-C-06 (regression lock): job-service groups by exact string only, an un-normalized legacy spelling stays its own bucket")
    void unNormalizedLegacySpellingStaysItsOwnBucket() {
        insertJobPost(null, "Usa");

        given()
                .when().get(FACETS)
                .then()
                .statusCode(200)
                .body("locations.find { it.value == 'Usa' }.count", equalTo(1))
                .body("locations.findAll { it.value == 'Usa' }.size()", equalTo(1));
    }
}
