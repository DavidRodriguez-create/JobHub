package com.davidcreate.jobhub.crawler.domain.service;

import com.davidcreate.jobhub.crawler.domain.exception.EnrichmentUnavailableException;
import com.davidcreate.jobhub.crawler.domain.model.EnrichBatchResult;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.port.in.EnrichJobsUseCase;
import com.davidcreate.jobhub.crawler.domain.port.out.JobEnricher;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EnrichmentService implements EnrichJobsUseCase {

    private static final Logger LOG = Logger.getLogger(EnrichmentService.class);

    private final JobPostRepository jobPostRepository;
    private final JobEnricher jobEnricher;
    private final TriggerRequestQueue triggerRequestQueue;
    private final int maxAttempts;

    public EnrichmentService(JobPostRepository jobPostRepository,
                             JobEnricher jobEnricher,
                             TriggerRequestQueue triggerRequestQueue,
                             @ConfigProperty(name = "crawler.enrichment.max-attempts", defaultValue = "3") int maxAttempts) {
        this.jobPostRepository = jobPostRepository;
        this.jobEnricher = jobEnricher;
        this.triggerRequestQueue = triggerRequestQueue;
        this.maxAttempts = maxAttempts;
    }

    private static final ShutdownSignal NO_SHUTDOWN = () -> false;

    @Override
    public int enrichPending(int limit) {
        return enrichPending(limit, null).getEnriched();
    }

    @Override
    public EnrichBatchResult enrichPending(int limit, UUID triggerRequestId) {
        return enrichPending(limit, triggerRequestId, NO_SHUTDOWN);
    }

    @Override
    public EnrichBatchResult enrichPending(int limit, UUID triggerRequestId, ShutdownSignal shutdownSignal) {
        List<JobPost> pending = jobPostRepository.findPendingEnrichment(limit);
        if (pending.isEmpty()) {
            return EnrichBatchResult.builder().attempted(0).enriched(0).cancelled(false).build();
        }

        // Registers this batch as in-flight for its whole duration (not per item), so the
        // ShutdownEvent observer's bounded drain knows to wait for it (story #398, D1).
        shutdownSignal.workStarted();
        try {
            return runEnrichPending(pending, triggerRequestId, shutdownSignal);
        } finally {
            shutdownSignal.workFinished();
        }
    }

    private EnrichBatchResult runEnrichPending(List<JobPost> pending, UUID triggerRequestId, ShutdownSignal shutdownSignal) {
        int attempted = 0;
        int enriched = 0;
        boolean cancelled = false;
        for (JobPost job : pending) {
            if (triggerRequestId != null && triggerRequestQueue.isCancelRequested(triggerRequestId)) {
                cancelled = true;
                break;
            }

            // Shutdown check (ADR 0032, story #398): stop at the item boundary, no new
            // model call starts once shutdown has begun.
            if (shutdownSignal.isShuttingDown()) {
                break;
            }

            attempted++;
            // The model call (slow, external) runs outside any DB transaction;
            // each persist below opens its own short transaction in the repository.
            try {
                JobEnrichment result = jobEnricher.enrich(
                        job.getTitle(), job.getDescription(), job.getCity(), job.getCountry());
                jobPostRepository.applyEnrichment(job.getId(), result);
                enriched++;
            } catch (EnrichmentUnavailableException e) {
                // Transient: no provider was reachable. Don't blame this (or any
                // later) posting for it — bail out of the batch rather than
                // burning through every pending row against a dead chain.
                LOG.warnf("Enrichment pass stopped early: providers transiently unavailable (%s)", e.getMessage());
                break;
            } catch (Exception e) {
                // A drain-timeout interrupt (ADR 0032, story #398, D1) lands here: the model
                // call or the apply-enrichment write was cut short mid-flight once shutdown
                // began. Expected, not a fault -- swallow quietly (no stack trace, and no
                // further markEnrichmentFailed write, which could itself hit a closing
                // EntityManagerFactory) rather than let it propagate. Reads the CDI-free
                // ShutdownFlag directly, not shutdownSignal.isShuttingDown(): by now the CDI
                // container may already be gone, and calling ANY method through an injected
                // proxy at that point can itself throw (4th pass).
                if (ShutdownFlag.isRaised()) {
                    LOG.infof("Enrichment abandoned during shutdown: %s", e.getMessage());
                    break;
                }
                LOG.warnf("Enrichment failed for job %s: %s", job.getId(), e.getMessage());
                jobPostRepository.markEnrichmentFailed(job.getId(), maxAttempts);
            }
        }

        if (cancelled) {
            LOG.infof("Enrichment pass cancelled: %d/%d job posts enriched before stop", enriched, pending.size());
        } else {
            LOG.infof("Enrichment pass: %d/%d job posts enriched", enriched, attempted);
        }
        return EnrichBatchResult.builder().attempted(attempted).enriched(enriched).cancelled(cancelled).build();
    }
}
