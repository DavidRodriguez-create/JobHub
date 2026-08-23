package com.davidcreate.jobhub.application.application.port.in;

import com.davidcreate.jobhub.application.domain.entity.Application;
import com.davidcreate.jobhub.application.domain.entity.ApplicationStats;
import com.davidcreate.jobhub.application.domain.entity.ApplicationSummaryView;
import com.davidcreate.jobhub.application.domain.entity.ApplicationView;
import com.davidcreate.jobhub.application.domain.entity.MonthlyStats;
import com.davidcreate.jobhub.application.domain.entity.StaleApplicationView;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;

import java.util.List;
import java.util.UUID;

public interface ApplicationUseCase {

    ApplicationView create(CreateApplicationCommand command);

    PagedResult<ApplicationView> list(ListApplicationsQuery query);

    ApplicationView get(UUID callerId, UUID applicationId);

    ApplicationView update(UpdateApplicationCommand command);

    ApplicationView updateStatus(UpdateApplicationStatusCommand command);

    ApplicationView updateJob(UUID callerId, UUID applicationId, JobDetailsCommand command);

    void delete(UUID callerId, UUID applicationId);

    void deleteAll(DeleteAllApplicationsCommand command);

    ApplicationStats stats(UUID callerId);

    List<MonthlyStats> statsHistory(UUID callerId, int months);

    /**
     * Returns non-terminal applications where {@code updatedAt} is older than {@code days} days.
     * Called by the internal ghosted-alert endpoint (service-to-service, no user JWT).
     */
    List<StaleApplicationView> listStaleApplications(int days);

    /**
     * Updates an application's status without owner-scoping.
     * Called by the internal ghosted-alert endpoint (service-to-service, no user JWT).
     * Returns the updated application so callers can extract userId for notification.
     *
     * @throws com.davidcreate.jobhub.application.domain.exception.ApplicationNotFoundException if not found
     * @throws com.davidcreate.jobhub.application.domain.exception.AlreadyTerminalException if current status is already terminal
     */
    Application updateApplicationStatusInternal(UUID applicationId, ApplicationStatus status);

    /**
     * True if the application with {@code applicationId} belongs to {@code userId}.
     * Returns false for both "not found" and "found but different owner" to avoid leaking existence.
     * Called by the internal ownership-check endpoint (ADR 0011 section 7).
     */
    boolean isOwnedByUser(UUID applicationId, UUID userId);

    /**
     * Resolves a batch of application ids to their compact display summary (company + job
     * title), for notification-service's enrich-at-read path (ADR 0014). Duplicate ids in the
     * input are resolved once each. Ids that do not resolve to an existing application are
     * omitted from the result, never returned as a null/placeholder entry.
     */
    List<ApplicationSummaryView> resolveApplicationSummaries(List<UUID> ids);

    record PagedResult<T>(List<T> items, long total) {
    }
}
