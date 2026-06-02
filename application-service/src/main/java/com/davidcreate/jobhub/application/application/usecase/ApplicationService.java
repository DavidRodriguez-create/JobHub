package com.davidcreate.jobhub.application.application.usecase;

import com.davidcreate.jobhub.application.application.port.in.ApplicationUseCase;
import com.davidcreate.jobhub.application.application.port.in.CreateApplicationCommand;
import com.davidcreate.jobhub.application.application.port.in.DeleteAllApplicationsCommand;
import com.davidcreate.jobhub.application.application.port.in.JobDetailsCommand;
import com.davidcreate.jobhub.application.application.port.in.ListApplicationsQuery;
import com.davidcreate.jobhub.application.application.port.in.UpdateApplicationCommand;
import com.davidcreate.jobhub.application.application.port.in.UpdateApplicationStatusCommand;
import com.davidcreate.jobhub.application.application.port.out.ApplicationRepository;
import com.davidcreate.jobhub.application.application.port.out.ApplicationTimelineRepository;
import com.davidcreate.jobhub.application.application.port.out.JobPostGateway;
import com.davidcreate.jobhub.application.application.port.out.JobPostSnapshotRepository;
import com.davidcreate.jobhub.application.application.port.out.UserJobPostRepository;
import com.davidcreate.jobhub.application.application.port.out.VerificationGateway;
import com.davidcreate.jobhub.application.domain.entity.Application;
import com.davidcreate.jobhub.application.domain.entity.ApplicationStats;
import com.davidcreate.jobhub.application.domain.entity.ApplicationView;
import com.davidcreate.jobhub.application.domain.entity.JobPostSnapshot;
import com.davidcreate.jobhub.application.domain.entity.MonthlyStats;
import com.davidcreate.jobhub.application.domain.entity.UserJobPost;
import com.davidcreate.jobhub.application.domain.exception.ApplicationNotFoundException;
import com.davidcreate.jobhub.application.domain.exception.CrawledJobImmutableException;
import com.davidcreate.jobhub.application.domain.exception.DuplicateApplicationException;
import com.davidcreate.jobhub.application.domain.exception.JobPostNotFoundException;
import com.davidcreate.jobhub.application.domain.exception.UserJobPostNotFoundException;
import com.davidcreate.jobhub.application.domain.exception.ValidationException;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import com.davidcreate.jobhub.application.domain.valueobject.JobInfo;
import com.davidcreate.jobhub.application.domain.valueobject.NextDeadline;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ApplicationService implements ApplicationUseCase {

    private final ApplicationRepository applicationRepository;
    private final ApplicationTimelineRepository timelineRepository;
    private final UserJobPostRepository userJobPostRepository;
    private final JobPostSnapshotRepository snapshotRepository;
    private final JobPostGateway jobPostGateway;
    private final VerificationGateway verificationGateway;

    @Override
    @Transactional
    public ApplicationView create(CreateApplicationCommand cmd) {
        boolean fromCrawled = cmd.jobPostId() != null;
        boolean fromManual = cmd.jobDetails() != null;
        if (fromCrawled == fromManual) {
            throw new ValidationException("exactly one of jobPostId or jobDetails must be provided");
        }

        UUID snapshotId = null;
        UUID userPostId = null;

        if (fromCrawled) {
            JobPostGateway.JobPostView view = jobPostGateway.findById(cmd.jobPostId())
                    .orElseThrow(() -> new JobPostNotFoundException(cmd.jobPostId()));
            if (applicationRepository.existsByUserAndJobPost(cmd.callerId(), cmd.jobPostId())) {
                throw new DuplicateApplicationException(cmd.jobPostId());
            }
            snapshotId = resolveSnapshot(view).getId();
        } else {
            userPostId = createUserJobPost(cmd.callerId(), cmd.jobDetails()).getId();
        }

        OffsetDateTime appliedAt = OffsetDateTime.now();
        Application saved = applicationRepository.save(Application.builder()
                .userId(cmd.callerId())
                .jobPostSnapshotId(snapshotId)
                .userJobPostId(userPostId)
                .jobPostId(cmd.jobPostId())
                .status(ApplicationStatus.APPLIED)
                .appliedAt(appliedAt)
                .build());
        timelineRepository.append(saved.getId(), ApplicationStatus.APPLIED, saved.getAppliedAt());
        return new ApplicationView(saved, resolveJob(saved));
    }

    @Override
    public PagedResult<ApplicationView> list(ListApplicationsQuery q) {
        int page = Math.max(0, q.page());
        int size = q.size() <= 0 ? 20 : Math.min(q.size(), 100);
        List<ApplicationView> items = applicationRepository.listByUser(q.callerId(), q.statusFilter(), page, size)
                .stream()
                .map(app -> new ApplicationView(app, resolveJob(app)))
                .toList();
        long total = applicationRepository.countByUser(q.callerId(), q.statusFilter());
        return new PagedResult<>(items, total);
    }

    @Override
    public ApplicationView get(UUID callerId, UUID applicationId) {
        Application app = loadOwned(callerId, applicationId);
        return new ApplicationView(app, resolveJob(app), timelineRepository.findByApplication(applicationId));
    }

    @Override
    @Transactional
    public ApplicationView update(UpdateApplicationCommand cmd) {
        Application app = loadOwned(cmd.callerId(), cmd.applicationId());
        Application.ApplicationBuilder b = app.toBuilder();
        if (cmd.notes() != null) b.notes(cmd.notes());
        if (cmd.appliedAt() != null) b.appliedAt(cmd.appliedAt());
        if (cmd.contact() != null) b.contact(cmd.contact());
        if (cmd.portalUrl() != null) b.portalUrl(cmd.portalUrl());
        if (cmd.nextStepProvided()) {
            b.nextStepLabel(cmd.nextStepLabel())
                    .nextStepDate(cmd.nextStepDate())
                    .nextStepReminderAt(cmd.nextStepReminderAt());
        }
        Application updated = applicationRepository.save(b.build());
        return new ApplicationView(updated, resolveJob(updated), timelineRepository.findByApplication(updated.getId()));
    }

    @Override
    @Transactional
    public ApplicationView updateStatus(UpdateApplicationStatusCommand cmd) {
        Application app = loadOwned(cmd.callerId(), cmd.applicationId());
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime endedAt = cmd.status().isTerminal() ? now : null;
        Application updated = applicationRepository.save(app.toBuilder()
                .status(cmd.status())
                .endedAt(endedAt)
                .build());
        timelineRepository.append(updated.getId(), cmd.status(), now);
        return new ApplicationView(updated, resolveJob(updated));
    }

    @Override
    @Transactional
    public ApplicationView updateJob(UUID callerId, UUID applicationId, JobDetailsCommand cmd) {
        Application app = loadOwned(callerId, applicationId);
        if (app.getJobPostSnapshotId() != null) {
            throw new CrawledJobImmutableException();
        }
        UserJobPost existing = userJobPostRepository.findOneById(app.getUserJobPostId())
                .orElseThrow(() -> new UserJobPostNotFoundException(app.getUserJobPostId()));

        UserJobPost.UserJobPostBuilder b = existing.toBuilder();
        if (cmd.title() != null && !cmd.title().isBlank()) b.title(cmd.title().trim());
        if (cmd.company() != null) b.company(blankToNull(cmd.company()));
        if (cmd.url() != null) b.url(blankToNull(cmd.url()));
        if (cmd.location() != null) b.location(blankToNull(cmd.location()));
        userJobPostRepository.save(b.build());

        return new ApplicationView(app, resolveJob(app));
    }

    @Override
    @Transactional
    public void delete(UUID callerId, UUID applicationId) {
        loadOwned(callerId, applicationId);
        applicationRepository.removeById(applicationId);
    }

    @Override
    @Transactional
    public void deleteAll(DeleteAllApplicationsCommand cmd) {
        verificationGateway.consumeDeleteAllApplications(cmd.bearerToken(), cmd.verificationId(), cmd.code());
        timelineRepository.removeByUser(cmd.callerId());
        applicationRepository.removeAllByUser(cmd.callerId());
        userJobPostRepository.removeAllByUser(cmd.callerId());
    }

    @Override
    public ApplicationStats stats(UUID callerId) {
        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(applicationRepository.countByUserGroupedByStatus(callerId));
        for (ApplicationStatus s : ApplicationStatus.values()) {
            byStatus.putIfAbsent(s, 0L);
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long applied = byStatus.get(ApplicationStatus.APPLIED);
        long activeCount = byStatus.entrySet().stream()
                .filter(e -> !e.getKey().isTerminal())
                .mapToLong(Map.Entry::getValue)
                .sum();

        long monthlyNew = applicationRepository.countByUserCreatedSince(callerId, startOfCurrentMonth());
        double responseRate = total == 0 ? 0.0 : round1((total - applied) * 100.0 / total);
        double avgReplyDays = round1(timelineRepository.avgReplyDays(callerId));
        double passThrough = total == 0 ? 0.0 : round1(timelineRepository.countReachedOffer(callerId) * 100.0 / total);
        NextDeadline nextDeadline = applicationRepository
                .earliestUpcomingNextStep(callerId, LocalDate.now(ZoneOffset.UTC))
                .orElse(null);

        return ApplicationStats.builder()
                .total(total)
                .byStatus(byStatus)
                .activeCount((int) activeCount)
                .monthlyNew((int) monthlyNew)
                .responseRate(responseRate)
                .avgReplyDays(avgReplyDays)
                .passThrough(passThrough)
                .nextDeadline(nextDeadline)
                .build();
    }

    @Override
    public List<MonthlyStats> statsHistory(UUID callerId, int months) {
        int span = Math.max(1, Math.min(months, 24));
        LocalDate firstMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(span - 1L);
        OffsetDateTime since = firstMonth.atStartOfDay().atOffset(ZoneOffset.UTC);

        Map<String, Map<ApplicationStatus, Long>> byMonth = new LinkedHashMap<>();
        for (int i = 0; i < span; i++) {
            LocalDate m = firstMonth.plusMonths(i);
            byMonth.put(monthKey(m.getYear(), m.getMonthValue()), new EnumMap<>(ApplicationStatus.class));
        }
        for (ApplicationRepository.MonthlyStatusCount c : applicationRepository.monthlyStatusCounts(callerId, since)) {
            Map<ApplicationStatus, Long> bucket = byMonth.get(monthKey(c.year(), c.month()));
            if (bucket != null) {
                bucket.merge(c.status(), c.count(), Long::sum);
            }
        }

        List<MonthlyStats> history = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            LocalDate m = firstMonth.plusMonths(i);
            history.add(new MonthlyStats(m.getYear(), m.getMonthValue(),
                    byMonth.get(monthKey(m.getYear(), m.getMonthValue()))));
        }
        return history;
    }

    private UserJobPost createUserJobPost(UUID callerId, JobDetailsCommand details) {
        boolean hasTitle = details.title() != null && !details.title().isBlank();
        boolean hasUrl = details.url() != null && !details.url().isBlank();
        if (!hasTitle && !hasUrl) {
            throw new ValidationException("jobDetails must include at least a title or a url");
        }
        return userJobPostRepository.save(UserJobPost.builder()
                .userId(callerId)
                .title(hasTitle ? details.title().trim() : null)
                .company(blankToNull(details.company()))
                .url(blankToNull(details.url()))
                .location(blankToNull(details.location()))
                .build());
    }

    private JobPostSnapshot resolveSnapshot(JobPostGateway.JobPostView view) {
        String hash = ContentHasher.hash(view.title(), null, view.location(), view.description());
        return snapshotRepository.findByContentHash(hash).orElseGet(() ->
                snapshotRepository.save(JobPostSnapshot.builder()
                        .jobPostId(view.id())
                        .contentHash(hash)
                        .title(view.title())
                        .url(view.url())
                        .location(view.location())
                        .build()));
    }

    private JobInfo resolveJob(Application app) {
        if (app.getJobPostSnapshotId() != null) {
            JobPostSnapshot s = snapshotRepository.findOneById(app.getJobPostSnapshotId())
                    .orElseThrow(() -> new IllegalStateException("snapshot missing for application " + app.getId()));
            return new JobInfo(s.getTitle(), s.getCompany(), s.getLocation(), s.getUrl());
        }
        UserJobPost u = userJobPostRepository.findOneById(app.getUserJobPostId())
                .orElseThrow(() -> new UserJobPostNotFoundException(app.getUserJobPostId()));
        return new JobInfo(u.getTitle(), u.getCompany(), u.getLocation(), u.getUrl());
    }

    private Application loadOwned(UUID callerId, UUID applicationId) {
        Application app = applicationRepository.findOneById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        if (!app.getUserId().equals(callerId)) {
            // Don't leak existence of another user's application — treat as not found.
            throw new ApplicationNotFoundException(applicationId);
        }
        return app;
    }

    private static OffsetDateTime startOfCurrentMonth() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static String monthKey(int year, int month) {
        return year + "-" + month;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
