package com.davidcreate.jobhub.job.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerOrigin;
import com.davidcreate.jobhub.job.domain.model.TriggerOutcome;
import com.davidcreate.jobhub.job.domain.model.TriggerProgress;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;
import com.davidcreate.jobhub.job.domain.model.TriggerStatus;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TriggerRequestMapper {

    public TriggerRequest toDomain(TriggerRequestEntity e) {
        return TriggerRequest.builder()
                .id(e.id)
                .kind(TriggerKind.fromValue(e.kind))
                .status(TriggerStatus.fromValue(e.status))
                .requestedBy(e.requestedBy)
                .requestedAt(e.requestedAt)
                .startedAt(e.startedAt)
                .finishedAt(e.finishedAt)
                .resultSummary(e.resultSummary)
                .errorReason(e.errorReason)
                .progress(toProgress(e))
                .origin(toOrigin(e))
                .outcome(toOutcome(e))
                .build();
    }

    // Story #398 / ADR 0032, C28: a null `origin` column means the row predates
    // the migration (or the DB default has not applied yet); job-service never
    // writes this column, so it defaults the domain reading to MANUAL rather
    // than surfacing null and forcing every caller to null-check it.
    private TriggerOrigin toOrigin(TriggerRequestEntity e) {
        if (e.origin == null) {
            return TriggerOrigin.MANUAL;
        }
        return TriggerOrigin.fromValue(e.origin);
    }

    // outcome is genuinely absent (not defaulted) while queued/running and for
    // pre-ADR-0032 rows: null stays null, no fabricated value.
    private TriggerOutcome toOutcome(TriggerRequestEntity e) {
        if (e.outcome == null) {
            return null;
        }
        return TriggerOutcome.fromValue(e.outcome);
    }

    // progress_updated_at IS NULL is the single, unambiguous "never reported
    // progress" marker (ADR 0029 decision 3), distinct from newPosts = 0
    // ("reported, nothing new yet"). Do not derive nullness from the counter columns.
    private TriggerProgress toProgress(TriggerRequestEntity e) {
        if (e.progressUpdatedAt == null) {
            return null;
        }
        return TriggerProgress.builder()
                .targetsVisited(e.progressTargetsVisited)
                .newPosts(e.progressNewPosts)
                .currentCompany(e.progressCurrentCompany)
                .currentSourceType(e.progressCurrentSourceType)
                .lastCompany(e.progressLastCompany)
                .lastSourceType(e.progressLastSourceType)
                .lastFoundPosts(e.progressLastFoundPosts)
                .lastNewPosts(e.progressLastNewPosts)
                .updatedAt(e.progressUpdatedAt)
                .build();
    }
}
