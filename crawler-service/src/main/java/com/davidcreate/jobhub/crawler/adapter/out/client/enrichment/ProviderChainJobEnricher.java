package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment;

import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.EnrichmentProvider;
import com.davidcreate.jobhub.crawler.domain.exception.EnrichmentUnavailableException;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.davidcreate.jobhub.crawler.domain.port.out.JobEnricher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Default {@link JobEnricher}: tries each configured, enabled and keyed provider
 * in declaration order ({@code crawler.enrichment.providers[N]}), moving to the
 * next on any failure. Throws once every provider is exhausted so
 * {@code EnrichmentService} marks the job as failed — no silent swallowing.
 *
 * <p>Replaces the hardcoded {@code FallbackJobEnricher} / {@code @HostedEnricher} /
 * {@code @LocalEnricher} pattern (ADR 0004). Per-model cooldown is owned by each
 * provider instance, not shared across providers within a single chain. The
 * provider list is {@code @Inject}ed, built once by
 * {@link com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.EnrichmentProviderFactory#providers}.
 */
@ApplicationScoped
public class ProviderChainJobEnricher implements JobEnricher {

    private static final Logger LOG = Logger.getLogger(ProviderChainJobEnricher.class);

    private final List<EnrichmentProvider> providers;

    @Inject
    public ProviderChainJobEnricher(List<EnrichmentProvider> providers) {
        this.providers = providers;
        if (providers.isEmpty()) {
            LOG.warn("No enrichment providers are enabled — "
                    + "enrichment calls will fail until a provider is configured.");
        }
    }

    @Override
    public JobEnrichment enrich(String title, String description, String city, String country) {
        boolean anyGenuineContentFailure = false;
        boolean anyTransientFailure = false;
        for (EnrichmentProvider provider : providers) {
            try {
                return provider.enrich(title, description, city, country);
            } catch (EnrichmentUnavailableException e) {
                anyTransientFailure = true;
                LOG.debugf("Provider '%s' transiently unavailable (%s) — trying next.", provider.name(), e.getMessage());
            } catch (Exception e) {
                anyGenuineContentFailure = true;
                LOG.debugf("Provider '%s' failed (%s) — trying next.", provider.name(), e.getMessage());
            }
        }
        if (anyTransientFailure && !anyGenuineContentFailure) {
            throw new EnrichmentUnavailableException(
                    "All enrichment providers transiently unavailable — no usable response");
        }
        throw new IllegalStateException("All enrichment providers exhausted — no usable response");
    }
}
