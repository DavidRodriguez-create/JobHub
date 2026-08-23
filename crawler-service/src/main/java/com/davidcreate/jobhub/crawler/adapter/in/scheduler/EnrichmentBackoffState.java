package com.davidcreate.jobhub.crawler.adapter.in.scheduler;

import com.davidcreate.jobhub.crawler.domain.service.EnrichmentBackoffCalculator;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;

/**
 * Story #537: in-memory, single-crawler-instance state for the enrichment scheduled-pass
 * failure backoff (D1: global to the pass, not per job_post row; D6: in-memory, no DB).
 * Shared by {@link EnrichmentScheduler} (checks + reports) and {@link TriggerRequestScheduler}
 * (reports only: admin-triggered enrichment bypasses the check per D4).
 *
 * <p>Takes {@code Instant now} as a parameter on every call instead of owning a Clock bean,
 * so it stays unit-testable without a CDI seam added purely for tests.
 */
@ApplicationScoped
public class EnrichmentBackoffState {

    private static final Logger LOG = Logger.getLogger(EnrichmentBackoffState.class);

    @ConfigProperty(name = "crawler.enrichment.backoff.enabled", defaultValue = "true")
    public boolean enabled;

    @ConfigProperty(name = "crawler.enrichment.backoff.step-minutes", defaultValue = "30")
    public int stepMinutes;

    @ConfigProperty(name = "crawler.enrichment.backoff.max-minutes", defaultValue = "120")
    public int maxMinutes;

    private int consecutiveFailures = 0;
    private Instant nextAttemptInstant;

    public boolean isBackedOff(Instant now) {
        return backoffActive() && nextAttemptInstant != null && now.isBefore(nextAttemptInstant);
    }

    /**
     * Reports the outcome of one scheduled or admin-triggered enrichment pass (D2):
     * a pass that attempted >= 1 job and enriched 0 is a failure (extends the delay);
     * a pass with >= 1 success resets the backoff; a pass with no pending rows or a
     * cancelled pass is neutral (no state change).
     */
    public void onPassResult(int attempted, int enriched, boolean cancelled, Instant now) {
        if (cancelled || attempted == 0) {
            return;
        }
        if (enriched > 0) {
            consecutiveFailures = 0;
            nextAttemptInstant = null;
            return;
        }

        consecutiveFailures++;
        if (!backoffActive()) {
            return;
        }
        int delayMinutes = EnrichmentBackoffCalculator.nextDelayMinutes(consecutiveFailures, stepMinutes, maxMinutes);
        nextAttemptInstant = now.plus(Duration.ofMinutes(delayMinutes));
        LOG.infof("Enrichment backoff armed: %d consecutive failures, delay=%d min, next attempt at %s",
                consecutiveFailures, delayMinutes, nextAttemptInstant);
    }

    private boolean backoffActive() {
        return enabled && stepMinutes > 0 && maxMinutes > 0;
    }
}
