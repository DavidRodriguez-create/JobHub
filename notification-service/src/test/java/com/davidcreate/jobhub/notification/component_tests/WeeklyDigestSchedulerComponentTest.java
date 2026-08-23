package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.WeeklyDigestScheduler;
import com.davidcreate.jobhub.notification.adapter.out.persistence.DigestRunPanacheRepository;
import com.davidcreate.jobhub.notification.adapter.out.persistence.NotificationPanacheRepository;
import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.DigestRunEntity;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockAppInternalResource;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockAuthInternalResource;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockJobServiceResource;
import com.davidcreate.jobhub.notification.domain.model.ReadStatusFilter;
import com.davidcreate.jobhub.notification.domain.port.out.DigestMailer;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * End-to-end component tests for {@link WeeklyDigestScheduler}, exercising
 * {@code WeeklyDigestService} against a real DevServices Postgres (preferences + digest_run)
 * and WireMock stand-ins for auth-service, application-service and job-service.
 *
 * <p>{@code DigestMailer} is mocked (no real SMTP); since {@code @InjectMock} replaces the
 * bean for this whole class, no other test class in this package may rely on a real
 * {@code DigestMailer} bean.
 */
@QuarkusTest
@QuarkusTestResource(WireMockAuthInternalResource.class)
@QuarkusTestResource(WireMockAppInternalResource.class)
@QuarkusTestResource(WireMockJobServiceResource.class)
@DisplayName("WeeklyDigestScheduler Component Tests")
class WeeklyDigestSchedulerComponentTest {

    private static final UUID USER_0001 = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID USER_0002 = UUID.fromString("e0000000-0000-0000-0000-000000000002");
    private static final UUID USER_0004 = UUID.fromString("e0000000-0000-0000-0000-000000000004");
    private static final UUID USER_0005 = UUID.fromString("e0000000-0000-0000-0000-000000000005");
    private static final UUID USER_0006 = UUID.fromString("e0000000-0000-0000-0000-000000000006");

    private static final String EMAILS_PATH = "/auth/internal/users/emails";

    @Inject
    WeeklyDigestScheduler scheduler;

    @Inject
    DigestRunPanacheRepository digestRunRepository;

    @Inject
    NotificationPanacheRepository notificationRepository;

    @InjectMock
    DigestMailer digestMailer;

    @BeforeEach
    void resetWireMockStubs() {
        authInternal().resetAll();
        appInternal().resetAll();
        jobService().resetAll();
    }

    @AfterEach
    @Transactional
    void cleanUpGeneratedDigestRuns() {
        // Leave the seeded 0004 'sent' row (TC-09b) intact; remove rows this test created.
        DigestRunEntity.delete("userId in ?1 and userId != ?2",
                List.of(USER_0001, USER_0002, USER_0004, USER_0005, USER_0006), USER_0004);
    }

    private WireMockServer authInternal() {
        return WireMockAuthInternalResource.server();
    }

    private WireMockServer appInternal() {
        return WireMockAppInternalResource.server();
    }

    private WireMockServer jobService() {
        return WireMockJobServiceResource.server();
    }

