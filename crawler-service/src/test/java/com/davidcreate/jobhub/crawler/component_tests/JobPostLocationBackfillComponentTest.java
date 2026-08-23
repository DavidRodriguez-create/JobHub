package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostLocationEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.PullTargetEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QAE-CRAWL-STORE-4: replays the actual {@code db/init/014-crawler-job-post-location.sql}
 * migration (frozen, not forked) against DevServices Postgres pre-loaded with legacy
 * {@code job_post} rows that predate the child table, and asserts its backfill INSERT
 * behaves exactly as ADR 0017 describes: one primary child row per post that has any
 * location data, none for posts with neither city nor country, and idempotent on replay.
 */
@QuarkusTest
@DisplayName("crawler.job_post_location backfill migration replay")
class JobPostLocationBackfillComponentTest {

    private static final UUID TARGET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");

    @Inject
    EntityManager entityManager;

    private static String readMigrationSql() {
        // Repo root is 3 levels up from the crawler-service module directory.
        Path path = Path.of("..", "db", "init", "014-crawler-job-post-location.sql");
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read 014-crawler-job-post-location.sql at " + path.toAbsolutePath(), e);
        }
    }

    private void runMigrationSql() {
        String withoutComments = readMigrationSql().lines()
                .map(line -> {
                    int commentIndex = line.indexOf("--");
                    return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
                })
                .reduce("", (acc, line) -> acc + line + "\n");

        QuarkusTransaction.requiringNew().run(() -> {
            for (String statement : withoutComments.split(";")) {
                String trimmed = statement.strip();
                // The GRANT is a cross-service permission concern (job_user), not part of
                // what this test replays: the DevServices test DB has no such role.
                if (trimmed.isEmpty() || trimmed.toUpperCase().startsWith("GRANT")) {
                    continue;
                }
                entityManager.createNativeQuery(trimmed).executeUpdate();
            }
        });
    }

    private UUID seedLegacyJobPost(String contentHash, String title, String url, String city, String country) {
        return QuarkusTransaction.requiringNew().call(() -> {
            JobPostEntity entity = new JobPostEntity();
            entity.targetId = TARGET_ID;
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

    private List<JobPostLocationEntity> findLocationsByJobPostId(UUID jobPostId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> JobPostLocationEntity.<JobPostLocationEntity>list("jobPostId", jobPostId));
    }

    @Test
    @DisplayName("QAE-CRAWL-STORE-4: backfill produces one primary row per pre-existing post, "
            + "none for posts with no location data, and is idempotent on replay")
    void backfillProducesOnePrimaryRowPerPreExistingPostAndIsIdempotent() {
        QuarkusTransaction.requiringNew().run(() -> {
            if (PullTargetEntity.findById(TARGET_ID) == null) {
                PullTargetEntity target = new PullTargetEntity();
                target.id = TARGET_ID;
                target.sourceType = "greenhouse";
                target.companyName = "Backfill Test Co";
                target.token = "backfill-test-co";
                target.pullPriority = 100;
                target.nextPullAfter = OffsetDateTime.now();
                target.status = "active";
                target.statusChangedAt = OffsetDateTime.now();
                target.consecutiveFailures = 0;
                target.createdAt = OffsetDateTime.now();
                target.updatedAt = OffsetDateTime.now();
                entityManager.persist(target);
            }
        });

        UUID withCityAndCountry = seedLegacyJobPost(
                "backfill-hash-a", "Legacy job with city+country",
                "https://example.com/backfill-a", "Barcelona", "Spain");
        UUID remoteOnly = seedLegacyJobPost(
                "backfill-hash-b", "Legacy remote job",
                "https://example.com/backfill-b", null, "Remote");
        UUID noLocationData = seedLegacyJobPost(
                "backfill-hash-c", "Legacy job with no location",
                "https://example.com/backfill-c", null, null);

        runMigrationSql();

        List<JobPostLocationEntity> rowsA = findLocationsByJobPostId(withCityAndCountry);
        assertThat(rowsA).hasSize(1);
        assertThat(rowsA.get(0).isPrimary).isTrue();
        assertThat(rowsA.get(0).city).isEqualTo("Barcelona");
        assertThat(rowsA.get(0).country).isEqualTo("Spain");

        List<JobPostLocationEntity> rowsB = findLocationsByJobPostId(remoteOnly);
        assertThat(rowsB).hasSize(1);
        assertThat(rowsB.get(0).isPrimary).isTrue();
        assertThat(rowsB.get(0).city).isNull();
        assertThat(rowsB.get(0).country).isEqualTo("Remote");

        assertThat(findLocationsByJobPostId(noLocationData)).isEmpty();

        // Re-running the backfill is a no-op (idempotent NOT EXISTS guard).
        runMigrationSql();

        assertThat(findLocationsByJobPostId(withCityAndCountry)).hasSize(1);
        assertThat(findLocationsByJobPostId(remoteOnly)).hasSize(1);
        assertThat(findLocationsByJobPostId(noLocationData)).isEmpty();
    }
}
