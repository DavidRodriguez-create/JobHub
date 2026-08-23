package com.davidcreate.jobhub.notification.unit_tests.domain.service;

import com.davidcreate.jobhub.notification.domain.model.DigestJob;
import com.davidcreate.jobhub.notification.domain.model.DigestRun;
import com.davidcreate.jobhub.notification.domain.model.DigestRunStatus;
import com.davidcreate.jobhub.notification.domain.model.InterestProfile;
import com.davidcreate.jobhub.notification.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.notification.domain.port.out.DigestMailer;
import com.davidcreate.jobhub.notification.domain.port.out.DigestRunRepository;
import com.davidcreate.jobhub.notification.domain.port.out.InterestProfileGateway;
import com.davidcreate.jobhub.notification.domain.port.out.JobSearchGateway;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import com.davidcreate.jobhub.notification.domain.port.out.UserEmailGateway;
import com.davidcreate.jobhub.notification.domain.service.WeeklyDigestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeeklyDigestService Unit Tests")
class WeeklyDigestServiceTest {

    @Mock NotificationPreferencesRepository preferencesRepository;
    @Mock DigestRunRepository digestRunRepository;
    @Mock InterestProfileGateway interestProfileGateway;
    @Mock UserEmailGateway userEmailGateway;
    @Mock JobSearchGateway jobSearchGateway;
    @Mock DigestMailer digestMailer;

    WeeklyDigestService service;

    @BeforeEach
    void setUp() {
        service = new WeeklyDigestService(
                preferencesRepository,
                digestRunRepository,
                interestProfileGateway,
                userEmailGateway,
                jobSearchGateway,
                digestMailer);
    }

    private DigestJob job(String title, String company, String location) {
        return DigestJob.builder()
                .id(UUID.randomUUID())
                .title(title)
                .companyName(company)
                .location(location)
                .companyLogoUrl(URI.create("https://example.com/logo.png"))
                .build();
    }

