package com.davidcreate.jobhub.crawler.adapter.in.maintenance;

import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.LocationBatchResult;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * One-off maintenance: on startup, re-normalize the {@code city}/{@code country} of every
 * existing {@code crawler.job_post} row through {@link
 * com.davidcreate.jobhub.crawler.adapter.out.client.support.LocationNormalizer#normalizePair}
 * (story #408, ADR 0021). No LLM call is made: this is a pure in-process data correction pass
 * that also rewrites each row's {@code job_post_location} child set to stay consistent with
 * ADR 0017's primary-mirror invariant.
 *
 * <p>Unlike {@link DescriptionBackfillRunner}/{@link LanguagesBackfillRunner}, a normalized row
 * still has a non-null {@code city}/{@code country}, so a page-0 selection would re-select
 * forever. This runner walks the table exactly once with an ascending-id cursor (ADR 0021
 * section 6), advancing to the last id processed on each page and stopping when a page reports
 * zero rows.
 *
 * <p>Gated by {@code crawler.maintenance.normalize-locations} and idempotent, so the intended
 * use is: enable, redeploy once, then disable again. Off by default.
 */
@ApplicationScoped
public class LocationNormalizationBackfillRunner {

    private static final Logger LOG = Logger.getLogger(LocationNormalizationBackfillRunner.class);

    // Safety cap on batch iterations so a stubborn table can never spin forever.
    public static final int MAX_BATCHES = 100_000;

    private final JobPostRepository repository;
    private final boolean enabled;
    private final int batchSize;

    public LocationNormalizationBackfillRunner(JobPostRepository repository,
                                                @ConfigProperty(name = "crawler.maintenance.normalize-locations", defaultValue = "false") boolean enabled,
                                                @ConfigProperty(name = "crawler.maintenance.normalize-locations-batch-size", defaultValue = "200") int batchSize) {
        this.repository = repository;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    public void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            return;
        }
        LOG.info("Locations backfill enabled, re-normalizing stored city/country values...");
        UUID cursor = null;
        long total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            LocationBatchResult result = repository.normalizeLocationsBatch(cursor, batchSize);
            if (result.isEmpty()) {
                LOG.infof("Locations backfill complete, %d rows processed. "
                        + "Disable crawler.maintenance.normalize-locations now.", total);
                return;
            }
            total += result.processed();
            cursor = result.lastId();
        }
        LOG.warnf("Locations backfill hit the %d-batch cap after %d rows, rerun to finish.",
                MAX_BATCHES, total);
    }
}
