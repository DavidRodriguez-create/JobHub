package com.davidcreate.jobhub.crawler.adapter.in.scheduler;

import com.davidcreate.jobhub.crawler.domain.port.in.CrawlUseCase;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CrawlerScheduler {

    private static final Logger LOG = Logger.getLogger(CrawlerScheduler.class);

    private final CrawlUseCase crawlUseCase;

    @ConfigProperty(name = "crawler.schedule.batch-size", defaultValue = "10")
    int batchSize;

    public CrawlerScheduler(CrawlUseCase crawlUseCase) {
        this.crawlUseCase = crawlUseCase;
    }

    @Scheduled(cron = "${crawler.schedule.cron:0 0/10 * * * ?}")
    public void run() {
        LOG.infof("Crawler scheduler triggered — batch size: %d", batchSize);
        crawlUseCase.crawlBatch(batchSize);
    }
}