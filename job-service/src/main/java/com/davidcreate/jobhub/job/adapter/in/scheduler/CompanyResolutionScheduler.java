package com.davidcreate.jobhub.job.adapter.in.scheduler;

import com.davidcreate.jobhub.job.domain.port.in.ResolveCompaniesUseCase;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Story #428 (ADR 0023 D5): drives {@link ResolveCompaniesUseCase} off the read path, on a
 * config-guarded interval. Skips overlapping runs - a large backlog batch can outlast the
 * interval.
 */
@ApplicationScoped
public class CompanyResolutionScheduler {

    private static final Logger LOG = Logger.getLogger(CompanyResolutionScheduler.class);

    private final ResolveCompaniesUseCase resolveCompaniesUseCase;

    @ConfigProperty(name = "job.company.resolve.enabled", defaultValue = "true")
    public boolean enabled;

    public CompanyResolutionScheduler(ResolveCompaniesUseCase resolveCompaniesUseCase) {
        this.resolveCompaniesUseCase = resolveCompaniesUseCase;
    }

    @Scheduled(every = "${job.company.resolve.every:15m}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void run() {
        if (!enabled) {
            return;
        }
        int resolved = resolveCompaniesUseCase.resolvePending();
        if (resolved > 0) {
            LOG.infof("Company resolution: resolved %d pull target(s)", resolved);
        }
    }
}
