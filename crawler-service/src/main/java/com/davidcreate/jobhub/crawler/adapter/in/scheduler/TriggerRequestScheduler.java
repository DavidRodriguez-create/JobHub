package com.davidcreate.jobhub.crawler.adapter.in.scheduler;

import com.davidcreate.jobhub.crawler.domain.model.CrawlBatchResult;
import com.davidcreate.jobhub.crawler.domain.model.EnrichBatchResult;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOutcome;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;
import com.davidcreate.jobhub.crawler.domain.port.in.CrawlUseCase;
import com.davidcreate.jobhub.crawler.domain.port.in.EnrichJobsUseCase;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TriggerRequestScheduler {

    private static final Logger LOG = Logger.getLogger(TriggerRequestScheduler.class);

    private final TriggerRequestQueue triggerRequestQueue;
    private final CrawlUseCase crawlUseCase;
    private final EnrichJobsUseCase enrichJobsUseCase;
    private final EnrichmentBackoffState backoffState;
    private final ShutdownSignal shutdownSignal;

    @ConfigProperty(name = "crawler.crawl.min-new-posts", defaultValue = "100")
    public int minNewPosts;

    @ConfigProperty(name = "crawler.enrichment.batch-size", defaultValue = "5")
    public int enrichBatchSize;

    public TriggerRequestScheduler(TriggerRequestQueue triggerRequestQueue,
                                    CrawlUseCase crawlUseCase,
                                    EnrichJobsUseCase enrichJobsUseCase,
                                    EnrichmentBackoffState backoffState,
                                    ShutdownSignal shutdownSignal) {
        this.triggerRequestQueue = triggerRequestQueue;
        this.crawlUseCase = crawlUseCase;
        this.enrichJobsUseCase = enrichJobsUseCase;
        this.backoffState = backoffState;
        this.shutdownSignal = shutdownSignal;
    }

    @Scheduled(cron = "${crawler.trigger.poll-cron:0/10 * * * * ?}")
    public void run() {
        // 4th pass (D1): the outer boundary for this scheduled tick. Nothing below this line
        // may let a Throwable escape once shutdown has begun -- otherwise Quarkus's
        // StatusEmitterInvoker logs it as "[Error Occurred After Shutdown]". A stray
        // IllegalStateException from an injected proxy whose CDI container is already gone is
        // exactly the kind of thing that cannot be enumerated ahead of time, hence catching
        // Throwable here, not Exception.
        ShutdownFlag.guardScheduledTick(this::tick,
                t -> LOG.infof("Scheduled trigger-request poll abandoned during shutdown: %s", t.getMessage()));
    }

    private void tick() {
        if (shutdownSignal.isShuttingDown()) {
            LOG.debug("Shutdown in progress: skipping trigger-request poll");
            return;
        }

        // N2 (story #398): only one active CRAWL row at a time, enforced as a DB fact by
        // the partial unique index (060) -- claiming a second one while one is already
        // running would violate it. A queued row (manual or scheduled) simply waits until
        // the active run finishes.
        if (!isCrawlRunning()) {
            processKind(TriggerKind.CRAWL);
        } else {
            LOG.info("Skipping crawl trigger processing: a crawl is currently running");
        }

        if (isCrawlRunning()) {
            LOG.info("Skipping enrichment trigger processing: a crawl is currently running");
            return;
        }

        processKind(TriggerKind.ENRICHMENT);
    }

    private boolean isCrawlRunning() {
        try {
            return triggerRequestQueue.hasRunning(TriggerKind.CRAWL);
        } catch (Exception e) {
            // 5th pass (D1, story #398): a drain-timeout interrupt can land here too, inside
            // the repository call, once the CDI container is tearing down (e.g. "Session/
            // EntityManager is closed"). Expected once shutdown is up -- log quietly (no
            // stack trace) instead of the loud ERROR this catch uses the rest of the time,
            // which remains a real safety net for a genuine DB failure while running.
            if (ShutdownFlag.isRaised()) {
                LOG.infof("Crawl-running check abandoned during shutdown: %s", e.getMessage());
            } else {
                LOG.errorf(e, "Failed to check for a running crawl; skipping enrichment as a precaution");
            }
            return true;
        }
    }

    private void processKind(TriggerKind kind) {
        Optional<TriggerRequest> claimed = triggerRequestQueue.claimNext(kind);
        claimed.ifPresent(this::execute);
    }

    private void execute(TriggerRequest request) {
        UUID id = request.getId();
        triggerRequestQueue.markRunning(id);

        try {
            switch (request.getKind()) {
                case CRAWL -> runCrawl(id);
                case ENRICHMENT -> runEnrichment(id);
            }
        } catch (Exception e) {
            // A drain-timeout interrupt (ADR 0032, story #398, D1) can land here too: the batch
            // itself already swallowed its own abandoned-item exception, but a markDone/
            // markCancelled write below can still hit a closing EntityManagerFactory. Once
            // shutdown is up this is a HARD no-op: don't even attempt the write. The
            // ShutdownEvent observer's own reapNonTerminal() call already put this row in a
            // terminal state on the way down (or, failing that, the startup reaper will); a
            // second write here is both unnecessary and guaranteed to fail once the CDI
            // container is gone. Reads the CDI-free ShutdownFlag directly, not
            // shutdownSignal.isShuttingDown(): that call itself goes through an injected proxy
            // and can throw at this point (4th pass).
            if (ShutdownFlag.isRaised()) {
                LOG.infof("Trigger request %s (%s) abandoned during shutdown", id, request.getKind());
                return;
            }
            LOG.errorf(e, "Trigger request %s (%s) failed", id, request.getKind());
            triggerRequestQueue.markDone(id, "failed", TriggerOutcome.FAILED.value(), null, e.getMessage());
        }
    }

    private void runCrawl(UUID id) {
        CrawlBatchResult result = crawlUseCase.crawlBatch(minNewPosts, id, shutdownSignal);
        if (result.isCancelled()) {
            String summary = String.format("crawled %d targets, %d new posts (cancelled)",
                    result.getCrawled(), result.getNewPosts());
            triggerRequestQueue.markCancelled(id, summary);
        } else {
            // N1 (story #398): a batch that visited nothing is a success, never a bare
            // "crawled 0 targets" -- outcome/summary both say so explicitly.
            boolean noTargets = result.getOutcome() == TriggerOutcome.NO_TARGETS;
            String summary = noTargets
                    ? "no more targets to crawl"
                    : String.format("crawled %d targets, %d new posts", result.getCrawled(), result.getNewPosts());
            String outcome = noTargets ? TriggerOutcome.NO_TARGETS.value() : TriggerOutcome.COMPLETED.value();
            triggerRequestQueue.markDone(id, "succeeded", outcome, summary, null);
        }
    }

    private void runEnrichment(UUID id) {
        // D4: admin-triggered enrichment bypasses the backoff check (never calls
        // isBackedOff), but its outcome still feeds the shared backoff state.
        EnrichBatchResult result = enrichJobsUseCase.enrichPending(enrichBatchSize, id, shutdownSignal);
        backoffState.onPassResult(result.getAttempted(), result.getEnriched(), result.isCancelled(), Instant.now());
        if (result.isCancelled()) {
            String summary = String.format("Cancelled after %d of %d postings", result.getEnriched(), enrichBatchSize);
            triggerRequestQueue.markCancelled(id, summary);
        } else {
            String summary = String.format("enriched %d postings", result.getEnriched());
            triggerRequestQueue.markDone(id, "succeeded", TriggerOutcome.COMPLETED.value(), summary, null);
        }
    }
}
