package com.davidcreate.jobhub.job.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.adapter.in.rest.dto.TriggerStatusMapper;
import com.davidcreate.jobhub.job.contract.model.TriggerStatusResponse;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerOrigin;
import com.davidcreate.jobhub.job.domain.model.TriggerOutcome;
import com.davidcreate.jobhub.job.domain.model.TriggerProgress;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;
import com.davidcreate.jobhub.job.domain.model.TriggerStatus;
import com.davidcreate.jobhub.job.domain.model.TriggerStatusOverview;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TriggerStatusMapper Unit Tests")
class TriggerStatusMapperTest {

    @Test
    @DisplayName("J-U-06: maps overview with crawl run-info present and enrichment absent")
    void mapsStatusResponse() {
        TriggerRequest crawl = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.CRAWL)
                .status(TriggerStatus.SUCCEEDED)
                .requestedAt(OffsetDateTime.now().minusHours(1))
                .startedAt(OffsetDateTime.now().minusMinutes(50))
                .finishedAt(OffsetDateTime.now().minusMinutes(45))
                .resultSummary("crawled 5")
                .build();

        TriggerStatusOverview overview = TriggerStatusOverview.builder()
                .triggerEnabled(true)
                .twoFactorRequired(false)
                .crawl(crawl)
                .enrichment(null)
                .build();

        TriggerStatusResponse response = TriggerStatusMapper.toStatusResponse(overview);

