package com.davidcreate.jobhub.application.adapter.out.persistence;

import com.davidcreate.jobhub.application.adapter.out.persistence.entity.ApplicationTimelineEntity;
import com.davidcreate.jobhub.application.application.port.out.ApplicationTimelineRepository;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import com.davidcreate.jobhub.application.domain.valueobject.TimelineEntry;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ApplicationTimelinePanacheRepository
        implements ApplicationTimelineRepository, PanacheRepositoryBase<ApplicationTimelineEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(ApplicationTimelinePanacheRepository.class);

    @Override
    public void append(UUID applicationId, ApplicationStatus status, OffsetDateTime occurredAt) {
        ApplicationTimelineEntity e = new ApplicationTimelineEntity();
        e.applicationId = applicationId;
        e.status = status;
        e.occurredAt = occurredAt;
        persist(e);
        LOG.infof("INSERT applications.application_timeline applicationId=%s status=%s", applicationId, status);
    }

    @Override
    public List<TimelineEntry> findByApplication(UUID applicationId) {
        return find("applicationId", Sort.ascending("occurredAt"), applicationId)
                .list()
                .stream()
                .map(e -> new TimelineEntry(e.status, e.occurredAt))
                .toList();
    }

    @Override
    public void removeByUser(UUID userId) {
        long deleted = delete("applicationId in (select a.id from ApplicationEntity a where a.userId = ?1)", userId);
        LOG.infof("DELETE applications.application_timeline userId=%s -> %d row(s)", userId, deleted);
    }

    @Override
    public double avgReplyDays(UUID userId) {
        Object result = getEntityManager().createNativeQuery(
                        "SELECT AVG(EXTRACT(EPOCH FROM (fc.first_change - a.applied_at)) / 86400.0) "
                                + "FROM applications.application a "
                                + "JOIN (SELECT application_id, MIN(occurred_at) AS first_change "
                                + "      FROM applications.application_timeline "
                                + "      WHERE status <> 'applied' GROUP BY application_id) fc "
                                + "  ON fc.application_id = a.id "
                                + "WHERE a.user_id = :uid")
                .setParameter("uid", userId)
                .getSingleResult();
        return result == null ? 0.0 : ((Number) result).doubleValue();
    }

    @Override
    public long countReachedOffer(UUID userId) {
        Object result = getEntityManager().createNativeQuery(
                        "SELECT COUNT(DISTINCT t.application_id) "
                                + "FROM applications.application_timeline t "
                                + "JOIN applications.application a ON a.id = t.application_id "
                                + "WHERE a.user_id = :uid AND t.status::text IN ('offered','accepted')")
                .setParameter("uid", userId)
                .getSingleResult();
        return result == null ? 0L : ((Number) result).longValue();
    }
}
