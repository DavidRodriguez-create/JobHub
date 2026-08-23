package com.davidcreate.jobhub.crawler.adapter.out.persistence;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.PullTargetEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.mapper.PullTargetMapper;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.davidcreate.jobhub.crawler.domain.port.out.PullTargetRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class PullTargetPanacheRepository
        implements PullTargetRepository, PanacheRepositoryBase<PullTargetEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(PullTargetPanacheRepository.class);

    private final PullTargetMapper mapper;

    @Override
    public Optional<PullTarget> findNextAvailableAndLock() {

        @SuppressWarnings("unchecked")
        List<PullTargetEntity> result = getEntityManager()
                .createNativeQuery("""
                        SELECT * FROM crawler.pull_target
                        WHERE status = 'active'
                        AND (locked_by IS NULL OR lease_expires_at < NOW())
                        AND next_pull_after <= NOW()
                        ORDER BY pull_priority DESC, next_pull_after
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """, PullTargetEntity.class)
                .getResultList();

        return result.stream()
                .findFirst()
                .map(mapper::toDomain);
    }

    @Override
    public Optional<PullTarget> findAndLockById(UUID id) {

        @SuppressWarnings("unchecked")
        List<PullTargetEntity> result = getEntityManager()
                .createNativeQuery("""
                        SELECT * FROM crawler.pull_target
                        WHERE id = :id
                        AND status = 'active'
                        AND locked_by IS NULL
                        FOR UPDATE SKIP LOCKED
                        """, PullTargetEntity.class)
                .setParameter("id", id)
                .getResultList();

        return result.stream()
                .findFirst()
                .map(mapper::toDomain);
    }

    @Override
    public Optional<PullTarget> findTargetById(UUID id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public void save(PullTarget domain) {
        findByIdOptional(domain.getId())
                .ifPresentOrElse(
                        entity -> {
                            mapper.updateEntity(entity, domain);
                            persistAndFlush(entity);
                            LOG.debugf("UPDATE crawler.pull_target id=%s status=%s", entity.id, entity.status);
                        },
                        () -> {
                            PullTargetEntity entity = mapper.toEntity(domain);
                            persistAndFlush(entity);
                            LOG.debugf("INSERT crawler.pull_target id=%s status=%s", entity.id, entity.status);
                        });
    }
}