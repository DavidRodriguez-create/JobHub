package com.davidcreate.jobhub.crawler.adapter.in.maintenance;

import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * One-off maintenance: on startup, re-normalize the {@code languages} array of every
 * existing {@code crawler.job_post} row through
 * {@link com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentParser#normalizeLanguages}.
 * No LLM call is made — this is a pure in-process data correction pass.
 *
 * Gated by {@code crawler.maintenance.normalize-languages} and idempotent, so the
 * intended use is: enable, redeploy once, then disable again. Off by default.
 */
@ApplicationScoped
public class LanguagesBackfillRunner {

    private static final Logger LOG = Logger.getLogger(LanguagesBackfillRunner.class);

    // Safety cap on batch iterations so a stubborn row can never spin forever.
    public static final int MAX_BATCHES = 100_000;

    private final JobPostRepository repository;
    private final boolean enabled;
    private final int batchSize;

    public LanguagesBackfillRunner(JobPostRepository repository,
                                   @ConfigProperty(name = "crawler.maintenance.normalize-languages", defaultValue = "false") boolean enabled,
                                   @ConfigProperty(name = "crawler.maintenance.normalize-languages-batch-size", defaultValue = "200") int batchSize) {
        this.repository = repository;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    public void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }
        LOG.info("Languages backfill enabled — re-normalizing stored language arrays…");
        long total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int processed = repository.normalizeLanguagesBatch(batchSize);
            if (processed == 0) {
                LOG.infof("Languages backfill complete — %d rows processed. "
                        + "Disable crawler.maintenance.normalize-languages now.", total);
                return;
            }
            total += processed;
        }
        LOG.warnf("Languages backfill hit the %d-batch cap after %d rows — rerun to finish.",
                MAX_BATCHES, total);
    }
}
