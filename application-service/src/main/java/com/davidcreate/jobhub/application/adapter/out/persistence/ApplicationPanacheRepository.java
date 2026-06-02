package com.davidcreate.jobhub.application.adapter.out.persistence;

import com.davidcreate.jobhub.application.adapter.out.persistence.entity.ApplicationEntity;
import com.davidcreate.jobhub.application.adapter.out.persistence.mapper.ApplicationMapper;
import com.davidcreate.jobhub.application.application.port.out.ApplicationRepository;
import com.davidcreate.jobhub.application.domain.entity.Application;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import com.davidcreate.jobhub.application.domain.valueobject.NextDeadline;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ApplicationPanacheRepository
        implements ApplicationRepository, PanacheRepositoryBase<ApplicationEntity, UUID> {

    private final ApplicationMapper mapper;

    @Override
    public Optional<Application> findOneById(UUID id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUserAndJobPost(UUID userId, UUID jobPostId) {
        return count("userId = ?1 AND jobPostId = ?2", userId, jobPostId) > 0;
    }

    @Override
    public List<Application> listByUser(UUID userId, ApplicationStatus statusFilter, int page, int size) {
        StringBuilder jpql = new StringBuilder("userId = :uid");
        Map<String, Object> params = new HashMap<>();
        params.put("uid", userId);
        if (statusFilter != null) {
            jpql.append(" AND status = :status");
            params.put("status", statusFilter);
        }
        return find(jpql.toString(), Sort.descending("appliedAt"), params)
                .page(Page.of(page, size))
                .list()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countByUser(UUID userId, ApplicationStatus statusFilter) {
        if (statusFilter == null) {
            return count("userId", userId);
        }
        return count("userId = ?1 AND status = ?2", userId, statusFilter);
    }

    @Override
    public Application save(Application a) {
        ApplicationEntity entity;
        OffsetDateTime now = OffsetDateTime.now();
        if (a.getId() != null && (entity = findByIdOptional(a.getId()).orElse(null)) != null) {
            mapper.updateEntity(entity, a);
            entity.updatedAt = now;
            persistAndFlush(entity);
            return mapper.toDomain(entity);
        }
        entity = mapper.toEntity(a);
        if (entity.createdAt == null) entity.createdAt = now;
        entity.updatedAt = now;
        if (entity.appliedAt == null) entity.appliedAt = now;
        persistAndFlush(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public void removeById(UUID id) {
        delete("id", id);
    }

    @Override
    public void removeAllByUser(UUID userId) {
        delete("userId", userId);
    }

    @Override
    public long countByUserCreatedSince(UUID userId, OffsetDateTime since) {
        return count("userId = ?1 AND createdAt >= ?2", userId, since);
    }

    @Override
    public Optional<NextDeadline> earliestUpcomingNextStep(UUID userId, LocalDate today) {
        List<Object[]> rows = getEntityManager().createQuery(
                        "SELECT a.id, a.nextStepDate FROM ApplicationEntity a "
                                + "WHERE a.userId = :uid AND a.nextStepDate >= :today AND a.endedAt IS NULL "
                                + "ORDER BY a.nextStepDate ASC", Object[].class)
                .setParameter("uid", userId)
                .setParameter("today", today)
                .setMaxResults(1)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = rows.get(0);
        return Optional.of(new NextDeadline((LocalDate) r[1], (UUID) r[0]));
    }

    @Override
    public List<MonthlyStatusCount> monthlyStatusCounts(UUID userId, OffsetDateTime since) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = getEntityManager().createNativeQuery(
                        "SELECT EXTRACT(YEAR FROM applied_at)::int AS y, "
                                + "EXTRACT(MONTH FROM applied_at)::int AS m, "
                                + "status::text AS s, COUNT(*) AS c "
                                + "FROM applications.application "
                                + "WHERE user_id = :uid AND applied_at >= :since "
                                + "GROUP BY y, m, s")
                .setParameter("uid", userId)
                .setParameter("since", since)
                .getResultList();
        List<MonthlyStatusCount> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            result.add(new MonthlyStatusCount(
                    ((Number) r[0]).intValue(),
                    ((Number) r[1]).intValue(),
                    ApplicationStatus.fromDbValue((String) r[2]),
                    ((Number) r[3]).longValue()));
        }
        return result;
    }

    @Override
    public Map<ApplicationStatus, Long> countByUserGroupedByStatus(UUID userId) {
        List<Tuple> rows = getEntityManager().createQuery(
                        "SELECT a.status AS s, COUNT(a) AS c FROM ApplicationEntity a "
                                + "WHERE a.userId = :uid GROUP BY a.status", Tuple.class)
                .setParameter("uid", userId)
                .getResultList();
        Map<ApplicationStatus, Long> result = new EnumMap<>(ApplicationStatus.class);
        for (Tuple t : rows) {
            result.put((ApplicationStatus) t.get("s"), (Long) t.get("c"));
        }
        return result;
    }
}
