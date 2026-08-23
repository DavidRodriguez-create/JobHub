package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostLocationEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.PullTargetEntity;
import com.davidcreate.jobhub.crawler.component_tests.support.LocationBackfillIsolationProfile;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.LocationBatchResult;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #408 (ADR 0021), section K: real-row backfill behaviour against DevServices Postgres.
 * Seeds legacy "dirty" rows the same way {@code JobPostLocationBackfillComponentTest} does
 * (direct entity persistence, not {@code test-seeds.sql}) since these fixtures are deliberately
 * distinct from the baseline seed.
 *
 * <p>Legacy rows are seeded with the messy raw value in {@code city} and {@code country=null},
 * matching what the OLD {@code LocationParser} actually wrote for a flat single-token source
 * string (a token with no comma was stored in {@code city}, never classified as a country
 * unless it was the literal {@code "Remote"} sentinel).
 *
 * <p>Runs in its own {@link LocationBackfillIsolationProfile} (fresh drop-and-create DB):
 * unlike most component tests, {@code normalizeLocationsBatch} walks and rewrites the ENTIRE
 * {@code crawler.job_post} table, so it must not share the default-profile shared instance
 * with every other component test class in this module.
 */
@QuarkusTest
@TestProfile(LocationBackfillIsolationProfile.class)
@DisplayName("LocationNormalizationBackfillRunner / normalizeLocationsBatch, real DB")
class LocationNormalizationBackfillComponentTest {

    @Inject
    JobPostRepository repository;

    @Inject
    EntityManager entityManager;

    private UUID seedLegacyJobPost(UUID targetId, String contentHash, String title, String url,
                                    String city, String country) {
        return QuarkusTransaction.requiringNew().call(() -> {
            JobPostEntity entity = new JobPostEntity();
            entity.targetId = targetId;
            entity.title = title;
            entity.url = url;
            entity.description = "Legacy description";
            entity.contentHash = contentHash;
            entity.city = city;
            entity.country = country;
            entity.firstSeenAt = OffsetDateTime.now().minusDays(30);
            entity.lastSeenAt = OffsetDateTime.now().minusDays(30);
            entityManager.persist(entity);
            entityManager.flush();
            return entity.id;
        });
    }

    /**
     * Creates a fresh {@code pull_target} row and returns its Hibernate-generated id. Does NOT
     * pre-assign an id before {@code persist()}: {@code PullTargetEntity.id} is {@code
     * @GeneratedValue}, and Hibernate treats a manually pre-set id on a generated-id field as an
     * already-existing (detached) entity, not a new one.
     */
    private UUID ensureTarget() {
        return QuarkusTransaction.requiringNew().call(() -> {
            PullTargetEntity target = new PullTargetEntity();
            target.sourceType = "greenhouse";
            target.companyName = "Location Backfill Test Co";
            target.token = "location-backfill-test-co-" + UUID.randomUUID();
            target.pullPriority = 100;
            target.nextPullAfter = OffsetDateTime.now();
            target.status = "active";
            target.statusChangedAt = OffsetDateTime.now();
            target.consecutiveFailures = 0;
            target.createdAt = OffsetDateTime.now();
            target.updatedAt = OffsetDateTime.now();
            entityManager.persist(target);
            entityManager.flush();
            return target.id;
        });
    }

