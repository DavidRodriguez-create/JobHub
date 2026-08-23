package com.davidcreate.jobhub.application.unit_tests.application.usecase;

import com.davidcreate.jobhub.application.application.port.in.ApplicationUseCase;
import com.davidcreate.jobhub.application.application.port.in.CreateApplicationCommand;
import com.davidcreate.jobhub.application.application.port.in.JobDetailsCommand;
import com.davidcreate.jobhub.application.application.port.in.ListApplicationsQuery;
import com.davidcreate.jobhub.application.application.port.in.UpdateApplicationStatusCommand;
import com.davidcreate.jobhub.application.application.port.in.DeleteAllApplicationsCommand;
import com.davidcreate.jobhub.application.application.port.in.UpdateApplicationCommand;
import com.davidcreate.jobhub.application.application.port.out.ApplicationRepository;
import com.davidcreate.jobhub.application.application.port.out.ApplicationTimelineRepository;
import com.davidcreate.jobhub.application.application.port.out.JobPostGateway;
import com.davidcreate.jobhub.application.application.port.out.JobPostSnapshotRepository;
import com.davidcreate.jobhub.application.application.port.out.UserJobPostRepository;
import com.davidcreate.jobhub.application.application.port.out.VerificationGateway;
import com.davidcreate.jobhub.application.application.usecase.ApplicationService;
import com.davidcreate.jobhub.application.domain.entity.Application;
import com.davidcreate.jobhub.application.domain.entity.ApplicationStats;
import com.davidcreate.jobhub.application.domain.entity.ApplicationSummaryView;
import com.davidcreate.jobhub.application.domain.entity.ApplicationView;
import com.davidcreate.jobhub.application.domain.entity.JobPostSnapshot;
import com.davidcreate.jobhub.application.domain.entity.UserJobPost;
import com.davidcreate.jobhub.application.domain.exception.ApplicationNotFoundException;
import com.davidcreate.jobhub.application.domain.exception.CrawledJobImmutableException;
import com.davidcreate.jobhub.application.domain.exception.DuplicateApplicationException;
import com.davidcreate.jobhub.application.domain.exception.InvalidVerificationException;
import com.davidcreate.jobhub.application.domain.exception.JobPostNotFoundException;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationService Unit Tests")
class ApplicationServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationTimelineRepository timelineRepository;
    @Mock UserJobPostRepository userJobPostRepository;
    @Mock JobPostSnapshotRepository snapshotRepository;
    @Mock JobPostGateway jobPostGateway;
    @Mock VerificationGateway verificationGateway;
    @InjectMocks ApplicationService service;

    private final UUID caller = UUID.randomUUID();

    @Test
    @DisplayName("create from crawled job-post inserts snapshot when contentHash is new")
    void createFromCrawledNewSnapshot() {
        UUID jobPostId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        var cmd = new CreateApplicationCommand(caller, jobPostId, null);
        var view = new JobPostGateway.JobPostView(jobPostId, "Dev", "https://x", "desc", "Madrid, Spain",
                "Acme Corp", "https://cdn.example/acme.png");
        var snapshot = JobPostSnapshot.builder()
                .id(snapId).jobPostId(jobPostId).title("Dev").url("https://x").location("Madrid, Spain")
                .company("Acme Corp").companyLogoUrl("https://cdn.example/acme.png").build();
        when(jobPostGateway.findById(jobPostId)).thenReturn(Optional.of(view));
        when(snapshotRepository.findByContentHash(any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(JobPostSnapshot.class))).thenReturn(snapshot);
        when(snapshotRepository.findOneById(snapId)).thenReturn(Optional.of(snapshot));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplicationView result = service.create(cmd);

        ArgumentCaptor<Application> cap = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(caller);
        assertThat(cap.getValue().getJobPostSnapshotId()).isEqualTo(snapId);
        assertThat(cap.getValue().getUserJobPostId()).isNull();
        assertThat(cap.getValue().getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(result.job().title()).isEqualTo("Dev");
        assertThat(result.job().location()).isEqualTo("Madrid, Spain");
    }

    @Test
    @DisplayName("create from crawled reuses existing snapshot by content hash")
    void createFromCrawledReusesSnapshot() {
        UUID jobPostId = UUID.randomUUID();
        UUID existingSnapId = UUID.randomUUID();
        var cmd = new CreateApplicationCommand(caller, jobPostId, null);
        var view = new JobPostGateway.JobPostView(jobPostId, "Dev", "https://x", "desc", "Madrid, Spain",
                "Acme Corp", "https://cdn.example/acme.png");
        var snapshot = JobPostSnapshot.builder().id(existingSnapId).title("Dev").location("Madrid, Spain").build();
        when(jobPostGateway.findById(jobPostId)).thenReturn(Optional.of(view));
        when(snapshotRepository.findByContentHash(any())).thenReturn(Optional.of(snapshot));
        when(snapshotRepository.findOneById(existingSnapId)).thenReturn(Optional.of(snapshot));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(cmd);

        verify(snapshotRepository, never()).save(any());
        ArgumentCaptor<Application> cap = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(cap.capture());
        assertThat(cap.getValue().getJobPostSnapshotId()).isEqualTo(existingSnapId);
    }

    @Test
    @DisplayName("AS244-U-06: resolveSnapshot returns the existing content-hash-matched snapshot unmodified, "
            + "never overwriting its null companyLogoUrl with a newly-fetched non-null value (no implicit backfill)")
    void createFromCrawledDoesNotBackfillExistingSnapshotsLogo() {
        UUID jobPostId = UUID.randomUUID();
        UUID existingSnapId = UUID.randomUUID();
        var cmd = new CreateApplicationCommand(caller, jobPostId, null);
        // job-service now has a logo for this post, but the existing snapshot row predates capture.
        var view = new JobPostGateway.JobPostView(jobPostId, "Dev", "https://x", "desc", "Madrid, Spain",
                "Acme Corp", "https://cdn.example/now-available.png");
        var existingSnapshot = JobPostSnapshot.builder()
                .id(existingSnapId).title("Dev").location("Madrid, Spain")
                .company(null).companyLogoUrl(null).build();
        when(jobPostGateway.findById(jobPostId)).thenReturn(Optional.of(view));
        when(snapshotRepository.findByContentHash(any())).thenReturn(Optional.of(existingSnapshot));
        when(snapshotRepository.findOneById(existingSnapId)).thenReturn(Optional.of(existingSnapshot));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplicationView result = service.create(cmd);

        verify(snapshotRepository, never()).save(any());
        assertThat(result.job().company()).isNull();
        assertThat(result.job().companyLogoUrl()).isNull();
    }

    @Test
    @DisplayName("AS244-U-03: resolveSnapshot persists a new snapshot with both companyName and companyLogoUrl "
            + "set when the view has both populated (first-seen content hash)")
    void createFromCrawledPersistsCompanyAndLogo() {
        UUID jobPostId = UUID.randomUUID();
        var cmd = new CreateApplicationCommand(caller, jobPostId, null);
        var view = new JobPostGateway.JobPostView(jobPostId, "Dev", "https://x", "desc", "Madrid, Spain",
                "Acme Corp", "https://cdn.example/acme.png");
        when(jobPostGateway.findById(jobPostId)).thenReturn(Optional.of(view));
        when(snapshotRepository.findByContentHash(any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(JobPostSnapshot.class))).thenAnswer(inv -> {
            JobPostSnapshot s = inv.getArgument(0);
            return s.toBuilder().id(UUID.randomUUID()).build();
        });
        when(snapshotRepository.findOneById(any())).thenAnswer(inv ->
                Optional.of(JobPostSnapshot.builder().id(inv.getArgument(0)).title("Dev").location("Madrid, Spain")
                        .company("Acme Corp").companyLogoUrl("https://cdn.example/acme.png").build()));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(cmd);

        ArgumentCaptor<JobPostSnapshot> cap = ArgumentCaptor.forClass(JobPostSnapshot.class);
        verify(snapshotRepository).save(cap.capture());
        assertThat(cap.getValue().getCompany()).isEqualTo("Acme Corp");
        assertThat(cap.getValue().getCompanyLogoUrl()).isEqualTo("https://cdn.example/acme.png");
    }

    @Test
    @DisplayName("AS244-U-04: resolveSnapshot persists companyLogoUrl=null when the source post has no logo, "
            + "independently of company still being populated")
    void createFromCrawledPersistsNullLogoWithCompany() {
        UUID jobPostId = UUID.randomUUID();
        var cmd = new CreateApplicationCommand(caller, jobPostId, null);
        var view = new JobPostGateway.JobPostView(jobPostId, "Dev", "https://x", "desc", "Madrid, Spain",
                "Acme Corp", null);
        when(jobPostGateway.findById(jobPostId)).thenReturn(Optional.of(view));
        when(snapshotRepository.findByContentHash(any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(JobPostSnapshot.class))).thenAnswer(inv -> {
            JobPostSnapshot s = inv.getArgument(0);
            return s.toBuilder().id(UUID.randomUUID()).build();
        });
        when(snapshotRepository.findOneById(any())).thenAnswer(inv ->
                Optional.of(JobPostSnapshot.builder().id(inv.getArgument(0)).title("Dev").location("Madrid, Spain")
                        .company("Acme Corp").companyLogoUrl(null).build()));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(cmd);

        ArgumentCaptor<JobPostSnapshot> cap = ArgumentCaptor.forClass(JobPostSnapshot.class);
        verify(snapshotRepository).save(cap.capture());
        assertThat(cap.getValue().getCompany()).isEqualTo("Acme Corp");
        assertThat(cap.getValue().getCompanyLogoUrl()).isNull();
    }

    @Test
    @DisplayName("AS244-U-05: resolveSnapshot persists company=null when companyName is null on the view "
            + "(defends against partial upstream data, never throws), independently of companyLogoUrl")
    void createFromCrawledPersistsNullCompanyDefensively() {
        UUID jobPostId = UUID.randomUUID();
        var cmd = new CreateApplicationCommand(caller, jobPostId, null);
        var view = new JobPostGateway.JobPostView(jobPostId, "Dev", "https://x", "desc", "Madrid, Spain",
                null, null);
        when(jobPostGateway.findById(jobPostId)).thenReturn(Optional.of(view));
        when(snapshotRepository.findByContentHash(any())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(JobPostSnapshot.class))).thenAnswer(inv -> {
            JobPostSnapshot s = inv.getArgument(0);
            return s.toBuilder().id(UUID.randomUUID()).build();
        });
        when(snapshotRepository.findOneById(any())).thenAnswer(inv ->
                Optional.of(JobPostSnapshot.builder().id(inv.getArgument(0)).title("Dev").location("Madrid, Spain")
                        .company(null).companyLogoUrl(null).build()));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplicationView result = service.create(cmd);

        ArgumentCaptor<JobPostSnapshot> cap = ArgumentCaptor.forClass(JobPostSnapshot.class);
        verify(snapshotRepository).save(cap.capture());
        assertThat(cap.getValue().getCompany()).isNull();
        assertThat(cap.getValue().getCompanyLogoUrl()).isNull();
        assertThat(result.job().title()).isEqualTo("Dev");
    }

    @Test
    @DisplayName("create throws JobPostNotFound when gateway returns empty")
    void createCrawledMissingPost() {
        UUID jobPostId = UUID.randomUUID();
        var cmd = new CreateApplicationCommand(caller, jobPostId, null);
        when(jobPostGateway.findById(jobPostId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(JobPostNotFoundException.class);
    }

    @Test
    @DisplayName("create rejects a duplicate application for the same job post")
    void createRejectsDuplicate() {
        UUID jobPostId = UUID.randomUUID();
        var cmd = new CreateApplicationCommand(caller, jobPostId, null);
        var view = new JobPostGateway.JobPostView(jobPostId, "Dev", "https://x", "desc", "Madrid, Spain",
                "Acme Corp", "https://cdn.example/acme.png");
        when(jobPostGateway.findById(jobPostId)).thenReturn(Optional.of(view));
        when(applicationRepository.existsByUserAndJobPost(caller, jobPostId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(DuplicateApplicationException.class);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("create rejects when both jobPostId and jobDetails are set")
    void createRejectsBoth() {
        var cmd = new CreateApplicationCommand(caller, UUID.randomUUID(),
                new JobDetailsCommand("T", null, null, null));
        assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(
                com.davidcreate.jobhub.application.domain.exception.ValidationException.class);
    }

    @Test
    @DisplayName("create rejects when neither jobPostId nor jobDetails is set")
    void createRejectsNeither() {
        var cmd = new CreateApplicationCommand(caller, null, null);
        assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(
                com.davidcreate.jobhub.application.domain.exception.ValidationException.class);
    }

    @Test
    @DisplayName("create from manual jobDetails stores a user-job-post owned by caller")
    void createFromManual() {
        UUID ujpId = UUID.randomUUID();
        var details = new JobDetailsCommand("Manual Dev", "Acme", "https://m", "Remote");
        var cmd = new CreateApplicationCommand(caller, null, details);
        var userPost = UserJobPost.builder()
                .id(ujpId).userId(caller).title("Manual Dev").company("Acme").url("https://m").location("Remote").build();
        when(userJobPostRepository.save(any(UserJobPost.class))).thenReturn(userPost);
        when(userJobPostRepository.findOneById(ujpId)).thenReturn(Optional.of(userPost));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplicationView result = service.create(cmd);

        ArgumentCaptor<Application> cap = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(cap.capture());
        assertThat(cap.getValue().getUserJobPostId()).isEqualTo(ujpId);
        assertThat(cap.getValue().getJobPostSnapshotId()).isNull();
        assertThat(result.job().company()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("create from manual jobDetails rejects when neither title nor url is provided")
    void createManualRequiresTitleOrUrl() {
        var cmd = new CreateApplicationCommand(caller, null,
                new JobDetailsCommand("  ", "Acme", null, "Remote"));
        assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(
                com.davidcreate.jobhub.application.domain.exception.ValidationException.class);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("get throws when application missing")
    void getMissing() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findOneById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(caller, id)).isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    @DisplayName("get treats another user's application as not found (no existence leak)")
    void getNotOwner() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findOneById(id))
                .thenReturn(Optional.of(Application.builder().id(id).userId(UUID.randomUUID()).build()));
        assertThatThrownBy(() -> service.get(caller, id)).isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    @DisplayName("list applies pagination defaults and queries repository")
    void listDefaults() {
        var q = new ListApplicationsQuery(caller, null, -1, 0);
        when(applicationRepository.listByUser(caller, null, 0, 20)).thenReturn(List.of());
        when(applicationRepository.countByUser(caller, null)).thenReturn(0L);

        ApplicationUseCase.PagedResult<ApplicationView> res = service.list(q);
        assertThat(res.items()).isEmpty();
        assertThat(res.total()).isZero();
    }

    @Test
    @DisplayName("list clamps page size to 100")
    void listClampsSize() {
        var q = new ListApplicationsQuery(caller, ApplicationStatus.APPLIED, 0, 999);
        when(applicationRepository.listByUser(caller, ApplicationStatus.APPLIED, 0, 100)).thenReturn(List.of());
        when(applicationRepository.countByUser(caller, ApplicationStatus.APPLIED)).thenReturn(0L);

        service.list(q);
        verify(applicationRepository).listByUser(caller, ApplicationStatus.APPLIED, 0, 100);
    }

    @Test
    @DisplayName("updateStatus sets endedAt for terminal status")
    void updateStatusTerminal() {
        UUID id = UUID.randomUUID();
        UUID ujpId = UUID.randomUUID();
        var app = Application.builder().id(id).userId(caller).userJobPostId(ujpId)
                .status(ApplicationStatus.APPLIED).build();
        when(applicationRepository.findOneById(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userJobPostRepository.findOneById(ujpId))
                .thenReturn(Optional.of(UserJobPost.builder().id(ujpId).title("T").build()));

        var view = service.updateStatus(new UpdateApplicationStatusCommand(caller, id, ApplicationStatus.REJECTED));

        assertThat(view.application().getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(view.application().getEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateStatus leaves endedAt null for non-terminal status")
    void updateStatusNonTerminal() {
        UUID id = UUID.randomUUID();
        UUID ujpId = UUID.randomUUID();
        var app = Application.builder().id(id).userId(caller).userJobPostId(ujpId)
                .status(ApplicationStatus.APPLIED).build();
        when(applicationRepository.findOneById(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userJobPostRepository.findOneById(ujpId))
                .thenReturn(Optional.of(UserJobPost.builder().id(ujpId).title("T").build()));

        var view = service.updateStatus(new UpdateApplicationStatusCommand(caller, id, ApplicationStatus.INTERVIEWING));

        assertThat(view.application().getStatus()).isEqualTo(ApplicationStatus.INTERVIEWING);
        assertThat(view.application().getEndedAt()).isNull();
    }

    @Test
    @DisplayName("updateJob patches the linked user-job-post for a manual entry")
    void updateJobManual() {
        UUID id = UUID.randomUUID();
        UUID ujpId = UUID.randomUUID();
        var app = Application.builder().id(id).userId(caller).userJobPostId(ujpId).build();
        var existing = UserJobPost.builder()
                .id(ujpId).userId(caller).title("Old").company("OldCo").location("Madrid").build();
        when(applicationRepository.findOneById(id)).thenReturn(Optional.of(app));
        when(userJobPostRepository.findOneById(ujpId)).thenReturn(Optional.of(existing));
        when(userJobPostRepository.save(any(UserJobPost.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateJob(caller, id, new JobDetailsCommand("New", null, null, null));

        ArgumentCaptor<UserJobPost> cap = ArgumentCaptor.forClass(UserJobPost.class);
        verify(userJobPostRepository).save(cap.capture());
        assertThat(cap.getValue().getTitle()).isEqualTo("New");
        assertThat(cap.getValue().getCompany()).isEqualTo("OldCo");
        assertThat(cap.getValue().getLocation()).isEqualTo("Madrid");
    }

    @Test
    @DisplayName("updateJob rejects crawled-job applications with 409")
    void updateJobCrawledImmutable() {
        UUID id = UUID.randomUUID();
        var app = Application.builder().id(id).userId(caller).jobPostSnapshotId(UUID.randomUUID()).build();
        when(applicationRepository.findOneById(id)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.updateJob(caller, id, new JobDetailsCommand("x", null, null, null)))
                .isInstanceOf(CrawledJobImmutableException.class);
        verify(userJobPostRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete throws when application missing")
    void deleteMissing() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findOneById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(caller, id)).isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    @DisplayName("delete treats another user's application as not found")
    void deleteNotOwner() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findOneById(id))
                .thenReturn(Optional.of(Application.builder().id(id).userId(UUID.randomUUID()).build()));
        assertThatThrownBy(() -> service.delete(caller, id)).isInstanceOf(ApplicationNotFoundException.class);
        verify(applicationRepository, never()).removeById(any());
    }

    @Test
    @DisplayName("delete removes when caller owns application")
    void deleteOk() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findOneById(id))
                .thenReturn(Optional.of(Application.builder().id(id).userId(caller).build()));
        service.delete(caller, id);
        verify(applicationRepository).removeById(id);
    }

    @Test
    @DisplayName("stats fills missing statuses with zero and sums total")
    void statsAggregates() {
        Map<ApplicationStatus, Long> partial = new EnumMap<>(ApplicationStatus.class);
        partial.put(ApplicationStatus.APPLIED, 3L);
        partial.put(ApplicationStatus.REJECTED, 1L);
        when(applicationRepository.countByUserGroupedByStatus(caller)).thenReturn(partial);

        ApplicationStats stats = service.stats(caller);

        assertThat(stats.getTotal()).isEqualTo(4L);
        assertThat(stats.getByStatus())
                .containsEntry(ApplicationStatus.APPLIED, 3L)
                .containsEntry(ApplicationStatus.REJECTED, 1L)
                .containsEntry(ApplicationStatus.INTERVIEWING, 0L);
    }

    @Test
    @DisplayName("update applies only the supplied fields and records nextStep")
    void updateAppliesProvidedFields() {
        UUID id = UUID.randomUUID();
        UUID ujpId = UUID.randomUUID();
        var app = Application.builder().id(id).userId(caller).userJobPostId(ujpId)
                .status(ApplicationStatus.APPLIED).build();
        when(applicationRepository.findOneById(id)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userJobPostRepository.findOneById(ujpId))
                .thenReturn(Optional.of(UserJobPost.builder().id(ujpId).title("T").build()));
        when(timelineRepository.findByApplication(id)).thenReturn(List.of());

        var cmd = new UpdateApplicationCommand(caller, id, "my notes", null, "Jane Recruiter",
                "https://portal.example", true, "Tech interview", LocalDate.of(2026, 6, 15), null);
        service.update(cmd);

        ArgumentCaptor<Application> cap = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(cap.capture());
        assertThat(cap.getValue().getNotes()).isEqualTo("my notes");
        assertThat(cap.getValue().getContact()).isEqualTo("Jane Recruiter");
        assertThat(cap.getValue().getPortalUrl()).isEqualTo("https://portal.example");
        assertThat(cap.getValue().getNextStepLabel()).isEqualTo("Tech interview");
        assertThat(cap.getValue().getNextStepDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("deleteAll consumes the code then wipes timeline, applications, and job posts")
    void deleteAllConsumesThenWipes() {
        var cmd = new DeleteAllApplicationsCommand(caller, UUID.randomUUID(), "123456", "Bearer x");

        service.deleteAll(cmd);

        verify(verificationGateway).consumeDeleteAllApplications("Bearer x", cmd.verificationId(), "123456");
        verify(timelineRepository).removeByUser(caller);
        verify(applicationRepository).removeAllByUser(caller);
        verify(userJobPostRepository).removeAllByUser(caller);
    }

    @Test
    @DisplayName("deleteAll aborts (deletes nothing) when verification fails")
    void deleteAllAbortsOnInvalidCode() {
        var cmd = new DeleteAllApplicationsCommand(caller, UUID.randomUUID(), "000000", "Bearer x");
        doThrow(new InvalidVerificationException("bad")).when(verificationGateway)
                .consumeDeleteAllApplications(any(), any(), any());

        assertThatThrownBy(() -> service.deleteAll(cmd)).isInstanceOf(InvalidVerificationException.class);
        verify(applicationRepository, never()).removeAllByUser(any());
        verify(timelineRepository, never()).removeByUser(any());
    }

    @Test
    @DisplayName("statsHistory returns exactly the requested number of months")
    void statsHistoryMonthCount() {
        when(applicationRepository.monthlyStatusCounts(eq(caller), any())).thenReturn(List.of());

        assertThat(service.statsHistory(caller, 3)).hasSize(3);
    }

    @Test
    @DisplayName("ApplicationStatus.isTerminal reflects terminal states")
    void statusTerminal() {
        assertThat(ApplicationStatus.REJECTED.isTerminal()).isTrue();
        assertThat(ApplicationStatus.ACCEPTED.isTerminal()).isTrue();
        assertThat(ApplicationStatus.WITHDRAWN.isTerminal()).isTrue();
        assertThat(ApplicationStatus.GHOSTED.isTerminal()).isTrue();
        assertThat(ApplicationStatus.APPLIED.isTerminal()).isFalse();
        assertThat(ApplicationStatus.SCREENING.isTerminal()).isFalse();
        assertThat(ApplicationStatus.INTERVIEWING.isTerminal()).isFalse();
        assertThat(ApplicationStatus.OFFERED.isTerminal()).isFalse();
    }

    // ── resolveApplicationSummaries (story #207, ticket #217, ADR 0014) ────────

    @Test
    @DisplayName("AS-U-01: resolves a batch of crawled-job-backed ids, each with company/jobTitle populated")
    void resolveApplicationSummariesAllExistCrawled() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Application app1 = crawledApplication(id1, "snap-1", "Senior Backend Developer", "Acme Corp");
        Application app2 = crawledApplication(id2, "snap-2", "Frontend Engineer", "Globex");
        when(applicationRepository.findAllByIds(List.of(id1, id2))).thenReturn(List.of(app1, app2));
        when(snapshotRepository.findOneById(app1.getJobPostSnapshotId()))
                .thenReturn(Optional.of(snapshot("snap-1", "Senior Backend Developer", "Acme Corp")));
        when(snapshotRepository.findOneById(app2.getJobPostSnapshotId()))
                .thenReturn(Optional.of(snapshot("snap-2", "Frontend Engineer", "Globex")));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(id1, id2));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ApplicationSummaryView::applicationId).containsExactlyInAnyOrder(id1, id2);
        ApplicationSummaryView view1 = result.stream().filter(v -> v.applicationId().equals(id1)).findFirst().orElseThrow();
        assertThat(view1.company()).isEqualTo("Acme Corp");
        assertThat(view1.jobTitle()).isEqualTo("Senior Backend Developer");
    }

    @Test
    @DisplayName("AS-U-02: resolves a manual-entry-backed id from the UserJobPost title/company columns")
    void resolveApplicationSummariesManualEntry() {
        UUID id = UUID.randomUUID();
        UUID userJobPostId = UUID.randomUUID();
        Application app = manualApplication(id, userJobPostId);
        when(applicationRepository.findAllByIds(List.of(id))).thenReturn(List.of(app));
        when(userJobPostRepository.findOneById(userJobPostId))
                .thenReturn(Optional.of(UserJobPost.builder()
                        .id(userJobPostId).title("Onsite Interview Role").company("Initech").build()));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(id));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).company()).isEqualTo("Initech");
        assertThat(result.get(0).jobTitle()).isEqualTo("Onsite Interview Role");
    }

    @Test
    @DisplayName("AS-U-03: an id with no matching application is omitted, not returned as a null entry")
    void resolveApplicationSummariesOmitsNotFound() {
        UUID found = UUID.randomUUID();
        UUID notFound = UUID.randomUUID();
        Application app = crawledApplication(found, "snap-x", "Java Developer", "Umbrella Corp");
        when(applicationRepository.findAllByIds(List.of(found, notFound))).thenReturn(List.of(app));
        when(snapshotRepository.findOneById(app.getJobPostSnapshotId()))
                .thenReturn(Optional.of(snapshot("snap-x", "Java Developer", "Umbrella Corp")));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(found, notFound));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).applicationId()).isEqualTo(found);
    }

    @Test
    @DisplayName("AS-U-04: per the frozen contract (id-only, no caller scoping), an existing id resolves "
            + "regardless of which user owns it, same as the stale/upcoming-next-steps endpoints")
    void resolveApplicationSummariesResolvesAcrossOwners() {
        UUID otherUsersAppId = UUID.randomUUID();
        Application app = crawledApplication(otherUsersAppId, "snap-other", "Final Round Candidate", "Initech");
        when(applicationRepository.findAllByIds(List.of(otherUsersAppId))).thenReturn(List.of(app));
        when(snapshotRepository.findOneById(app.getJobPostSnapshotId()))
                .thenReturn(Optional.of(snapshot("snap-other", "Final Round Candidate", "Initech")));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(otherUsersAppId));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).applicationId()).isEqualTo(otherUsersAppId);
    }

    @Test
    @DisplayName("AS-U-05: duplicate ids in the input are resolved once each, no duplicate output entries")
    void resolveApplicationSummariesDeduplicatesInput() {
        UUID id = UUID.randomUUID();
        Application app = crawledApplication(id, "snap-dup", "Java Developer", "Globex");
        when(applicationRepository.findAllByIds(List.of(id))).thenReturn(List.of(app));
        when(snapshotRepository.findOneById(app.getJobPostSnapshotId()))
                .thenReturn(Optional.of(snapshot("snap-dup", "Java Developer", "Globex")));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(id, id));

        assertThat(result).hasSize(1);
        verify(applicationRepository).findAllByIds(List.of(id));
    }

    @Test
    @DisplayName("AS-U-06: all requested ids unresolvable returns an empty list, not an exception")
    void resolveApplicationSummariesAllUnresolvableReturnsEmpty() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(applicationRepository.findAllByIds(List.of(id1, id2))).thenReturn(List.of());

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(id1, id2));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("AS-U-07: every resolved entry has all three fields non-null, never a partially-null entry")
    void resolveApplicationSummariesNeverPartiallyNull() {
        UUID id = UUID.randomUUID();
        Application app = crawledApplication(id, "snap-full", "Platform Engineer", "Stark Industries");
        when(applicationRepository.findAllByIds(List.of(id))).thenReturn(List.of(app));
        when(snapshotRepository.findOneById(app.getJobPostSnapshotId()))
                .thenReturn(Optional.of(snapshot("snap-full", "Platform Engineer", "Stark Industries")));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(id));

        assertThat(result).hasSize(1);
        ApplicationSummaryView view = result.get(0);
        assertThat(view.applicationId()).isNotNull();
        assertThat(view.company()).isNotNull();
        assertThat(view.jobTitle()).isNotNull();
    }

    // ── companyLogoUrl threading (story #244, ADR 0015) ─────────────────────────

    @Test
    @DisplayName("AS244-U-07: resolveJob for a crawled application returns a JobInfo whose companyLogoUrl "
            + "equals the snapshot's stored value (populated case)")
    void resolveApplicationSummariesCrawledWithLogo() {
        UUID id = UUID.randomUUID();
        Application app = crawledApplication(id, "snap-logo", "Platform Engineer", "Stark Industries");
        when(applicationRepository.findAllByIds(List.of(id))).thenReturn(List.of(app));
        when(snapshotRepository.findOneById(app.getJobPostSnapshotId()))
                .thenReturn(Optional.of(snapshotWithLogo("snap-logo", "Platform Engineer", "Stark Industries",
                        "https://cdn.example/stark.png")));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(id));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).companyLogoUrl()).isEqualTo("https://cdn.example/stark.png");
    }

    @Test
    @DisplayName("AS244-U-08: resolveJob for an application backed by a pre-fix crawled snapshot "
            + "(companyLogoUrl=null on the row) returns companyLogoUrl==null and company==null, "
            + "title() still populated - the exact S4 data shape")
    void resolveApplicationSummariesCrawledWithoutLogo() {
        UUID id = UUID.randomUUID();
        Application app = crawledApplication(id, "snap-nologo", "Platform Engineer", "Stark Industries");
        when(applicationRepository.findAllByIds(List.of(id))).thenReturn(List.of(app));
        when(snapshotRepository.findOneById(app.getJobPostSnapshotId()))
                .thenReturn(Optional.of(JobPostSnapshot.builder()
                        .id(UUID.nameUUIDFromBytes("snap-nologo".getBytes()))
                        .title("Platform Engineer").company(null).companyLogoUrl(null).build()));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(id));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).companyLogoUrl()).isNull();
        assertThat(result.get(0).company()).isNull();
        assertThat(result.get(0).jobTitle()).isEqualTo("Platform Engineer");
    }

    @Test
    @DisplayName("AS244-U-09: resolveJob for a manual-entry application returns companyLogoUrl==null always "
            + "(manual entries have no source post to capture a logo from)")
    void resolveApplicationSummariesManualEntryHasNullLogo() {
        UUID id = UUID.randomUUID();
        UUID userJobPostId = UUID.randomUUID();
        Application app = manualApplication(id, userJobPostId);
        when(applicationRepository.findAllByIds(List.of(id))).thenReturn(List.of(app));
        when(userJobPostRepository.findOneById(userJobPostId))
                .thenReturn(Optional.of(UserJobPost.builder()
                        .id(userJobPostId).title("Onsite Interview Role").company("Initech").build()));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(id));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).companyLogoUrl()).isNull();
    }

    @Test
    @DisplayName("AS244-U-10: resolveApplicationSummaries for a resolvable id whose snapshot has a populated "
            + "companyLogoUrl returns a view with companyLogoUrl set to that value")
    void resolveApplicationSummariesSetsLogoFromSnapshot() {
        UUID id = UUID.randomUUID();
        Application app = crawledApplication(id, "snap-summary-logo", "Senior Backend Developer", "Acme Corp");
        when(applicationRepository.findAllByIds(List.of(id))).thenReturn(List.of(app));
        when(snapshotRepository.findOneById(app.getJobPostSnapshotId()))
                .thenReturn(Optional.of(snapshotWithLogo("snap-summary-logo", "Senior Backend Developer",
                        "Acme Corp", "https://cdn.example/acme.png")));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(id));

        assertThat(result).hasSize(1);
        ApplicationSummaryView view = result.get(0);
        assertThat(view.companyLogoUrl()).isEqualTo("https://cdn.example/acme.png");
        assertThat(view.company()).isEqualTo("Acme Corp");
        assertThat(view.jobTitle()).isEqualTo("Senior Backend Developer");
    }

    @Test
    @DisplayName("AS244-U-11: resolveApplicationSummaries for a resolvable id whose snapshot has "
            + "companyLogoUrl=null returns a view with companyLogoUrl==null, company/jobTitle still populated")
    void resolveApplicationSummariesNullLogoRegression() {
        UUID id = UUID.randomUUID();
        Application app = crawledApplication(id, "snap-summary-nologo", "Frontend Engineer", "Globex");
        when(applicationRepository.findAllByIds(List.of(id))).thenReturn(List.of(app));
        when(snapshotRepository.findOneById(app.getJobPostSnapshotId()))
                .thenReturn(Optional.of(snapshot("snap-summary-nologo", "Frontend Engineer", "Globex")));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(id));

        assertThat(result).hasSize(1);
        ApplicationSummaryView view = result.get(0);
        assertThat(view.companyLogoUrl()).isNull();
        assertThat(view.company()).isEqualTo("Globex");
        assertThat(view.jobTitle()).isEqualTo("Frontend Engineer");
    }

    @Test
    @DisplayName("AS244-U-12: resolveApplicationSummaries for an unresolvable id continues to omit it entirely "
            + "(regression: the new field does not change the existing omit-not-found rule from #207)")
    void resolveApplicationSummariesStillOmitsUnresolvable() {
        UUID found = UUID.randomUUID();
        UUID notFound = UUID.randomUUID();
        Application app = crawledApplication(found, "snap-summary-omit", "Java Developer", "Umbrella Corp");
        when(applicationRepository.findAllByIds(List.of(found, notFound))).thenReturn(List.of(app));
        when(snapshotRepository.findOneById(app.getJobPostSnapshotId()))
                .thenReturn(Optional.of(snapshotWithLogo("snap-summary-omit", "Java Developer", "Umbrella Corp",
                        "https://cdn.example/umbrella.png")));

        List<ApplicationSummaryView> result = service.resolveApplicationSummaries(List.of(found, notFound));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).applicationId()).isEqualTo(found);
        assertThat(result.get(0).companyLogoUrl()).isEqualTo("https://cdn.example/umbrella.png");
    }

    private Application crawledApplication(UUID id, String snapshotId, String title, String company) {
        return Application.builder()
                .id(id)
                .userId(UUID.randomUUID())
                .jobPostSnapshotId(UUID.nameUUIDFromBytes(snapshotId.getBytes()))
                .status(ApplicationStatus.APPLIED)
                .build();
    }

    private Application manualApplication(UUID id, UUID userJobPostId) {
        return Application.builder()
                .id(id)
                .userId(UUID.randomUUID())
                .userJobPostId(userJobPostId)
                .status(ApplicationStatus.APPLIED)
                .build();
    }

    private JobPostSnapshot snapshot(String idSeed, String title, String company) {
        return JobPostSnapshot.builder()
                .id(UUID.nameUUIDFromBytes(idSeed.getBytes()))
                .title(title)
                .company(company)
                .build();
    }

    private JobPostSnapshot snapshotWithLogo(String idSeed, String title, String company, String companyLogoUrl) {
        return JobPostSnapshot.builder()
                .id(UUID.nameUUIDFromBytes(idSeed.getBytes()))
                .title(title)
                .company(company)
                .companyLogoUrl(companyLogoUrl)
                .build();
    }
}
