package com.davidcreate.jobhub.crawler.domain.service;

import com.davidcreate.jobhub.crawler.domain.exception.ConflictException;
import com.davidcreate.jobhub.crawler.domain.exception.ValidationException;
import com.davidcreate.jobhub.crawler.domain.model.*;
import com.davidcreate.jobhub.crawler.domain.port.in.CrawlUseCase;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.JobSourceClient;
import com.davidcreate.jobhub.crawler.domain.port.out.PullTargetRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class CrawlerService implements CrawlUseCase {

    private static final Logger LOG = Logger.getLogger(CrawlerService.class);

    private final PullTargetRepository pullTargetRepository;
    private final JobPostRepository jobPostRepository;
    private final Instance<JobSourceClient> clients;

    @ConfigProperty(name = "crawler.failure.cooldown-minutes", defaultValue = "15")
    int cooldownMinutes;
    @ConfigProperty(name = "crawler.failure.cooldown-rate-limit-hours", defaultValue = "1")
    int cooldownRateLimitHours;
    @ConfigProperty(name = "crawler.failure.cooldown-unavailable-minutes", defaultValue = "30")
    int cooldownUnavailableMinutes;
    @ConfigProperty(name = "crawler.failure.cooldown-not-found-days", defaultValue = "1")
    int cooldownNotFoundDays;

    // ─── Use case: crawl by ID (REST triggered) ───────────────────────────────

    @Override
    @Transactional
    public void crawl(UUID targetId) {
        pullTargetRepository.findAndLockById(targetId)
                .ifPresentOrElse(
                        this::doCrawl,
                        () -> {
                            throw new ConflictException(
                                    String.format("Target %s already locked or unavailable — skipping", targetId));
                        });
    }

    // ─── Use case: crawl batch (scheduler + REST triggered) ──────────────────

    @Override
    public CrawlBatchResult crawlBatch(int limit) {
        if (limit < 1 || limit > 50) {
            throw new ValidationException("limit must be between 1 and 50");
        }

        int count = 0;
        while (count < limit) {
            if (!crawlNext())
                break;
            count++;
        }

        LOG.infof("Batch complete: %d targets crawled", count);
        return CrawlBatchResult.builder()
                .crawled(count)
                .hasMore(false)
                .build();
    }

    // ─── Each crawlNext is its own transaction ────────────────────────────────

    @Transactional
    public boolean crawlNext() {
        return pullTargetRepository.findNextAvailableAndLock()
                .map(target -> {
                    doCrawl(target);
                    return true;
                })
                .orElse(false);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private PullResult doCrawl(PullTarget target) {
        LOG.infof("Crawling: %s (%s)", target.getCompanyName(), target.getSourceType());

        // 1. set locked_by for observability
        target.lock(resolveWorkerId());

        // 2. find right client
        JobSourceClient client = findClient(target.getSourceType());

        // 3. crawl
        PullResult result = client.crawl(target);

        // 4. persist jobs + update target state
        if (result.isSuccess()) {
            persistJobs(result.getJobs());
            target.recordSuccess(OffsetDateTime.now().plusHours(1));
        } else {
            LOG.warnf("Crawl failed for %s: %s (HTTP %s)",
                    target.getCompanyName(), result.getErrorReason(), result.getHttpStatus());
            target.recordFailure(result.getErrorReason(), resolveCooldown(result.getHttpStatus()));
        }

        // 5. one save covers lock + result
        pullTargetRepository.save(target);

        return result;
    }

    private void persistJobs(List<JobPost> jobs) {
        List<JobPost> newJobs = jobs.stream()
                .filter(job -> {
                    Optional<JobPost> existing = jobPostRepository.findByContentHash(job.getContentHash());
                    existing.ifPresent(JobPost::markSeenAgain);
                    existing.ifPresent(jobPostRepository::save);
                    return existing.isEmpty();
                })
                .toList();

        if (!newJobs.isEmpty()) {
            jobPostRepository.saveAll(newJobs);
        }
    }

    private JobSourceClient findClient(String sourceType) {
        return clients.stream()
                .filter(c -> c.supports(sourceType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No client found for sourceType: " + sourceType));
    }

    private OffsetDateTime resolveCooldown(Integer httpStatus) {
        if (httpStatus == null)
            return OffsetDateTime.now().plusMinutes(cooldownMinutes);
        return switch (httpStatus) {
            case 429 -> OffsetDateTime.now().plusHours(cooldownRateLimitHours);
            case 503 -> OffsetDateTime.now().plusMinutes(cooldownUnavailableMinutes);
            case 404 -> OffsetDateTime.now().plusDays(cooldownNotFoundDays);
            default -> OffsetDateTime.now().plusMinutes(cooldownMinutes);
        };
    }

    private String resolveWorkerId() {
        return System.getenv().getOrDefault("HOSTNAME", "worker-local");
    }
}