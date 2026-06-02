package com.davidcreate.jobhub.application.application.port.in;

import com.davidcreate.jobhub.application.domain.entity.ApplicationStats;
import com.davidcreate.jobhub.application.domain.entity.ApplicationView;
import com.davidcreate.jobhub.application.domain.entity.MonthlyStats;

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

    record PagedResult<T>(List<T> items, long total) {
    }
}
