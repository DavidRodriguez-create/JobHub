package com.davidcreate.jobhub.job.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.contract.model.TriggerLastRun;
import com.davidcreate.jobhub.job.contract.model.TriggerOrigin;
import com.davidcreate.jobhub.job.contract.model.TriggerOutcome;
import com.davidcreate.jobhub.job.contract.model.TriggerProgress;
import com.davidcreate.jobhub.job.contract.model.TriggerRunInfo;
import com.davidcreate.jobhub.job.contract.model.TriggerStatusResponse;
import com.davidcreate.jobhub.job.contract.model.TriggerStatusValue;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;
import com.davidcreate.jobhub.job.domain.model.TriggerStatusOverview;

public final class TriggerStatusMapper {

    private TriggerStatusMapper() {
    }

    public static TriggerStatusResponse toStatusResponse(TriggerStatusOverview overview) {
        return new TriggerStatusResponse()
                .triggerEnabled(overview.isTriggerEnabled())
                .twoFactorRequired(overview.isTwoFactorRequired())
                .crawl(toRunInfo(overview.getCrawl()))
                .enrichment(toRunInfo(overview.getEnrichment()))
                .lastCrawlRun(toLastRun(overview.getLastCrawlRun()))
                .lastEnrichmentRun(toLastRun(overview.getLastEnrichmentRun()));
    }

    private static TriggerRunInfo toRunInfo(TriggerRequest request) {
        if (request == null) {
            return null;
        }
        return new TriggerRunInfo()
                .id(request.getId())
                .status(TriggerStatusValue.fromValue(request.getStatus().value()))
                .requestedAt(request.getRequestedAt())
                .startedAt(request.getStartedAt())
                .finishedAt(request.getFinishedAt())
                .resultSummary(request.getResultSummary())
                .errorReason(request.getErrorReason())
                .origin(toOrigin(request.getOrigin()))
                .outcome(toOutcome(request.getOutcome()))
                .progress(toProgress(request.getProgress()));
    }

    // Story #398, C25: the most recent FINISHED run of one kind, whatever
    // started it (distinct from crawl/enrichment above, which describe the
    // most recent request and stay unfinished mid-run). null means the kind
    // has never finished a run.
    private static TriggerLastRun toLastRun(TriggerRequest request) {
        if (request == null) {
            return null;
        }
        return new TriggerLastRun()
                .id(request.getId())
                .finishedAt(request.getFinishedAt())
                .status(TriggerStatusValue.fromValue(request.getStatus().value()))
                .outcome(toOutcome(request.getOutcome()))
                .origin(toOrigin(request.getOrigin()))
                .resultSummary(request.getResultSummary())
                .errorReason(request.getErrorReason());
    }

    private static TriggerOrigin toOrigin(com.davidcreate.jobhub.job.domain.model.TriggerOrigin origin) {
        if (origin == null) {
            return null;
        }
        return TriggerOrigin.fromValue(origin.value());
    }

    private static TriggerOutcome toOutcome(com.davidcreate.jobhub.job.domain.model.TriggerOutcome outcome) {
        if (outcome == null) {
            return null;
        }
        return TriggerOutcome.fromValue(outcome.value());
    }

    private static TriggerProgress toProgress(com.davidcreate.jobhub.job.domain.model.TriggerProgress progress) {
        if (progress == null) {
            return null;
        }
        return new TriggerProgress()
                .targetsVisited(progress.getTargetsVisited())
                .newPosts(progress.getNewPosts())
                .currentCompany(progress.getCurrentCompany())
                .currentSourceType(progress.getCurrentSourceType())
                .lastCompany(progress.getLastCompany())
                .lastSourceType(progress.getLastSourceType())
                .lastFoundPosts(progress.getLastFoundPosts())
                .lastNewPosts(progress.getLastNewPosts())
                .updatedAt(progress.getUpdatedAt());
    }
}