    private List<DigestJob> jobs(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> job("Job " + i, "Company " + i, "Remote"))
                .toList();
    }

    // TC-01
    @Test
    @DisplayName("sends_personalized_digest_for_user_with_history")
    void sendsPersonalizedDigestForUserWithHistory() {
        UUID u1 = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(u1));
        when(digestRunRepository.hasSentThisWeek(u1)).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(u1))).thenReturn(Map.of(u1, "u1@example.com"));
        when(interestProfileGateway.fetch(u1)).thenReturn(InterestProfile.builder()
                .userId(u1)
                .locations(List.of("Barcelona, Spain"))
                .companies(List.of("Acme Corp"))
                .keywords(List.of("backend", "java", "developer"))
                .build());
        List<DigestJob> sixJobs = jobs(6);
        when(jobSearchGateway.search(any())).thenReturn(sixJobs);

        service.run();

        ArgumentCaptor<JobSearchQuery> queryCaptor = ArgumentCaptor.forClass(JobSearchQuery.class);
        verify(jobSearchGateway, times(1)).search(queryCaptor.capture());
        JobSearchQuery query = queryCaptor.getValue();
        assertThat(query.getKeyword()).isEqualTo("backend java developer");
        assertThat(query.getLocations()).containsExactly("Barcelona, Spain");
        assertThat(query.getPostedWithin()).isEqualTo("week");
        assertThat(query.getSort()).isEqualTo("newest");
        assertThat(query.getSize()).isEqualTo(10);

        verify(digestMailer, times(1)).send(eq("u1@example.com"), eq(sixJobs), eq(true));

        ArgumentCaptor<DigestRun> runCaptor = ArgumentCaptor.forClass(DigestRun.class);
        verify(digestRunRepository, times(1)).save(runCaptor.capture());
        DigestRun savedRun = runCaptor.getValue();
        assertThat(savedRun.getUserId()).isEqualTo(u1);
        assertThat(savedRun.getStatus()).isEqualTo(DigestRunStatus.SENT);
        assertThat(savedRun.getJobCount()).isEqualTo(6);
    }

    // TC-02
    @Test
    @DisplayName("builds_job_query_from_top_3_keywords_and_all_locations")
    void buildsJobQueryFromTop3KeywordsAndAllLocations() {
        UUID u2 = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(u2));
        when(digestRunRepository.hasSentThisWeek(u2)).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(u2))).thenReturn(Map.of(u2, "u2@example.com"));
        when(interestProfileGateway.fetch(u2)).thenReturn(InterestProfile.builder()
                .userId(u2)
                .locations(List.of("Barcelona, Spain", "Madrid, Spain", "Remote"))
                .companies(List.of())
                .keywords(List.of("backend", "java", "developer", "spring", "microservices"))
                .build());
        when(jobSearchGateway.search(any())).thenReturn(jobs(1));

        service.run();

        ArgumentCaptor<JobSearchQuery> queryCaptor = ArgumentCaptor.forClass(JobSearchQuery.class);
        verify(jobSearchGateway, times(1)).search(queryCaptor.capture());
        JobSearchQuery query = queryCaptor.getValue();
        assertThat(query.getKeyword()).isEqualTo("backend java developer");
        assertThat(query.getLocations()).containsExactly("Barcelona, Spain", "Madrid, Spain", "Remote");
    }

    // TC-03
    @Test
    @DisplayName("falls_back_to_generic_digest_for_user_with_no_history")
    void fallsBackToGenericDigestForUserWithNoHistory() {
        UUID u3 = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(u3));
        when(digestRunRepository.hasSentThisWeek(u3)).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(u3))).thenReturn(Map.of(u3, "u3@example.com"));
        when(interestProfileGateway.fetch(u3)).thenReturn(InterestProfile.builder()
                .userId(u3)
                .locations(List.of())
                .companies(List.of())
                .keywords(List.of())
                .build());
        List<DigestJob> tenJobs = jobs(10);
        when(jobSearchGateway.search(any())).thenReturn(tenJobs);

        service.run();

        ArgumentCaptor<JobSearchQuery> queryCaptor = ArgumentCaptor.forClass(JobSearchQuery.class);
        verify(jobSearchGateway, times(1)).search(queryCaptor.capture());
        JobSearchQuery query = queryCaptor.getValue();
        assertThat(query.getKeyword()).isNullOrEmpty();
        assertThat(query.getLocations()).isNullOrEmpty();
        assertThat(query.getPostedWithin()).isEqualTo("week");
        assertThat(query.getSort()).isEqualTo("newest");
        assertThat(query.getSize()).isEqualTo(10);

        verify(digestMailer, times(1)).send(eq("u3@example.com"), eq(tenJobs), eq(false));

        ArgumentCaptor<DigestRun> runCaptor = ArgumentCaptor.forClass(DigestRun.class);
        verify(digestRunRepository, times(1)).save(runCaptor.capture());
        DigestRun savedRun = runCaptor.getValue();
        assertThat(savedRun.getStatus()).isEqualTo(DigestRunStatus.SENT);
        assertThat(savedRun.getJobCount()).isEqualTo(10);
    }

    // TC-04
    @Test
    @DisplayName("non_empty_profile_with_only_locations_uses_personalised_branch")
    void nonEmptyProfileWithOnlyLocationsUsesPersonalisedBranch() {
        UUID u4 = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(u4));
        when(digestRunRepository.hasSentThisWeek(u4)).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(u4))).thenReturn(Map.of(u4, "u4@example.com"));
        when(interestProfileGateway.fetch(u4)).thenReturn(InterestProfile.builder()
                .userId(u4)
                .locations(List.of("Remote"))
                .companies(List.of())
                .keywords(List.of())
                .build());
        when(jobSearchGateway.search(any())).thenReturn(jobs(2));

        service.run();

        ArgumentCaptor<JobSearchQuery> queryCaptor = ArgumentCaptor.forClass(JobSearchQuery.class);
        verify(jobSearchGateway, times(1)).search(queryCaptor.capture());
        JobSearchQuery query = queryCaptor.getValue();
        assertThat(query.getLocations()).containsExactly("Remote");
        assertThat(query.getKeyword()).isNullOrEmpty();

        verify(digestMailer, times(1)).send(eq("u4@example.com"), any(), eq(true));
    }

    // TC-05
    @Test
    @DisplayName("excludes_opted_out_user_from_candidate_list")
    void excludesOptedOutUserFromCandidateList() {
        UUID u5a = UUID.randomUUID();
        UUID u5b = UUID.randomUUID();
        // The candidate-query port itself only returns opted-in users — u5b never appears.
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(u5a));
        when(digestRunRepository.hasSentThisWeek(u5a)).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(u5a))).thenReturn(Map.of(u5a, "u5a@example.com"));
        when(interestProfileGateway.fetch(u5a)).thenReturn(InterestProfile.builder()
                .userId(u5a).locations(List.of()).companies(List.of()).keywords(List.of()).build());
        when(jobSearchGateway.search(any())).thenReturn(jobs(1));

        service.run();

        verify(interestProfileGateway, never()).fetch(eq(u5b));
        verify(digestMailer, never()).send(eq("u5b"), any(), any(Boolean.class));
        verify(digestRunRepository, never()).save(argThat(run -> run.getUserId().equals(u5b)));
    }

    // TC-06
    @Test
    @DisplayName("user_with_no_preferences_row_is_treated_as_opted_in")
    void userWithNoPreferencesRowIsTreatedAsOptedIn() {
        UUID u6 = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(u6));
        when(digestRunRepository.hasSentThisWeek(u6)).thenReturn(false);
        when(interestProfileGateway.fetch(u6)).thenReturn(InterestProfile.builder()
                .userId(u6).locations(List.of()).companies(List.of()).keywords(List.of()).build());
        when(jobSearchGateway.search(any())).thenReturn(jobs(3));
        when(userEmailGateway.fetchEmails(Set.of(u6))).thenReturn(Map.of(u6, "u6@example.com"));

        service.run();

        verify(digestMailer, times(1)).send(eq("u6@example.com"), any(), any(Boolean.class));
        ArgumentCaptor<DigestRun> runCaptor = ArgumentCaptor.forClass(DigestRun.class);
        verify(digestRunRepository, times(1)).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getStatus()).isEqualTo(DigestRunStatus.SENT);
        assertThat(runCaptor.getValue().getJobCount()).isEqualTo(3);
    }

    // TC-07
    @Test
    @DisplayName("skips_user_with_zero_matching_jobs_without_sending_email")
    void skipsUserWithZeroMatchingJobsWithoutSendingEmail() {
        UUID u7 = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(u7));
        when(digestRunRepository.hasSentThisWeek(u7)).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(u7))).thenReturn(Map.of(u7, "u7@example.com"));
        when(interestProfileGateway.fetch(u7)).thenReturn(InterestProfile.builder()
                .userId(u7)
                .locations(List.of("Nowhere, Nowhereland"))
                .companies(List.of())
                .keywords(List.of("cobol", "mainframe", "punchcard"))
                .build());
        when(jobSearchGateway.search(any())).thenReturn(List.of());

        service.run();

        verify(digestMailer, never()).send(eq("u7@example.com"), any(), any(Boolean.class));
        verify(digestRunRepository, never()).save(argThat(run ->
                run.getUserId().equals(u7) && run.getStatus() == DigestRunStatus.SENT));
        verify(digestRunRepository, never()).save(argThat(run ->
                run.getUserId().equals(u7) && run.getStatus() == DigestRunStatus.FAILED));
    }

    // TC-08
    @Test
    @DisplayName("zero_match_skip_does_not_count_as_already_sent_for_double_send_check")
    void zeroMatchSkipDoesNotCountAsAlreadySentForDoubleSendCheck() {
        UUID u8 = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(u8));
        when(digestRunRepository.hasSentThisWeek(u8)).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(u8))).thenReturn(Map.of(u8, "u8@example.com"));
        when(interestProfileGateway.fetch(u8)).thenReturn(InterestProfile.builder()
                .userId(u8)
                .locations(List.of("Nowhere"))
                .companies(List.of())
                .keywords(List.of("cobol"))
                .build());

        // First run: zero matches.
        when(jobSearchGateway.search(any())).thenReturn(List.of());
        service.run();

        // Second run (same-week retry): now 2 jobs match.
        when(jobSearchGateway.search(any())).thenReturn(jobs(2));
        service.run();

        verify(digestMailer, times(1)).send(eq("u8@example.com"), any(), any(Boolean.class));
        ArgumentCaptor<DigestRun> runCaptor = ArgumentCaptor.forClass(DigestRun.class);
        verify(digestRunRepository, times(2)).save(runCaptor.capture());
        List<DigestRun> savedRuns = runCaptor.getAllValues();
        assertThat(savedRuns.get(0).getStatus()).isEqualTo(DigestRunStatus.SKIPPED);
        DigestRun secondRun = savedRuns.get(1);
        assertThat(secondRun.getStatus()).isEqualTo(DigestRunStatus.SENT);
        assertThat(secondRun.getJobCount()).isEqualTo(2);
    }

    // TC-09
    @Test
    @DisplayName("skips_user_already_sent_this_iso_week")
    void skipsUserAlreadySentThisIsoWeek() {
        UUID u9 = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(u9));
        when(digestRunRepository.hasSentThisWeek(u9)).thenReturn(true);

        service.run();

        verify(interestProfileGateway, never()).fetch(u9);
        verify(jobSearchGateway, never()).search(any());
        verify(digestMailer, never()).send(any(), any(), any(Boolean.class));
        verify(digestRunRepository, never()).save(any());
    }

    // TC-10
    @Test
    @DisplayName("other_users_processed_normally_when_one_user_already_sent")
    void otherUsersProcessedNormallyWhenOneUserAlreadySent() {
        UUID u9 = UUID.randomUUID();
        UUID u10 = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(u9, u10));
        when(digestRunRepository.hasSentThisWeek(u9)).thenReturn(true);
        when(digestRunRepository.hasSentThisWeek(u10)).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(u10))).thenReturn(Map.of(u10, "u10@example.com"));
        when(interestProfileGateway.fetch(u10)).thenReturn(InterestProfile.builder()
                .userId(u10).locations(List.of()).companies(List.of()).keywords(List.of()).build());
        when(jobSearchGateway.search(any())).thenReturn(jobs(3));

        service.run();

        verify(interestProfileGateway, never()).fetch(u9);
        verify(digestMailer, never()).send(eq("u9@example.com"), any(), any(Boolean.class));

        verify(digestMailer, times(1)).send(eq("u10@example.com"), any(), any(Boolean.class));
        ArgumentCaptor<DigestRun> runCaptor = ArgumentCaptor.forClass(DigestRun.class);
        verify(digestRunRepository, times(1)).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getUserId()).isEqualTo(u10);
        assertThat(runCaptor.getValue().getStatus()).isEqualTo(DigestRunStatus.SENT);
        assertThat(runCaptor.getValue().getJobCount()).isEqualTo(3);
    }

    // TC-11
    @Test
    @DisplayName("records_failed_digest_run_when_interest_profile_fetch_throws")
    void recordsFailedDigestRunWhenInterestProfileFetchThrows() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(a, b, c));
        when(digestRunRepository.hasSentThisWeek(any())).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(a, b, c))).thenReturn(Map.of(
                a, "a@example.com", b, "b@example.com", c, "c@example.com"));

        when(interestProfileGateway.fetch(a)).thenThrow(new RuntimeException("application-service timeout"));
        InterestProfile emptyProfile = InterestProfile.builder()
                .locations(List.of()).companies(List.of()).keywords(List.of()).build();
        when(interestProfileGateway.fetch(b)).thenReturn(emptyProfile);
        when(interestProfileGateway.fetch(c)).thenReturn(emptyProfile);
        when(jobSearchGateway.search(any())).thenReturn(jobs(2));

        service.run();

        verify(digestMailer, never()).send(eq("a@example.com"), any(), any(Boolean.class));

        ArgumentCaptor<DigestRun> runCaptor = ArgumentCaptor.forClass(DigestRun.class);
        verify(digestRunRepository, times(3)).save(runCaptor.capture());
        List<DigestRun> savedRuns = runCaptor.getAllValues();

        DigestRun aRun = savedRuns.stream().filter(r -> r.getUserId().equals(a)).findFirst().orElseThrow();
        assertThat(aRun.getStatus()).isEqualTo(DigestRunStatus.FAILED);
        assertThat(aRun.getErrorMessage()).isNotBlank();

        DigestRun bRun = savedRuns.stream().filter(r -> r.getUserId().equals(b)).findFirst().orElseThrow();
        assertThat(bRun.getStatus()).isEqualTo(DigestRunStatus.SENT);

        DigestRun cRun = savedRuns.stream().filter(r -> r.getUserId().equals(c)).findFirst().orElseThrow();
        assertThat(cRun.getStatus()).isEqualTo(DigestRunStatus.SENT);

        verify(digestMailer, times(1)).send(eq("b@example.com"), any(), any(Boolean.class));
        verify(digestMailer, times(1)).send(eq("c@example.com"), any(), any(Boolean.class));
    }

    // TC-12
    @Test
    @DisplayName("run_completes_without_throwing_when_a_per_user_step_fails")
    void runCompletesWithoutThrowingWhenAPerUserStepFails() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(a, b, c));
        when(digestRunRepository.hasSentThisWeek(any())).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(a, b, c))).thenReturn(Map.of(
                a, "a@example.com", b, "b@example.com", c, "c@example.com"));

        when(interestProfileGateway.fetch(a)).thenThrow(new RuntimeException("application-service timeout"));
        InterestProfile emptyProfile = InterestProfile.builder()
                .locations(List.of()).companies(List.of()).keywords(List.of()).build();
        when(interestProfileGateway.fetch(b)).thenReturn(emptyProfile);
        when(interestProfileGateway.fetch(c)).thenReturn(emptyProfile);
        when(jobSearchGateway.search(any())).thenReturn(jobs(2));

        assertDoesNotThrow(() -> service.run());
    }

    // TC-13
    @Test
    @DisplayName("whole_run_logs_single_systemic_error_when_auth_service_unreachable")
    void wholeRunLogsSingleSystemicErrorWhenAuthServiceUnreachable() {
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(d, e));
        when(digestRunRepository.hasSentThisWeek(any())).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(d, e)))
                .thenThrow(new RuntimeException("auth-service unreachable"));

        service.run();

        verify(interestProfileGateway, never()).fetch(any());
        verify(jobSearchGateway, never()).search(any());
        verify(digestMailer, never()).send(any(), any(), any(Boolean.class));
        verify(digestRunRepository, never()).save(argThat(run -> run.getStatus() == DigestRunStatus.SENT));
    }

    // TC-14
    @Test
    @DisplayName("whole_run_failure_writes_failed_rows_with_shared_cause_message")
    void wholeRunFailureWritesFailedRowsWithSharedCauseMessage() {
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(d, e));
        when(digestRunRepository.hasSentThisWeek(any())).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(d, e)))
                .thenThrow(new RuntimeException("auth-service unreachable"));

        service.run();

        ArgumentCaptor<DigestRun> runCaptor = ArgumentCaptor.forClass(DigestRun.class);
        verify(digestRunRepository, times(2)).save(runCaptor.capture());
        for (DigestRun run : runCaptor.getAllValues()) {
            assertThat(run.getStatus()).isEqualTo(DigestRunStatus.FAILED);
            assertThat(run.getErrorMessage()).containsIgnoringCase("auth-service");
            assertThat(run.getStatus()).isNotEqualTo(DigestRunStatus.SENT);
        }
    }

    // TC-15
    @Test
    @DisplayName("whole_run_failure_does_not_propagate_exception_to_scheduler")
    void wholeRunFailureDoesNotPropagateExceptionToScheduler() {
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();
        when(preferencesRepository.findWeeklyDigestCandidateUserIds()).thenReturn(List.of(d, e));
        when(digestRunRepository.hasSentThisWeek(any())).thenReturn(false);
        when(userEmailGateway.fetchEmails(Set.of(d, e)))
                .thenThrow(new RuntimeException("auth-service unreachable"));

        assertDoesNotThrow(() -> service.run());
    }
}
