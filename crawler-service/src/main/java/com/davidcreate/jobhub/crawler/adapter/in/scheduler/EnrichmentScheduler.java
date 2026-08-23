package com.davidcreate.jobhub.crawler.adapter.in.scheduler;

import com.davidcreate.jobhub.crawler.domain.model.EnrichBatchResult;
import com.davidcreate.jobhub.crawler.domain.port.in.EnrichJobsUseCase;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;

@ApplicationScoped
public class EnrichmentScheduler {

    private static final Logger LOG = Logger.getLogger(EnrichmentScheduler.class);

    private final EnrichJobsUseCase enrichJobsUseCase;
    private final EnrichmentBackoffState backoffState;
    private final ShutdownSignal shutdownSignal;

    @ConfigProperty(name = "crawler.enrichment.enabled", defaultValue = "true")
    public boolean enabled;

    @ConfigProperty(name = "crawler.enrichment.batch-size", defaultValue = "5")
    public int batchSize;

    public EnrichmentScheduler(EnrichJobsUseCase enrichJobsUseCase, EnrichmentBackoffState backoffState,
                                ShutdownSignal shutdownSignal) {
        this.enrichJobsUseCase = enrichJobsUseCase;
        this.backoffState = backoffState;
        this.shutdownSignal = shutdownSignal;
    }

    // Skip overlapping runs — a batch of model calls can outlast the interval.
    // SKIP is per-instance only; see JobPostRepository.findPendingEnrichment for the
    // multi-instance caveat (pending rows are not claimed).
    @Scheduled(cron = "${crawler.enrichment.cron:0/30 * * * * ?}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void run() {
        // 4th pass (D1, story #398): outer boundary -- nothing below may let a Throwable
        // escape once shutdown has begun (see ShutdownFlag#guardScheduledTick).
        ShutdownFlag.guardScheduledTick(this::tick,
                t -> LOG.infof("Scheduled enrichment tick abandoned during shutdown: %s", t.getMessage()));
    }

    private void tick() {
        if (shutdownSignal.isShuttingDown()) {
            LOG.debug("Shutdown in progress: skipping enrichment tick");
            return;
        }
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        if (backoffState.isBackedOff(now)) {
            LOG.debug("Skipping enrichment tick: pass is backed off after prior failures");
            return;
        }

        EnrichBatchResult result = enrichJobsUseCase.enrichPending(batchSize, null, shutdownSignal);
        backoffState.onPassResult(result.getAttempted(), result.getEnriched(), result.isCancelled(), now);
    }
}
