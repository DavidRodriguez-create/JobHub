package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.LowFacetCacheTtlProfile;
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
 * Component test for the per-entry {@code cache.ttl} backstop (Story #332 / ADR 0020,
 * AC-332-11): a facet-affecting write that bumps neither {@code last_seen_at} nor
 * {@code enriched_at} (simulating {@code normalizeLanguagesBatch} maintenance) is
 * invisible to the generation stamp, and only observed once the per-entry
 * {@code cache.ttl} backstop expires the cached entry.
 *
 * <p>Runs in its own {@link LowFacetCacheTtlProfile} (cache.ttl=PT5S, stamp.ttl=PT120S
 * so the stamp provably never re-reads within the test window, isolating the entry TTL
 * as the sole invalidation mechanism under test).
 */
@QuarkusTest
@TestProfile(LowFacetCacheTtlProfile.class)
@DisplayName("GET /jobs/facets: per-entry cache.ttl backstop (FC332-C-15)")
class JobFacetsBackstopComponentTest {

    private static final String FACETS = "/jobs/facets";
    private static final UUID ROW_1_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Inject
    EntityManager entityManager;

    @Test
    @DisplayName("FC332-C-15 (AC-332-11): a languages rewrite that bumps neither timestamp is unseen immediately, seen once cache.ttl expires")
    void backstopExpiresEntryIndependentOfStamp() {
        int before = fetchGermanLanguageCount();

        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                        "UPDATE crawler.job_post SET languages = ARRAY['English','Spanish','German'] WHERE id = :id")
                        .setParameter("id", ROW_1_ID)
                        .executeUpdate());

        int immediatelyAfter = fetchGermanLanguageCount();
        assertThat(immediatelyAfter).isEqualTo(before);

        sleep(6000);

        int afterBackstop = fetchGermanLanguageCount();
        assertThat(afterBackstop).isEqualTo(before + 1);
    }

    private int fetchGermanLanguageCount() {
        return given().when().get(FACETS)
                .then().statusCode(200)
                .extract().jsonPath().getInt("languages.find { it.value == 'German' }.count");
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
