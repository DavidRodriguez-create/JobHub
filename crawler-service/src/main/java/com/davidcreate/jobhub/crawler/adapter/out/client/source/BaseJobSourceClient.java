package com.davidcreate.jobhub.crawler.adapter.out.client.source;

import com.davidcreate.jobhub.crawler.adapter.out.client.support.SalaryParser;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.http.HttpClient;
import java.time.Duration;

public abstract class BaseJobSourceClient {

    @Inject
    protected ObjectMapper objectMapper;

    @Inject
    protected SalaryParser salaryParser;

    @ConfigProperty(name = "crawler.http.connect-timeout-seconds", defaultValue = "10")
    int connectTimeoutSeconds;

    @ConfigProperty(name = "crawler.http.request-timeout-seconds", defaultValue = "30")
    int requestTimeoutSeconds;

    protected HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    protected Duration requestTimeout() {
        return Duration.ofSeconds(requestTimeoutSeconds);
    }

    /**
     * Story #398 (ADR 0032, D1, 5th pass): a drain-timeout interrupt lands as a generic
     * exception right here, inside {@code httpClient().send(...)} or the parsing that
     * follows it. Once the shutdown flag is up, that is expected, not a fault -- log a
     * single quiet line with no stack trace instead of the loud ERROR this catch uses the
     * rest of the time (a real safety net for genuine crawl failures outside shutdown).
     */
    protected static void logCrawlFailure(Logger log, String message, Exception e) {
        if (ShutdownFlag.isRaised()) {
            log.infof("%s abandoned during shutdown: %s", message, e.getMessage());
            return;
        }
        log.error(message, e);
    }
}
