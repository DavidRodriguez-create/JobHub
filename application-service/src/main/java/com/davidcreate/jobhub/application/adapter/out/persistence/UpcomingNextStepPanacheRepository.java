package com.davidcreate.jobhub.application.adapter.out.persistence;

import com.davidcreate.jobhub.application.application.port.out.UpcomingNextStepRepository;
import com.davidcreate.jobhub.application.domain.entity.UpcomingNextStep;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregation over existing {@code applications.application} columns for the
 * internal {@code GET /internal/applications/upcoming-next-steps} endpoint (ADR 0009). No
 * schema change: joins to {@code job_post_snapshot} / {@code user_job_post} for the company
 * name, exactly as the existing application detail view does.
 *
 * <p>{@code nextStepDate} is a DATE (day-granular), so the window is evaluated in whole days,
 * not partial hours: the lower bound {@code >= CURRENT_DATE} treats "today" as always upcoming
 * (time-of-day on the step is unknown), and the upper bound adds {@code withinHours / 24} whole
 * days to {@code CURRENT_DATE}. Keeping both bounds day-granular makes membership independent of
 * the time of day the query runs (a midnight-boundary hour comparison would otherwise flip a
 * day-dated step in or out of the window). The hourly scheduler plus the send-log idempotency in
 * notification-service makes this coarse pre-filter safe; it computes exact H24/H1 fire instants
 * locally (ADR 0009).
 */
@ApplicationScoped
@RequiredArgsConstructor
public class UpcomingNextStepPanacheRepository implements UpcomingNextStepRepository {

    private static final String QUERY = """
            SELECT a.user_id AS user_id,
                   a.id AS application_id,
                   a.next_step_label AS next_step_label,
                   a.next_step_date AS next_step_date,
                   a.next_step_reminder_at AS next_step_reminder_at,
                   COALESCE(s.company, u.company) AS company_name,
                   a.status::text AS status
            FROM applications.application a
            LEFT JOIN applications.job_post_snapshot s ON s.id = a.job_post_snapshot_id
            LEFT JOIN applications.user_job_post u ON u.id = a.user_job_post_id
            WHERE a.next_step_label IS NOT NULL
              AND a.next_step_label <> ''
              AND a.next_step_date >= CURRENT_DATE
              AND a.next_step_date <= CURRENT_DATE + (CAST(:withinHours AS integer) / 24)
              AND a.status NOT IN ('rejected', 'accepted', 'withdrawn', 'ghosted')
            """;

    private final EntityManager entityManager;

    @Override
    public List<UpcomingNextStep> findUpcoming(int withinHours) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = entityManager.createNativeQuery(QUERY, Tuple.class)
                .setParameter("withinHours", withinHours)
                .getResultList();

        List<UpcomingNextStep> result = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            result.add(toDomain(row));
        }
        return result;
    }

    private UpcomingNextStep toDomain(Tuple row) {
        Timestamp reminderAt = (Timestamp) row.get("next_step_reminder_at");
        return UpcomingNextStep.builder()
                .userId((UUID) row.get("user_id"))
                .applicationId((UUID) row.get("application_id"))
                .nextStepLabel((String) row.get("next_step_label"))
                .nextStepDate((LocalDate) row.get("next_step_date"))
                .nextStepReminderAt(reminderAt == null ? null : OffsetDateTime.ofInstant(reminderAt.toInstant(), ZoneOffset.UTC))
                .companyName((String) row.get("company_name"))
                .status(ApplicationStatus.fromDbValue((String) row.get("status")))
                .build();
    }
}
