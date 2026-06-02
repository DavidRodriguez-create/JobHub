package com.davidcreate.jobhub.application.application.port.out;

import com.davidcreate.jobhub.application.domain.entity.Application;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import com.davidcreate.jobhub.application.domain.valueobject.NextDeadline;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository {

    Optional<Application> findOneById(UUID id);

    /** True if the user already has an application linked to a snapshot of this job post. */
    boolean existsByUserAndJobPost(UUID userId, UUID jobPostId);

    List<Application> listByUser(UUID userId, ApplicationStatus statusFilter, int page, int size);

    long countByUser(UUID userId, ApplicationStatus statusFilter);

    Application save(Application application);

    void removeById(UUID id);

    void removeAllByUser(UUID userId);

    Map<ApplicationStatus, Long> countByUserGroupedByStatus(UUID userId);

    long countByUserCreatedSince(UUID userId, OffsetDateTime since);

    Optional<NextDeadline> earliestUpcomingNextStep(UUID userId, LocalDate today);

    List<MonthlyStatusCount> monthlyStatusCounts(UUID userId, OffsetDateTime since);

    record MonthlyStatusCount(int year, int month, ApplicationStatus status, long count) {
    }
}
