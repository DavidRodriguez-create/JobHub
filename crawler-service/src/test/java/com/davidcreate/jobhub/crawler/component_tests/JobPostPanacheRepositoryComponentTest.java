package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostLocationEntity;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.JobPostLocation;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Component tests for {@link com.davidcreate.jobhub.crawler.adapter.out.persistence.JobPostPanacheRepository#saveAll}
 * against a real DevServices Postgres. Each test seeds its own job_post row(s) with a
 * unique content_hash so it is unaffected by other tests in this class.
 */
@QuarkusTest
@DisplayName("JobPostPanacheRepository Component Tests")
class JobPostPanacheRepositoryComponentTest {

    private static final UUID TARGET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");

    @Inject
    JobPostRepository jobPostRepository;

    @Inject
    EntityManager entityManager;

    private void seedJobPost(String contentHash, String title, String url) {
        QuarkusTransaction.requiringNew().run(() -> {
            JobPostEntity entity = new JobPostEntity();
            entity.targetId = TARGET_ID;
            entity.title = title;
            entity.url = url;
            entity.description = "Original description";
            entity.contentHash = contentHash;
            entity.firstSeenAt = OffsetDateTime.now().minusDays(1);
            entity.lastSeenAt = OffsetDateTime.now().minusDays(1);
            entityManager.persist(entity);
        });
    }

    private long countByContentHash(String contentHash) {
        return QuarkusTransaction.requiringNew()
                .call(() -> JobPostEntity.count("contentHash", contentHash));
    }

    private long countAll() {
        return QuarkusTransaction.requiringNew().call(() -> JobPostEntity.count());
    }

    private String findTitleByContentHash(String contentHash) {
        return QuarkusTransaction.requiringNew()
                .call(() -> ((JobPostEntity) JobPostEntity.find("contentHash", contentHash).firstResult()).title);
    }

    private JobPost newJob(String contentHash, String title, String url) {
        return JobPost.builder()
                .targetId(TARGET_ID)
                .title(title)
                .url(url)
                .description("Freshly crawled description")
                .contentHash(contentHash)
                .build();
    }

    @Test
    @DisplayName("BE-DUP-05: saveAll upserts an existing row by contentHash, updating its title")
    void saveAllUpdatesExistingRowByContentHash() {
        String hash = "dup-hash-001";
        seedJobPost(hash, "Old Title", "https://example.com/dup-001");

        JobPost incoming = newJob(hash, "New Title", "https://example.com/dup-001");

        jobPostRepository.saveAll(List.of(incoming));

        assertThat(countByContentHash(hash)).isEqualTo(1);
        assertThat(findTitleByContentHash(hash)).isEqualTo("New Title");
    }

    @Test
    @DisplayName("BE-DUP-06: saveAll inserts brand-new jobs and increases the row count")
    void saveAllInsertsBrandNewJobs() {
        long before = countAll();

        List<JobPost> brandNew = List.of(
                newJob("brand-new-hash-001", "New Job 1", "https://example.com/new-001"),
                newJob("brand-new-hash-002", "New Job 2", "https://example.com/new-002"),
                newJob("brand-new-hash-003", "New Job 3", "https://example.com/new-003"));

        jobPostRepository.saveAll(brandNew);

        assertThat(countAll()).isEqualTo(before + 3);
    }

    @Test
    @DisplayName("BE-DUP-07: saveAll with two incoming jobs sharing the same contentHash as an "
            + "existing row results in exactly one row for that hash")
    void saveAllWithDuplicateIncomingHashesResultsInOneRow() {
        String hash = "dup-hash-002";
        seedJobPost(hash, "Old Title", "https://example.com/dup-002");

        JobPost firstIncoming = newJob(hash, "New Title A", "https://example.com/dup-002");
        JobPost secondIncoming = newJob(hash, "New Title B", "https://example.com/dup-002b");

        jobPostRepository.saveAll(List.of(firstIncoming, secondIncoming));

        assertThat(countByContentHash(hash)).isEqualTo(1);
    }

    @Test
    @DisplayName("BE-DUP-08: saveAll with an empty list does not throw and leaves the row count unchanged")
    void saveAllWithEmptyListIsNoop() {
        long before = countAll();

        jobPostRepository.saveAll(List.of());

        assertThat(countAll()).isEqualTo(before);
    }

    // ─── Story #1 / #291: crawler.job_post_location ────────────────────────────

    private List<JobPostLocationEntity> findLocationsByJobPostId(UUID jobPostId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> JobPostLocationEntity.<JobPostLocationEntity>list("jobPostId", jobPostId));
    }

    private UUID findIdByContentHash(String contentHash) {
        return QuarkusTransaction.requiringNew()
                .call(() -> ((JobPostEntity) JobPostEntity.find("contentHash", contentHash).firstResult()).id);
    }

    @Test
    @DisplayName("QAE-CRAWL-STORE-2: one transaction persists the primary and an additional opening together")
    void saveAllPersistsPrimaryAndAdditionalOpeningsInOneTransaction() {
        String hash = "loc-hash-001";
        JobPost incoming = JobPost.builder()
                .targetId(TARGET_ID)
                .title("Multi-location job")
                .url("https://example.com/loc-001")
                .description("Description")
                .contentHash(hash)
                .city("Barcelona")
                .country("Spain")
                .additionalLocations(List.of(
                        JobPostLocation.builder().country("Netherlands").city("Amsterdam").build()))
                .build();

        jobPostRepository.saveAll(List.of(incoming));

        UUID jobPostId = findIdByContentHash(hash);
        List<JobPostLocationEntity> rows = findLocationsByJobPostId(jobPostId);

        assertThat(rows).hasSize(2);
        JobPostLocationEntity primary = rows.stream().filter(r -> r.isPrimary).findFirst().orElseThrow();
        assertThat(primary.city).isEqualTo("Barcelona");
        assertThat(primary.country).isEqualTo("Spain");

        UUID parentJobPostId = jobPostId;
        JobPostEntity parent = QuarkusTransaction.requiringNew()
                .call(() -> JobPostEntity.findById(parentJobPostId));
        assertThat(parent.city).isEqualTo(primary.city);
        assertThat(parent.country).isEqualTo(primary.country);
    }

    @Test
    @DisplayName("QAE-CRAWL-STORE-2B: single-opening post produces exactly one primary child row")
    void saveAllWithSingleOpeningProducesExactlyOnePrimaryChildRow() {
        String hash = "loc-hash-002";
        JobPost incoming = JobPost.builder()
                .targetId(TARGET_ID)
                .title("Single-location job")
                .url("https://example.com/loc-002")
                .description("Description")
                .contentHash(hash)
                .city("Madrid")
                .country("Spain")
                .build();

        jobPostRepository.saveAll(List.of(incoming));

        UUID jobPostId = findIdByContentHash(hash);
        List<JobPostLocationEntity> rows = findLocationsByJobPostId(jobPostId);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).isPrimary).isTrue();
        assertThat(rows.get(0).city).isEqualTo("Madrid");
        assertThat(rows.get(0).country).isEqualTo("Spain");
    }

    @Test
    @DisplayName("QAE-CRAWL-STORE-3: DB rejects a second is_primary=true row for the same post")
    void secondPrimaryRowForSamePostViolatesUniqueConstraint() {
        String hash = "loc-hash-003";
        seedJobPost(hash, "Job with primary row", "https://example.com/loc-003");
        UUID jobPostId = findIdByContentHash(hash);

        QuarkusTransaction.requiringNew().run(() -> {
            JobPostLocationEntity primary = new JobPostLocationEntity();
            primary.jobPostId = jobPostId;
            primary.country = "Spain";
            primary.city = "Barcelona";
            primary.isPrimary = true;
            primary.position = 0;
            primary.createdAt = OffsetDateTime.now();
            entityManager.persist(primary);
        });

        assertThatThrownBy(() -> QuarkusTransaction.requiringNew().run(() -> {
            JobPostLocationEntity secondPrimary = new JobPostLocationEntity();
            secondPrimary.jobPostId = jobPostId;
            secondPrimary.country = "France";
            secondPrimary.city = "Paris";
            secondPrimary.isPrimary = true;
            secondPrimary.position = 1;
            secondPrimary.createdAt = OffsetDateTime.now();
            entityManager.persist(secondPrimary);
            entityManager.flush();
        })).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("QAE-CRAWL-STORE-3B: duplicate (country, city) within one post is deduped by the application layer")
    void applicationLayerDedupesRepeatedSaveOfSameOpening() {
        String hash = "loc-hash-004";
        JobPost firstSave = JobPost.builder()
                .targetId(TARGET_ID)
                .title("Dedupe job")
                .url("https://example.com/loc-004")
                .description("Description")
                .contentHash(hash)
                .city("Barcelona")
                .country("Spain")
                .additionalLocations(List.of(
                        JobPostLocation.builder().country("Spain").city("Barcelona").build()))
                .build();

        jobPostRepository.saveAll(List.of(firstSave));

        UUID jobPostId = findIdByContentHash(hash);
        List<JobPostLocationEntity> rows = findLocationsByJobPostId(jobPostId);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).isPrimary).isTrue();

        // A second write of the same post (upsert path) must stay idempotent: still 1 row.
        jobPostRepository.saveAll(List.of(firstSave));
        assertThat(findLocationsByJobPostId(jobPostId)).hasSize(1);
    }

    @Test
    @DisplayName("QAE-CRAWL-STORE-5: cascade delete removes child location rows with the parent")
    void deletingParentJobPostCascadesToLocationRows() {
        String hash = "loc-hash-005";
        JobPost incoming = JobPost.builder()
                .targetId(TARGET_ID)
                .title("Cascade job")
                .url("https://example.com/loc-005")
                .description("Description")
                .contentHash(hash)
                .city("Barcelona")
                .country("Spain")
                .additionalLocations(List.of(
                        JobPostLocation.builder().country("Netherlands").city("Amsterdam").build()))
                .build();

        jobPostRepository.saveAll(List.of(incoming));
        UUID jobPostId = findIdByContentHash(hash);
        assertThat(findLocationsByJobPostId(jobPostId)).hasSize(2);

        QuarkusTransaction.requiringNew().run(() -> {
            JobPostEntity.deleteById(jobPostId);
        });

        assertThat(findLocationsByJobPostId(jobPostId)).isEmpty();
    }

    // ─── Story #319 (ticket #323): Lever-shaped multi-location persistence ─────

    @Test
    @DisplayName("TC-319-CRAWL-11: Lever-shaped JobPost (primary + 2 additional openings) persists as 3 "
            + "deduped rows, exactly 1 primary")
    void leverShapedJobPostPersistsThreeDedupedRowsOnePrimary() {
        String hash = "loc-hash-319-crawl-11";
        JobPost incoming = JobPost.builder()
                .targetId(TARGET_ID)
                .title("Backend Engineer")
                .url("https://jobs.lever.co/Acme/319-crawl-11")
                .description("Backend engineering role, multiple offices open.")
                .contentHash(hash)
                .city("Barcelona")
                .country("Spain")
                .additionalLocations(List.of(
                        JobPostLocation.builder().country("Netherlands").city("Amsterdam").build(),
                        JobPostLocation.builder().country("France").city("Paris").build()))
                .build();

        jobPostRepository.saveAll(List.of(incoming));

        UUID jobPostId = findIdByContentHash(hash);
        List<JobPostLocationEntity> rows = findLocationsByJobPostId(jobPostId);

        assertThat(rows).hasSize(3);
        assertThat(rows.stream().filter(r -> r.isPrimary).count()).isEqualTo(1);
        JobPostLocationEntity primary = rows.stream().filter(r -> r.isPrimary).findFirst().orElseThrow();
        assertThat(primary.country).isEqualTo("Spain");
        assertThat(primary.city).isEqualTo("Barcelona");
        assertThat(rows).filteredOn(r -> !r.isPrimary)
                .extracting(r -> r.country, r -> r.city)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Netherlands", "Amsterdam"),
                        org.assertj.core.groups.Tuple.tuple("France", "Paris"));
    }

    @Test
    @DisplayName("TC-319-CRAWL-12: re-crawl identity is unaffected by additional openings changing "
            + "order/count; child rows reflect only the latest save")
    void recrawlIdentityUnaffectedByAdditionalOpeningsChange() {
        String hash = "loc-hash-319-recrawl";
        JobPost firstCrawl = JobPost.builder()
                .targetId(TARGET_ID)
                .title("Backend Engineer")
                .url("https://jobs.lever.co/Acme/319-recrawl")
                .description("Backend engineering role, multiple offices open.")
                .contentHash(hash)
                .city("Barcelona")
                .country("Spain")
                .additionalLocations(List.of(
                        JobPostLocation.builder().country("Netherlands").city("Amsterdam").build(),
                        JobPostLocation.builder().country("France").city("Paris").build()))
                .build();

        jobPostRepository.saveAll(List.of(firstCrawl));
        UUID jobPostId = findIdByContentHash(hash);
        assertThat(findLocationsByJobPostId(jobPostId)).hasSize(3);

        // Second crawl: same contentHash (unchanged canonical location + description), but the
        // additional-openings list is reordered and reduced to 1 entry (an office closed).
        JobPost secondCrawl = JobPost.builder()
                .targetId(TARGET_ID)
                .title("Backend Engineer")
                .url("https://jobs.lever.co/Acme/319-recrawl")
                .description("Backend engineering role, multiple offices open.")
                .contentHash(hash)
                .city("Barcelona")
                .country("Spain")
                .additionalLocations(List.of(
                        JobPostLocation.builder().country("France").city("Paris").build()))
                .build();

        jobPostRepository.saveAll(List.of(secondCrawl));

        assertThat(countByContentHash(hash)).isEqualTo(1);
        assertThat(findIdByContentHash(hash)).isEqualTo(jobPostId);

        List<JobPostLocationEntity> rowsAfter = findLocationsByJobPostId(jobPostId);
        assertThat(rowsAfter).hasSize(2);
        assertThat(rowsAfter.stream().filter(r -> r.isPrimary).count()).isEqualTo(1);
        assertThat(rowsAfter).filteredOn(r -> !r.isPrimary)
                .extracting(r -> r.country, r -> r.city)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("France", "Paris"));
    }

    // ─── Architect ruling item 4 (story #319, ticket #323): update-path parent re-sync ──

    @Test
    @DisplayName("ARCH-319-UPDATE: re-crawl re-syncs the split primary city/country onto the parent row, "
            + "not just the child")
    void recrawlResyncsSplitPrimaryCityAndCountryOnParentRow() {
        String hash = "loc-hash-319-update-resync";

        // Simulate a pre-existing row written before the split (parent carries the raw, unsplit
        // location string in `city`, `country` left null), the shape LeverJobSourceClient used
        // to produce before this story.
        QuarkusTransaction.requiringNew().run(() -> {
            JobPostEntity legacy = new JobPostEntity();
            legacy.targetId = TARGET_ID;
            legacy.title = "Backend Engineer";
            legacy.url = "https://jobs.lever.co/Acme/319-update-resync";
            legacy.description = "Backend engineering role.";
            legacy.contentHash = hash;
            legacy.city = "Barcelona, Spain";
            legacy.country = null;
            legacy.firstSeenAt = OffsetDateTime.now().minusDays(1);
            legacy.lastSeenAt = OffsetDateTime.now().minusDays(1);
            entityManager.persist(legacy);
        });

        // Re-crawl: same contentHash (deterministic split of the same raw location string),
        // now split into city/country as LeverJobSourceClient produces post-#319.
        JobPost recrawled = JobPost.builder()
                .targetId(TARGET_ID)
                .title("Backend Engineer")
                .url("https://jobs.lever.co/Acme/319-update-resync")
                .description("Backend engineering role.")
                .contentHash(hash)
                .city("Barcelona")
                .country("Spain")
                .build();

        jobPostRepository.saveAll(List.of(recrawled));

        UUID jobPostId = findIdByContentHash(hash);
        JobPostEntity parent = QuarkusTransaction.requiringNew().call(() -> JobPostEntity.findById(jobPostId));
        assertThat(parent.city).isEqualTo("Barcelona");
        assertThat(parent.country).isEqualTo("Spain");

        List<JobPostLocationEntity> rows = findLocationsByJobPostId(jobPostId);
        assertThat(rows).hasSize(1);
        JobPostLocationEntity primary = rows.get(0);
        assertThat(primary.isPrimary).isTrue();
        assertThat(primary.city).isEqualTo(parent.city);
        assertThat(primary.country).isEqualTo(parent.country);
    }
}
