package com.davidcreate.jobhub.crawler.adapter.in.maintenance;

import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * One-off maintenance: on startup, rewrite stored job descriptions that still hold HTML
 * markup into plain text (the same {@code HtmlToText} cleaning new crawls now apply).
 * Gated by {@code crawler.maintenance.clean-descriptions} and idempotent, so the intended
 * use is: enable, redeploy once, then disable again. Off by default.
 */
@ApplicationScoped
public class DescriptionBackfillRunner {

    private static final Logger LOG = Logger.getLogger(DescriptionBackfillRunner.class);

    // Safety cap on batch iterations so a stubborn row can never spin forever.
    private static final int MAX_BATCHES = 100_000;

    private final JobPostRepository repository;
    private final boolean enabled;
    private final int batchSize;

    public DescriptionBackfillRunner(JobPostRepository repository,
                                     @ConfigProperty(name = "crawler.maintenance.clean-descriptions", defaultValue = "false") boolean enabled,
                                     @ConfigProperty(name = "crawler.maintenance.clean-descriptions-batch-size", defaultValue = "200") int batchSize) {
        this.repository = repository;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }
        LOG.info("Description backfill enabled — rewriting HTML descriptions to plain text…");
        long total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int cleaned = repository.cleanHtmlDescriptionBatch(batchSize);
            if (cleaned == 0) {
                LOG.infof("Description backfill complete — %d descriptions cleaned. "
                        + "Disable crawler.maintenance.clean-descriptions now.", total);
                return;
            }
            total += cleaned;
        }
        LOG.warnf("Description backfill hit the %d-batch cap after %d rows — rerun to finish.",
                MAX_BATCHES, total);
    }
}
