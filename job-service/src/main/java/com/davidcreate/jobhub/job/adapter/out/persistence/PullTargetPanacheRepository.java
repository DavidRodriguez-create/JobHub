package com.davidcreate.jobhub.job.adapter.out.persistence;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.PullTargetEntity;
import com.davidcreate.jobhub.job.domain.model.UnresolvedTarget;
import com.davidcreate.jobhub.job.domain.port.out.PullTargetRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PullTargetPanacheRepository
        implements PullTargetRepository, PanacheRepositoryBase<PullTargetEntity, UUID> {

    private final EntityManager em;

    public PullTargetPanacheRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<UnresolvedTarget> findWithoutCompany(int limit) {
        return find("companyId is null", Sort.ascending("id"))
                .page(0, limit)
                .list()
                .stream()
                .map(target -> UnresolvedTarget.builder()
                        .id(target.id)
                        .companyName(target.companyName)
                        .build())
                .toList();
    }

    /**
     * A targeted bulk UPDATE of {@code company_id} only (never a full-entity
     * persist/merge), matching job_user's narrow {@code UPDATE (company_id)} grant on
     * {@code crawler.pull_target} (ADR 0023 D2/D6) - job_user has no general UPDATE
     * privilege on this table's other columns.
     */
    @Override
    public void assignCompany(UUID targetId, UUID companyId) {
        em.createQuery("UPDATE PullTargetEntity SET companyId = :companyId WHERE id = :targetId")
                .setParameter("companyId", companyId)
                .setParameter("targetId", targetId)
                .executeUpdate();
    }
}
