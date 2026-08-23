package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.LowFacetStampTtlProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component tests for stamp-driven facet-cache invalidation (Story #332 / ADR 0020):
 * a crawl write (FC332-C-13) and the decisive enrichment-only write (FC332-C-14), both
 * observed only after {@code job.search.facets.stamp.ttl} has next elapsed.
 *
 * <p>Runs in its own {@link LowFacetStampTtlProfile} (stamp.ttl=PT1S, cache.ttl=PT120S
 * so the per-entry TTL backstop never fires within the test window, isolating the
 * generation stamp as the sole invalidation mechanism under test), each test getting a
 * fresh drop-and-create DB seeded from {@code test-seeds.sql}.
 *
 * <p>Mutations use {@link QuarkusTransaction#requiringNew()} + injected
 * {@link EntityManager} (genuinely committed writes, visible to the separate connection
 * the REST call's own transaction runs in), never {@code @TestTransaction} (CLAUDE.md /
 * {@code AdminTriggerDedupeStatesComponentTest} precedent: {@code @TestTransaction}'s
 * rollback-only semantics are invisible across connections).
 */
@QuarkusTest
@TestProfile(LowFacetStampTtlProfile.class)
@DisplayName("GET /jobs/facets: stamp-driven invalidation (FC332-C-13/14)")
class JobFacetsInvalidationComponentTest {

    private static final String FACETS = "/jobs/facets";
    private static final UUID STRIPE_TARGET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    // Row 8 (test-seeds.sql): Stripe, career_level NULL, last_seen_at fixed in 2024;
    // the decisive AC-332-9 enrichment-only case backfills its career level in place.
    private static final UUID ROW_8_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Inject
    EntityManager entityManager;

    @Test
    @DisplayName("FC332-C-13 (AC-332-8/10): a new Stripe/Spain posting (crawl write) is reflected only after the stamp's next re-read")
    void crawlWriteInvalidatesAfterStampWindow() {
        int before = fetchStripeSpainCount();

        UUID newId = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                        "INSERT INTO crawler.job_post (id, target_id, title, url, description, content_hash,"
                                + " city, country, employment_type, career_level, languages, requirements,"
                                + " first_seen_at, last_seen_at)"
                                + " VALUES (:id, :targetId, :title, :url, :description, :hash, :city, :country,"
                                + " :employmentType, :careerLevel, ARRAY['English'], ARRAY[]::text[], NOW(), NOW())")
                        .setParameter("id", newId)
                        .setParameter("targetId", STRIPE_TARGET_ID)
                        .setParameter("title", "New Stripe Spain Role")
                        .setParameter("url", "https://example.com/jobs/new-stripe-" + newId)
                        .setParameter("description", "Newly crawled role")
                        .setParameter("hash", "hash-new-stripe-" + newId)
                        .setParameter("city", "Madrid")
                        .setParameter("country", "Spain")
                        .setParameter("employmentType", "full-time")
                        .setParameter("careerLevel", "senior")
                        .executeUpdate());

        // AC-332-10's documented MAY window: within stamp.ttl the pre-write value MAY still
        // legitimately be served. Deliberately no assertion here (non-assertable MAY clause).

        sleep(1200);

        int after = fetchStripeSpainCount();

        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("FC332-C-14 (AC-332-9, decisive): an enrichment-only write (career_level backfilled, enriched_at bumped, last_seen_at untouched) is reflected only after the stamp's next re-read")
    void enrichmentOnlyWriteInvalidatesAfterStampWindow() {
        int before = fetchSeniorCareerLevelCount();

        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                        "UPDATE crawler.job_post SET career_level = :careerLevel, enriched_at = NOW()"
                                + " WHERE id = :id")
                        .setParameter("careerLevel", "senior")
                        .setParameter("id", ROW_8_ID)
                        .executeUpdate());

        sleep(1200);

        int after = fetchSeniorCareerLevelCount();

        assertThat(after).isEqualTo(before + 1);
    }

    private int fetchStripeSpainCount() {
        return given().queryParam("location", "Spain").when().get(FACETS)
                .then().statusCode(200)
                .extract().jsonPath().getInt("companies.find { it.value == 'Stripe' }.count");
    }

    private int fetchSeniorCareerLevelCount() {
        return given().when().get(FACETS)
                .then().statusCode(200)
                .extract().jsonPath().getInt("careerLevels.find { it.value == 'senior' }.count");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