    private JobPostEntity findFresh(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> JobPostEntity.findById(id));
    }

    private List<JobPostLocationEntity> findLocationsByJobPostId(UUID jobPostId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> JobPostLocationEntity.<JobPostLocationEntity>list("jobPostId", jobPostId));
    }

    private void runFullBackfillPass() {
        UUID cursor = null;
        for (int i = 0; i < 1000; i++) {
            LocationBatchResult result = repository.normalizeLocationsBatch(cursor, 5);
            if (result.isEmpty()) {
                return;
            }
            cursor = result.lastId();
        }
        throw new IllegalStateException("Backfill did not terminate within 1000 pages");
    }

    @Test
    @DisplayName("QAE-408-C-01: legacy dirty rows normalize correctly, preserved values survive, content_hash untouched")
    void backfillNormalizesLegacyRowsAndPreservesUnmappableValues() {
        UUID targetId = ensureTarget();

        UUID usRow = seedLegacyJobPost(targetId, "hash-408-us", "US Alias Role A",
                "https://example.com/408-us", "Us", null);
        UUID usaRow = seedLegacyJobPost(targetId, "hash-408-usa", "US Alias Role B",
                "https://example.com/408-usa", "Usa", null);
        UUID caRow = seedLegacyJobPost(targetId, "hash-408-ca", "Ambiguous State Code Role",
                "https://example.com/408-ca", "Ca", null);
        UUID esRow = seedLegacyJobPost(targetId, "hash-408-es", "ISO-2 Country Role",
                "https://example.com/408-es", "Es", null);
        UUID emeaRow = seedLegacyJobPost(targetId, "hash-408-emea", "Unmappable Region Role",
                "https://example.com/408-emea", "Emea", null);
        UUID controlRow = seedLegacyJobPost(targetId, "hash-408-control", "Already Clean Role",
                "https://example.com/408-control", "Barcelona", "Spain");

        String usHashBefore = findFresh(usRow).contentHash;
        String usaHashBefore = findFresh(usaRow).contentHash;
        String caHashBefore = findFresh(caRow).contentHash;
        String esHashBefore = findFresh(esRow).contentHash;
        String emeaHashBefore = findFresh(emeaRow).contentHash;
        String controlHashBefore = findFresh(controlRow).contentHash;

        runFullBackfillPass();

        JobPostEntity us = findFresh(usRow);
        assertThat(us.city).isNull();
        assertThat(us.country).isEqualTo("United States");
        assertThat(us.contentHash).isEqualTo(usHashBefore);

        JobPostEntity usa = findFresh(usaRow);
        assertThat(usa.city).isNull();
        assertThat(usa.country).isEqualTo("United States");
        assertThat(usa.contentHash).isEqualTo(usaHashBefore);

        JobPostEntity ca = findFresh(caRow);
        assertThat(ca.city).isEqualTo("California");
        assertThat(ca.country).isEqualTo("United States");
        assertThat(ca.contentHash).isEqualTo(caHashBefore);

        JobPostEntity es = findFresh(esRow);
        assertThat(es.city).isNull();
        assertThat(es.country).isEqualTo("Spain");
        assertThat(es.contentHash).isEqualTo(esHashBefore);

        JobPostEntity emea = findFresh(emeaRow);
        assertThat(emea.city).isEqualTo("Emea");
        assertThat(emea.country).isNull();
        assertThat(emea.contentHash).isEqualTo(emeaHashBefore);

        JobPostEntity control = findFresh(controlRow);
        assertThat(control.city).isEqualTo("Barcelona");
        assertThat(control.country).isEqualTo("Spain");
        assertThat(control.contentHash).isEqualTo(controlHashBefore);

        // job_post_location child rows mirror the new primary opening (ADR 0017 invariant).
        List<JobPostLocationEntity> usLocations = findLocationsByJobPostId(usRow);
        assertThat(usLocations).hasSize(1);
        assertThat(usLocations.get(0).isPrimary).isTrue();
        assertThat(usLocations.get(0).city).isNull();
        assertThat(usLocations.get(0).country).isEqualTo("United States");

        List<JobPostLocationEntity> caLocations = findLocationsByJobPostId(caRow);
        assertThat(caLocations).hasSize(1);
        assertThat(caLocations.get(0).isPrimary).isTrue();
        assertThat(caLocations.get(0).city).isEqualTo("California");
        assertThat(caLocations.get(0).country).isEqualTo("United States");

        List<JobPostLocationEntity> emeaLocations = findLocationsByJobPostId(emeaRow);
        assertThat(emeaLocations).hasSize(1);
        assertThat(emeaLocations.get(0).isPrimary).isTrue();
        assertThat(emeaLocations.get(0).city).isEqualTo("Emea");
        assertThat(emeaLocations.get(0).country).isNull();

        List<JobPostLocationEntity> controlLocations = findLocationsByJobPostId(controlRow);
        assertThat(controlLocations).hasSize(1);
        assertThat(controlLocations.get(0).city).isEqualTo("Barcelona");
        assertThat(controlLocations.get(0).country).isEqualTo("Spain");
    }

    @Test
    @DisplayName("QAE-408-C-02: idempotent on replay, city/country/contentHash/child rows unchanged, no duplicate child rows")
    void backfillIsIdempotentOnReplay() {
        UUID targetId = ensureTarget();

        UUID row = seedLegacyJobPost(targetId, "hash-408-idempotent", "Idempotent Replay Role",
                "https://example.com/408-idempotent", "Tx", null);

        runFullBackfillPass();

        JobPostEntity afterFirstPass = findFresh(row);
        String cityAfterFirst = afterFirstPass.city;
        String countryAfterFirst = afterFirstPass.country;
        String hashAfterFirst = afterFirstPass.contentHash;
        int childCountAfterFirst = findLocationsByJobPostId(row).size();

        runFullBackfillPass();

        JobPostEntity afterSecondPass = findFresh(row);
        assertThat(afterSecondPass.city).isEqualTo(cityAfterFirst);
        assertThat(afterSecondPass.country).isEqualTo(countryAfterFirst);
        assertThat(afterSecondPass.contentHash).isEqualTo(hashAfterFirst);
        assertThat(findLocationsByJobPostId(row)).hasSize(childCountAfterFirst);

        assertThat(afterSecondPass.city).isEqualTo("Texas");
        assertThat(afterSecondPass.country).isEqualTo("United States");
    }

    @Test
    @DisplayName("QAE-408-C-03: cursor terminates on an all-already-normalized table, each row processed exactly once")
    void cursorTerminatesOnAlreadyNormalizedTable() {
        UUID targetId = ensureTarget();

        UUID rowA = seedLegacyJobPost(targetId, "hash-408-already-a", "Already Normalized Role A",
                "https://example.com/408-already-a", null, "United States");
        UUID rowB = seedLegacyJobPost(targetId, "hash-408-already-b", "Already Normalized Role B",
                "https://example.com/408-already-b", null, "United States");

        int totalProcessed = 0;
        UUID cursor = null;
        int pages = 0;
        for (int i = 0; i < 1000; i++) {
            LocationBatchResult result = repository.normalizeLocationsBatch(cursor, 1);
            pages++;
            if (result.isEmpty()) {
                break;
            }
            totalProcessed += result.processed();
            cursor = result.lastId();
            assertThat(pages).isLessThan(1000);
        }

        assertThat(totalProcessed).isGreaterThanOrEqualTo(2);
        assertThat(findFresh(rowA).country).isEqualTo("United States");
        assertThat(findFresh(rowB).country).isEqualTo("United States");
    }
}
