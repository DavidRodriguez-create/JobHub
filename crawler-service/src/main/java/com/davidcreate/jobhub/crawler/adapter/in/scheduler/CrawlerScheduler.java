package com.davidcreate.jobhub.crawler.adapter.in.scheduler;

import com.davidcreate.jobhub.crawler.domain.exception.ConflictException;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOrigin;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

/**
 * Records the automatic scheduled crawl as a real {@code trigger_request} row (ADR 0032,
 * story #398, N2), so it is claimed and executed through the same pipeline as an
 * admin-triggered run, with {@code origin = scheduled}. Yields its own tick whenever any
 * crawl row (either origin) is already active: it never claims/executes directly.
 */
@ApplicationScoped
public class CrawlerScheduler {

    private static final Logger LOG = Logger.getLogger(CrawlerScheduler.class);

    private final TriggerRequestQueue triggerRequestQueue;
    private final ShutdownSignal shutdownSignal;

    public CrawlerScheduler(TriggerRequestQueue triggerRequestQueue, ShutdownSignal shutdownSignal) {
        this.triggerRequestQueue = triggerRequestQueue;
        this.shutdownSignal = shutdownSignal;
    }

    @Scheduled(cron = "${crawler.crawl.cron:0 0/10 * * * ?}")
    public void run() {
        // 4th pass (D1, story #398): outer boundary -- nothing below may let a Throwable
        // escape once shutdown has begun (see ShutdownFlag#guardScheduledTick).
        ShutdownFlag.guardScheduledTick(this::tick,
                t -> LOG.infof("Scheduled crawl tick abandoned during shutdown: %s", t.getMessage()));
    }

    private void tick() {
        if (shutdownSignal.isShuttingDown()) {
            LOG.info("Shutdown in progress: skipping scheduled crawl tick");
            return;
        }

        if (triggerRequestQueue.hasActive(TriggerKind.CRAWL)) {
            LOG.info("Skipping scheduled crawl tick: a crawl run is already active");
            return;
        }

        try {
            triggerRequestQueue.enqueue(TriggerKind.CRAWL, TriggerOrigin.SCHEDULED, null);
        } catch (ConflictException e) {
            // Race with a concurrent admin trigger between the hasActive check above and this
            // insert (story #582): the other caller won, so this tick simply yields.
            LOG.infof("Skipping scheduled crawl tick: lost the race to queue a crawl request: %s",
                    e.getMessage());
        }
    }
}