    private void stubEmailBatch() {
        authInternal().stubFor(get(urlPathEqualTo(EMAILS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "emails": [
                                    {"userId": "e0000000-0000-0000-0000-000000000001", "email": "personalised@example.com"},
                                    {"userId": "e0000000-0000-0000-0000-000000000002", "email": "generic@example.com"},
                                    {"userId": "e0000000-0000-0000-0000-000000000005", "email": "zero-matches@example.com"}
                                  ]
                                }
                                """)));
    }

    private void stubInterestProfile(UUID userId, String body) {
        appInternal().stubFor(get(urlPathEqualTo("/internal/users/" + userId + "/interest-profile"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubJobSearch(String keyword, String location, String jobsJson) {
        var mapping = get(urlPathEqualTo("/jobs"))
                .withQueryParam("postedWithin", equalTo("week"))
                .withQueryParam("sort", equalTo("newest"))
                .withQueryParam("size", equalTo("10"));

        mapping = mapping.withQueryParam("keyword", keyword != null ? equalTo(keyword) : absent());
        mapping = mapping.withQueryParam("location", location != null ? equalTo(location) : absent());

        jobService().stubFor(mapping.willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(jobsJson)));
    }

    private static String jobsPage(int count) {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                content.append(",");
            }
            content.append("""
                    {
                      "id": "%s",
                      "title": "Job %d",
                      "url": "https://example.com/jobs/%d",
                      "location": "Barcelona, Spain",
                      "firstSeenAt": "2026-06-08T08:00:00Z",
                      "lastSeenAt": "2026-06-08T08:00:00Z",
                      "company": {"name": "Acme Corp", "logoUrl": null}
                    }
                    """.formatted(UUID.randomUUID(), i, i));
        }
        return """
                { "content": [%s], "page": 0, "size": 10, "totalElements": %d, "totalPages": 1 }
                """.formatted(content, count);
    }

    private static String emptyJobsPage() {
        return """
                { "content": [], "page": 0, "size": 10, "totalElements": 0, "totalPages": 0 }
                """;
    }

    // TC-21
    @Test
    @DisplayName("TC-21: scheduler run sends a personalised email and records a 'sent' digest_run for user 0001")
    void schedulerRunSendsPersonalisedEmailAndRecordsSentRun() {
        stubEmailBatch();
        stubInterestProfile(USER_0001, """
                { "userId": "e0000000-0000-0000-0000-000000000001",
                  "locations": ["Barcelona, Spain"], "companies": ["Acme Corp"],
                  "keywords": ["backend","java","developer"] }
                """);
        stubInterestProfile(USER_0002, """
                { "userId": "e0000000-0000-0000-0000-000000000002", "locations": [], "companies": [], "keywords": [] }
                """);
        stubInterestProfile(USER_0005, """
                { "userId": "e0000000-0000-0000-0000-000000000005",
                  "locations": [], "companies": [], "keywords": ["cobol","mainframe","punchcard"] }
                """);

        stubJobSearch("backend java developer", "Barcelona, Spain", jobsPage(6));
        stubJobSearch(null, null, jobsPage(10));
        stubJobSearch("cobol mainframe punchcard", null, emptyJobsPage());

        scheduler.run();

        verify(digestMailer, times(1)).send(eq("personalised@example.com"), anyList(), eq(true));

        List<DigestRunEntity> runs = digestRunRepository.list("userId", USER_0001);
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getStatus()).isEqualTo("sent");
        assertThat(runs.get(0).getJobCount()).isEqualTo(6);
    }

    // TC-22
    @Test
    @DisplayName("TC-22: scheduler run sends a generic email and records a 'sent' digest_run for user 0002 (no history)")
    void schedulerRunSendsGenericEmailForUserWithNoHistory() {
        stubEmailBatch();
        stubInterestProfile(USER_0001, """
                { "userId": "e0000000-0000-0000-0000-000000000001",
                  "locations": ["Barcelona, Spain"], "companies": ["Acme Corp"],
                  "keywords": ["backend","java","developer"] }
                """);
        stubInterestProfile(USER_0002, """
                { "userId": "e0000000-0000-0000-0000-000000000002", "locations": [], "companies": [], "keywords": [] }
                """);
        stubInterestProfile(USER_0005, """
                { "userId": "e0000000-0000-0000-0000-000000000005",
                  "locations": [], "companies": [], "keywords": ["cobol","mainframe","punchcard"] }
                """);

        stubJobSearch("backend java developer", "Barcelona, Spain", jobsPage(6));
        stubJobSearch(null, null, jobsPage(10));
        stubJobSearch("cobol mainframe punchcard", null, emptyJobsPage());

        scheduler.run();

        verify(digestMailer, times(1)).send(eq("generic@example.com"), anyList(), eq(false));

        List<DigestRunEntity> runs = digestRunRepository.list("userId", USER_0002);
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getStatus()).isEqualTo("sent");
        assertThat(runs.get(0).getJobCount()).isEqualTo(10);
    }

    // TC-23
    @Test
    @DisplayName("TC-23: scheduler run skips user 0005 (zero matching jobs) — no email, no 'sent'/'failed' digest_run")
    void schedulerRunSkipsUserWithZeroMatchingJobs() {
        stubEmailBatch();
        stubInterestProfile(USER_0001, """
                { "userId": "e0000000-0000-0000-0000-000000000001",
                  "locations": ["Barcelona, Spain"], "companies": ["Acme Corp"],
                  "keywords": ["backend","java","developer"] }
                """);
        stubInterestProfile(USER_0002, """
                { "userId": "e0000000-0000-0000-0000-000000000002", "locations": [], "companies": [], "keywords": [] }
                """);
        stubInterestProfile(USER_0005, """
                { "userId": "e0000000-0000-0000-0000-000000000005",
                  "locations": [], "companies": [], "keywords": ["cobol","mainframe","punchcard"] }
                """);

        stubJobSearch("backend java developer", "Barcelona, Spain", jobsPage(6));
        stubJobSearch(null, null, jobsPage(10));
        stubJobSearch("cobol mainframe punchcard", null, emptyJobsPage());

        scheduler.run();

        verify(digestMailer, never()).send(eq("zero-matches@example.com"), anyList(), anyBoolean());

        List<DigestRunEntity> runs = digestRunRepository.list("userId", USER_0005);
        assertThat(runs).noneMatch(r -> "sent".equals(r.getStatus()));
        assertThat(runs).noneMatch(r -> "failed".equals(r.getStatus()));
    }

    // TC-09b
    @Test
    @DisplayName("TC-09b: scheduler run does not resend to user 0004 (already sent this ISO week)")
    void schedulerRunDoesNotResendToUserAlreadySentThisWeek() {
        stubEmailBatch();
        stubInterestProfile(USER_0001, """
                { "userId": "e0000000-0000-0000-0000-000000000001",
                  "locations": ["Barcelona, Spain"], "companies": ["Acme Corp"],
                  "keywords": ["backend","java","developer"] }
                """);
        stubInterestProfile(USER_0002, """
                { "userId": "e0000000-0000-0000-0000-000000000002", "locations": [], "companies": [], "keywords": [] }
                """);
        stubInterestProfile(USER_0005, """
                { "userId": "e0000000-0000-0000-0000-000000000005",
                  "locations": [], "companies": [], "keywords": ["cobol","mainframe","punchcard"] }
                """);

        stubJobSearch("backend java developer", "Barcelona, Spain", jobsPage(6));
        stubJobSearch(null, null, jobsPage(10));
        stubJobSearch("cobol mainframe punchcard", null, emptyJobsPage());

        scheduler.run();

        // 0004 is filtered out before any outbound call (BR-6) — its email is never used.
        verify(digestMailer, never()).send(eq("already-sent@example.com"), anyList(), anyBoolean());

        // Other eligible users in the same run are still processed normally.
        verify(digestMailer, times(1)).send(eq("personalised@example.com"), anyList(), eq(true));
        verify(digestMailer, times(1)).send(eq("generic@example.com"), anyList(), eq(false));

        List<DigestRunEntity> runsFor0004 = digestRunRepository.list("userId", USER_0004);
        assertThat(runsFor0004).hasSize(1);
        assertThat(runsFor0004.get(0).getStatus()).isEqualTo("sent");
        assertThat(runsFor0004.get(0).getJobCount()).isEqualTo(4);
    }

    // TC-20b
    @Test
    @DisplayName("TC-20b: scheduler run excludes user 0006 (missing from email batch) without marking 'failed'")
    void schedulerRunExcludesUserMissingFromEmailBatchWithoutMarkingFailed() {
        stubEmailBatch();
        stubInterestProfile(USER_0001, """
                { "userId": "e0000000-0000-0000-0000-000000000001",
                  "locations": ["Barcelona, Spain"], "companies": ["Acme Corp"],
                  "keywords": ["backend","java","developer"] }
                """);
        stubInterestProfile(USER_0002, """
                { "userId": "e0000000-0000-0000-0000-000000000002", "locations": [], "companies": [], "keywords": [] }
                """);
        stubInterestProfile(USER_0005, """
                { "userId": "e0000000-0000-0000-0000-000000000005",
                  "locations": [], "companies": [], "keywords": ["cobol","mainframe","punchcard"] }
                """);
        // 0006 would succeed if reached — non-empty profile + matching jobs.
        stubInterestProfile(USER_0006, """
                { "userId": "e0000000-0000-0000-0000-000000000006",
                  "locations": ["Madrid, Spain"], "companies": [], "keywords": ["frontend"] }
                """);

        stubJobSearch("backend java developer", "Barcelona, Spain", jobsPage(6));
        stubJobSearch(null, null, jobsPage(10));
        stubJobSearch("cobol mainframe punchcard", null, emptyJobsPage());
        stubJobSearch("frontend", "Madrid, Spain", jobsPage(3));

        scheduler.run();

        List<DigestRunEntity> runsFor0006 = digestRunRepository.list("userId", USER_0006);
        assertThat(runsFor0006).noneMatch(r -> "failed".equals(r.getStatus()));

        // Other eligible users in the same run are still processed normally.
        verify(digestMailer, times(1)).send(eq("personalised@example.com"), anyList(), eq(true));
        verify(digestMailer, times(1)).send(eq("generic@example.com"), anyList(), eq(false));
    }

    // DG-C-29: weekly digest run creates zero in-app NotificationResponse rows for the
    // recipients of either flow (maps to AC-DG-3). Compares a before/after count of all
    // notifications for the personalised (0001) and generic (0002) recipients across the run.
    @Test
    @DisplayName("DG-C-29: weekly digest run creates zero in-app notification rows for personalised and generic recipients")
    void schedulerRunCreatesZeroInAppNotificationRowsForEitherFlow() {
        stubEmailBatch();
        stubInterestProfile(USER_0001, """
                { "userId": "e0000000-0000-0000-0000-000000000001",
                  "locations": ["Barcelona, Spain"], "companies": ["Acme Corp"],
                  "keywords": ["backend","java","developer"] }
                """);
        stubInterestProfile(USER_0002, """
                { "userId": "e0000000-0000-0000-0000-000000000002", "locations": [], "companies": [], "keywords": [] }
                """);
        stubInterestProfile(USER_0005, """
                { "userId": "e0000000-0000-0000-0000-000000000005",
                  "locations": [], "companies": [], "keywords": ["cobol","mainframe","punchcard"] }
                """);

        stubJobSearch("backend java developer", "Barcelona, Spain", jobsPage(6));
        stubJobSearch(null, null, jobsPage(10));
        stubJobSearch("cobol mainframe punchcard", null, emptyJobsPage());

        long beforePersonalised = notificationRepository.countByUserId(USER_0001, ReadStatusFilter.ALL);
        long beforeGeneric = notificationRepository.countByUserId(USER_0002, ReadStatusFilter.ALL);

        scheduler.run();

        // Sanity check the run actually exercised both flows (sent emails as in TC-21/TC-22).
        verify(digestMailer, times(1)).send(eq("personalised@example.com"), anyList(), eq(true));
        verify(digestMailer, times(1)).send(eq("generic@example.com"), anyList(), eq(false));

        long afterPersonalised = notificationRepository.countByUserId(USER_0001, ReadStatusFilter.ALL);
        long afterGeneric = notificationRepository.countByUserId(USER_0002, ReadStatusFilter.ALL);

        assertThat(afterPersonalised).isEqualTo(beforePersonalised);
        assertThat(afterGeneric).isEqualTo(beforeGeneric);
    }
}
