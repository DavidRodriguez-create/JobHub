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
        var view = new JobPostGateway.JobPostView(jobPostId, "Dev", "https://x", "desc", "Madrid, Spain");
        var snapshot = JobPostSnapshot.builder()
                .id(snapId).jobPostId(jobPostId).title("Dev").url("https://x").location("Madrid, Spain").build();
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
        var view = new JobPostGateway.JobPostView(jobPostId, "Dev", "https://x", "desc", "Madrid, Spain");
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
        var view = new JobPostGateway.JobPostView(jobPostId, "Dev", "https://x", "desc", "Madrid, Spain");
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
}