        assertThat(response.getTriggerEnabled()).isTrue();
        assertThat(response.getTwoFactorRequired()).isFalse();
        assertThat(response.getCrawl()).isNotNull();
        assertThat(response.getCrawl().getStatus().toString()).isEqualTo("succeeded");
        assertThat(response.getCrawl().getResultSummary()).isEqualTo("crawled 5");
        assertThat(response.getEnrichment()).isNull();
    }

    @ParameterizedTest(name = "maps run-info with TriggerStatus.{0} to its JSON value")
    @EnumSource(value = TriggerStatus.class, names = {"CANCEL_REQUESTED", "CANCELLED"})
    @DisplayName("Story #58 / ADR 0006: maps run-info with the new cancel statuses to their JSON values")
    void mapsCancelStatusesInRunInfo(TriggerStatus status) {
        TriggerRequest crawl = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.CRAWL)
                .status(status)
                .requestedAt(OffsetDateTime.now().minusMinutes(5))
                .startedAt(OffsetDateTime.now().minusMinutes(4))
                .build();

        TriggerStatusOverview overview = TriggerStatusOverview.builder()
                .triggerEnabled(true)
                .twoFactorRequired(false)
                .crawl(crawl)
                .enrichment(null)
                .build();

        TriggerStatusResponse response = TriggerStatusMapper.toStatusResponse(overview);

        assertThat(response.getCrawl().getStatus().toString()).isEqualTo(status.value());
    }

    @Test
    @DisplayName("TC-513-J3: a non-null domain TriggerProgress maps to the contract TriggerProgress, field-by-field")
    void mapsPopulatedProgress() {
        OffsetDateTime updatedAt = OffsetDateTime.now();
        TriggerProgress progress = TriggerProgress.builder()
                .targetsVisited(3)
                .newPosts(47)
                .currentCompany("Klaviyo")
                .currentSourceType("greenhouse")
                .lastCompany("Stripe")
                .lastSourceType("lever")
                .lastFoundPosts(142)
                .lastNewPosts(16)
                .updatedAt(updatedAt)
                .build();

        TriggerRequest crawl = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.CRAWL)
                .status(TriggerStatus.RUNNING)
                .requestedAt(OffsetDateTime.now().minusMinutes(5))
                .startedAt(OffsetDateTime.now().minusMinutes(4))
                .progress(progress)
                .build();

        TriggerStatusOverview overview = TriggerStatusOverview.builder()
                .triggerEnabled(true)
                .twoFactorRequired(false)
                .crawl(crawl)
                .enrichment(null)
                .build();

        TriggerStatusResponse response = TriggerStatusMapper.toStatusResponse(overview);

        assertThat(response.getCrawl().getProgress()).isNotNull();
        assertThat(response.getCrawl().getProgress().getTargetsVisited()).isEqualTo(3);
        assertThat(response.getCrawl().getProgress().getNewPosts()).isEqualTo(47);
        assertThat(response.getCrawl().getProgress().getCurrentCompany()).isEqualTo("Klaviyo");
        assertThat(response.getCrawl().getProgress().getCurrentSourceType()).isEqualTo("greenhouse");
        assertThat(response.getCrawl().getProgress().getLastCompany()).isEqualTo("Stripe");
        assertThat(response.getCrawl().getProgress().getLastSourceType()).isEqualTo("lever");
        assertThat(response.getCrawl().getProgress().getLastFoundPosts()).isEqualTo(142);
        assertThat(response.getCrawl().getProgress().getLastNewPosts()).isEqualTo(16);
        assertThat(response.getCrawl().getProgress().getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("TC-513-J4: domain progress = null maps to contract progress = null, no NPE, no fabricated object (crawl kind)")
    void mapsNullProgressForCrawl() {
        TriggerRequest crawl = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.CRAWL)
                .status(TriggerStatus.QUEUED)
                .requestedAt(OffsetDateTime.now())
                .progress(null)
                .build();

        TriggerStatusOverview overview = TriggerStatusOverview.builder()
                .triggerEnabled(true)
                .twoFactorRequired(false)
                .crawl(crawl)
                .enrichment(null)
                .build();

        TriggerStatusResponse response = TriggerStatusMapper.toStatusResponse(overview);

        assertThat(response.getCrawl().getProgress()).isNull();
    }

    @Test
    @DisplayName("TC-513-J4: domain progress = null maps to contract progress = null, no NPE, no fabricated object (enrichment kind)")
    void mapsNullProgressForEnrichment() {
        TriggerRequest enrichment = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.ENRICHMENT)
                .status(TriggerStatus.SUCCEEDED)
                .requestedAt(OffsetDateTime.now().minusHours(1))
                .resultSummary("enriched 8 postings")
                .progress(null)
                .build();

        TriggerStatusOverview overview = TriggerStatusOverview.builder()
                .triggerEnabled(true)
                .twoFactorRequired(false)
                .crawl(null)
                .enrichment(enrichment)
                .build();

        TriggerStatusResponse response = TriggerStatusMapper.toStatusResponse(overview);

        assertThat(response.getEnrichment().getProgress()).isNull();
    }

    @Test
    @DisplayName("C25: maps overview.lastCrawlRun / lastEnrichmentRun to TriggerLastRun, 1:1")
    void mapsLastRunOneToOne() {
        OffsetDateTime finishedAt = OffsetDateTime.now().minusMinutes(12);
        TriggerRequest lastCrawl = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.CRAWL)
                .status(TriggerStatus.SUCCEEDED)
                .origin(TriggerOrigin.SCHEDULED)
                .outcome(TriggerOutcome.NO_TARGETS)
                .finishedAt(finishedAt)
                .resultSummary("no more targets to crawl")
                .build();

        TriggerStatusOverview overview = TriggerStatusOverview.builder()
                .triggerEnabled(true)
                .twoFactorRequired(false)
                .lastCrawlRun(lastCrawl)
                .lastEnrichmentRun(null)
                .build();

        TriggerStatusResponse response = TriggerStatusMapper.toStatusResponse(overview);

        assertThat(response.getLastCrawlRun()).isNotNull();
        assertThat(response.getLastCrawlRun().getId()).isEqualTo(lastCrawl.getId());
        assertThat(response.getLastCrawlRun().getStatus().toString()).isEqualTo("succeeded");
        assertThat(response.getLastCrawlRun().getOrigin().toString()).isEqualTo("scheduled");
        assertThat(response.getLastCrawlRun().getOutcome().toString()).isEqualTo("no_targets");
        assertThat(response.getLastCrawlRun().getFinishedAt()).isEqualTo(finishedAt);
        assertThat(response.getLastCrawlRun().getResultSummary()).isEqualTo("no more targets to crawl");
        assertThat(response.getLastEnrichmentRun()).isNull();
    }

    @Test
    @DisplayName("C25/edge: pre-ADR row (outcome absent) maps outcome=null, no error")
    void mapsLastRunWithNullOutcomePreAdr() {
        TriggerRequest lastEnrichment = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.ENRICHMENT)
                .status(TriggerStatus.SUCCEEDED)
                .origin(TriggerOrigin.MANUAL)
                .outcome(null)
                .finishedAt(OffsetDateTime.now().minusHours(2))
                .resultSummary("enriched 8 postings")
                .build();

        TriggerStatusOverview overview = TriggerStatusOverview.builder()
                .triggerEnabled(true)
                .twoFactorRequired(false)
                .lastCrawlRun(null)
                .lastEnrichmentRun(lastEnrichment)
                .build();

        TriggerStatusResponse response = TriggerStatusMapper.toStatusResponse(overview);

        assertThat(response.getLastEnrichmentRun()).isNotNull();
        assertThat(response.getLastEnrichmentRun().getOutcome()).isNull();
        assertThat(response.getLastEnrichmentRun().getOrigin().toString()).isEqualTo("manual");
    }

    @Test
    @DisplayName("C25/AC-10: overview with no finished run of a kind maps that lastXRun to null, no crash")
    void mapsNullLastRun() {
        TriggerStatusOverview overview = TriggerStatusOverview.builder()
                .triggerEnabled(true)
                .twoFactorRequired(false)
                .lastCrawlRun(null)
                .lastEnrichmentRun(null)
                .build();

        TriggerStatusResponse response = TriggerStatusMapper.toStatusResponse(overview);

        assertThat(response.getLastCrawlRun()).isNull();
        assertThat(response.getLastEnrichmentRun()).isNull();
    }
}
