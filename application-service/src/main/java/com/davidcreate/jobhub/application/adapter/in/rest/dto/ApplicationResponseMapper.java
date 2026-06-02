package com.davidcreate.jobhub.application.adapter.in.rest.dto;

import com.davidcreate.jobhub.application.application.port.in.ApplicationUseCase.PagedResult;
import com.davidcreate.jobhub.application.contract.model.ApplicationPage;
import com.davidcreate.jobhub.application.contract.model.ApplicationResponse;
import com.davidcreate.jobhub.application.contract.model.ApplicationStatsResponse;
import com.davidcreate.jobhub.application.contract.model.JobSummary;
import com.davidcreate.jobhub.application.contract.model.MonthlyCount;
import com.davidcreate.jobhub.application.contract.model.NextDeadline;
import com.davidcreate.jobhub.application.contract.model.NextStep;
import com.davidcreate.jobhub.application.domain.entity.Application;
import com.davidcreate.jobhub.application.domain.entity.ApplicationStats;
import com.davidcreate.jobhub.application.domain.entity.ApplicationView;
import com.davidcreate.jobhub.application.domain.entity.MonthlyStats;
import com.davidcreate.jobhub.application.domain.valueobject.JobInfo;
import com.davidcreate.jobhub.application.domain.valueobject.TimelineEntry;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps domain reads into the generated contract response models.
 */
public final class ApplicationResponseMapper {

    private ApplicationResponseMapper() {
    }

    public static ApplicationResponse toResponse(ApplicationView view) {
        Application a = view.application();
        ApplicationResponse response = new ApplicationResponse()
                .id(a.getId())
                .jobPostId(a.getJobPostId())
                .jobPostSnapshotId(a.getJobPostSnapshotId())
                .status(ApplicationStatusMapper.toContract(a.getStatus()))
                .appliedAt(a.getAppliedAt())
                .endedAt(a.getEndedAt())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .job(toJobSummary(view.job()))
                .notes(a.getNotes())
                .contact(a.getContact())
                .portalUrl(toUri(a.getPortalUrl()));
        if (a.hasNextStep()) {
            response.nextStep(new NextStep()
                    .label(a.getNextStepLabel())
                    .date(a.getNextStepDate())
                    .reminderAt(a.getNextStepReminderAt()));
        }
        if (view.timeline() != null) {
            response.timeline(view.timeline().stream().map(ApplicationResponseMapper::toTimelineEntry).toList());
        }
        return response;
    }

    public static ApplicationPage toPage(PagedResult<ApplicationView> result, int page, int size) {
        int effectiveSize = size <= 0 ? 20 : Math.min(size, 100);
        long total = result.total();
        int totalPages = effectiveSize == 0 ? 0 : (int) Math.ceil((double) total / effectiveSize);
        return new ApplicationPage()
                .content(result.items().stream().map(ApplicationResponseMapper::toResponse).toList())
                .page(Math.max(0, page))
                .size(effectiveSize)
                .totalElements(total)
                .totalPages(totalPages);
    }

    public static ApplicationStatsResponse toStats(ApplicationStats stats) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        stats.getByStatus().forEach((status, count) -> byStatus.put(status.dbValue(), count));
        ApplicationStatsResponse response = new ApplicationStatsResponse()
                .total(stats.getTotal())
                .byStatus(byStatus)
                .activeCount(stats.getActiveCount())
                .monthlyNew(stats.getMonthlyNew())
                .responseRate(stats.getResponseRate())
                .avgReplyDays(stats.getAvgReplyDays())
                .passThrough(stats.getPassThrough());
        if (stats.getNextDeadline() != null) {
            response.nextDeadline(new NextDeadline()
                    .date(stats.getNextDeadline().date())
                    .applicationId(stats.getNextDeadline().applicationId()));
        }
        return response;
    }

    public static List<MonthlyCount> toHistory(List<MonthlyStats> history) {
        return history.stream().map(ApplicationResponseMapper::toMonthlyCount).toList();
    }

    private static MonthlyCount toMonthlyCount(MonthlyStats m) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        m.byStatus().forEach((status, count) -> byStatus.put(status.dbValue(), count));
        return new MonthlyCount()
                .year(m.year())
                .month(m.month())
                .byStatus(byStatus);
    }

    private static com.davidcreate.jobhub.application.contract.model.TimelineEntry toTimelineEntry(TimelineEntry e) {
        return new com.davidcreate.jobhub.application.contract.model.TimelineEntry()
                .status(ApplicationStatusMapper.toContract(e.status()))
                .occurredAt(e.occurredAt());
    }

    private static JobSummary toJobSummary(JobInfo job) {
        return new JobSummary()
                .title(job.title())
                .company(job.company())
                .location(job.location())
                .url(toUri(job.url()));
    }

    private static URI toUri(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return URI.create(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
