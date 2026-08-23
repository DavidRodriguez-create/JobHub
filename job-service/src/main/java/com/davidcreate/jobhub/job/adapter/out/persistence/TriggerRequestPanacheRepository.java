package com.davidcreate.jobhub.job.adapter.out.persistence;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.TriggerRequestMapper;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;
import com.davidcreate.jobhub.job.domain.model.TriggerStatus;
import com.davidcreate.jobhub.job.domain.port.out.TriggerRequestRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TriggerRequestPanacheRepository
        implements TriggerRequestRepository, PanacheRepositoryBase<TriggerRequestEntity, UUID> {

    private static final List<String> TERMINAL_STATUSES = List.of(
            TriggerStatus.SUCCEEDED.value(), TriggerStatus.FAILED.value(), TriggerStatus.CANCELLED.value());

    private final TriggerRequestMapper mapper;

    public TriggerRequestPanacheRepository(TriggerRequestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TriggerRequest> findMostRecent(TriggerKind kind) {
        return find("kind", Sort.descending("requestedAt"), kind.value())
                .firstResultOptional()
                .map(mapper::toDomain);
    }

    @Override
    public Optional<TriggerRequest> findLastFinished(TriggerKind kind) {
        return find("kind = ?1 and status in ?2 order by finishedAt desc", kind.value(), TERMINAL_STATUSES)
                .firstResultOptional()
                .map(mapper::toDomain);
    }
}
