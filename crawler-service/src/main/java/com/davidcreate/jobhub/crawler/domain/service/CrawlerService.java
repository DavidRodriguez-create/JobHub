package com.davidcreate.jobhub.crawler.domain.service;

import com.davidcreate.jobhub.crawler.domain.exception.ConflictException;
import com.davidcreate.jobhub.crawler.domain.exception.ValidationException;
import com.davidcreate.jobhub.crawler.domain.model.*;
import com.davidcreate.jobhub.crawler.domain.port.in.CrawlUseCase;
import com.davidcreate.jobhub.crawler.domain.port.out.CrawlProgressRecorder;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.JobSourceClient;
import com.davidcreate.jobhub.crawler.domain.port.out.PullTargetRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class CrawlerService implements CrawlUseCase {

    private static final Logger LOG = Logger.getLogger(CrawlerService.class);

    private final PullTargetRepository pullTargetRepository;
    private final JobPostRepository jobPostRepository;
    private final Instance<JobSourceClient> clients;
    private final TriggerRequestQueue triggerRequestQueue;
    private final CrawlProgressRecorder progressRecorder;

    @ConfigProperty(name = "crawler.failure.cooldown-minutes", defaultValue = "15")
    int cooldownMinutes;
    @ConfigProperty(name = "crawler.failure.cooldown-rate-limit-hours", defaultValue = "1")
    int cooldownRateLimitHours;
    @ConfigProperty(name = "crawler.failure.cooldown-unavailable-minutes", defaultValue = "30")
    int cooldownUnavailableMinutes;
    @ConfigProperty(name = "crawler.failure.cooldown-not-found-days", defaultValue = "1")
    int cooldownNotFoundDays;

    @ConfigProperty(name = "crawler.crawl.min-new-posts", defaultValue = "100")
    int minNewPosts;

    @ConfigProperty(name = "crawler.crawl.max-targets-per-run", defaultValue = "200")
    int maxTargetsPerRun;

    // Use case: crawl by ID (REST triggered)

    @Override
    @Transactional
    public void crawl(UUID targetId) {
        pullTargetRepository.findAndLockById(targetId)
                .ifPresentOrElse(
                        target -> doCrawl(target),
                        () -> {
                            throw new ConflictException(
                                    String.format("Target %s already locked or unavailable, skipping", targetId));
                        });
    }

    // Use case: crawl batch (scheduler + REST triggered)

    private static final ShutdownSignal NO_SHUTDOWN = () -> false;

    @Override
    public CrawlBatchResult crawlBatch(int minNewPostsTarget) {
        return crawlBatch(minNewPostsTarget, null);
    }

    @Override
    public CrawlBatchResult crawlBatch(int minNewPostsTarget, UUID triggerRequestId) {
        return crawlBatch(minNewPostsTarget, triggerRequestId, NO_SHUTDOWN);
    }

    @Override
    public CrawlBatchResult crawlBatch(int minNewPostsTarget, UUID triggerRequestId, ShutdownSignal shutdownSignal) {
        if (minNewPostsTarget < 1) {
            throw new ValidationException("limit must be >= 1, got " + minNewPostsTarget);
        }

        // Registers this batch as in-flight for its whole duration (not per item), so the
        // ShutdownEvent observer's bounded drain knows to wait for it (story #398, D1).
        shutdownSignal.workStarted();
        try {
            return runCrawlBatch(minNewPostsTarget, triggerRequestId, shutdownSignal);
        } finally {
            shutdownSignal.workFinished();
        }
    }

    private CrawlBatchResult runCrawlBatch(int minNewPostsTarget, UUID triggerRequestId, ShutdownSignal shutdownSignal) {
        int targetsVisited = 0;
        int newPostsAccumulated = 0;
        boolean cancelled = false;

        while (true) {
            // 1. Cancellation check (trigger-driven runs only)
            if (triggerRequestId != null && triggerRequestQueue.isCancelRequested(triggerRequestId)) {
                cancelled = true;
                break;
            }

            // 1b. Shutdown check (ADR 0032, story #398): stop at the item boundary, no
            // new external call or transaction starts once shutdown has begun.
            if (shutdownSignal.isShuttingDown()) {
                break;
            }

            // 2. Safety cap: targets visited >= max-targets-per-run
            if (targetsVisited >= maxTargetsPerRun) {
                break;
            }

            // 3. Call crawlNext(); if empty, no source available
            Optional<CrawlOutcome> stepResult;
            try {
                stepResult = crawlNext(triggerRequestId);
            } catch (Exception e) {
                // A drain-timeout interrupt (ADR 0032, story #398, D1) lands here: the current
                // item's HTTP/DB call was cut short mid-flight once shutdown began. Expected,
                // not a fault -- swallow quietly (no stack trace) rather than let it propagate
                // toward Quarkus's uncaught-exception handler against a closing
                // EntityManagerFactory. Reads the CDI-free ShutdownFlag directly, not
                // shutdownSignal.isShuttingDown(): by now the CDI container may already be
                // gone, and calling ANY method through an injected proxy at that point can
                // itself throw (4th pass).
                if (ShutdownFlag.isRaised()) {
                    LOG.infof("Crawl abandoned during shutdown: %s", e.getMessage());
                    break;
                }
                throw e;
            }
            if (stepResult.isEmpty()) {
                break;
            }

            // 4. Accumulate
            CrawlOutcome outcome = stepResult.get();
            targetsVisited++;
            newPostsAccumulated += outcome.getNewPosts();

            // Live progress: running totals plus the just-finished target's own found/new pair
            // (ADR 0029). Uses exactly the same two counters as the log line below.
            recordTargetCompleted(triggerRequestId, CrawlProgress.builder()
                    .targetsVisited(targetsVisited)
                    .newPosts(newPostsAccumulated)
                    .lastCompanyName(outcome.getCompanyName())
                    .lastSourceType(outcome.getSourceType())
                    .lastFoundPosts(outcome.getFoundPosts())
                    .lastNewPosts(outcome.getNewPosts())
                    .build());

            LOG.infof("Crawl progress: %d targets visited, %d new posts so far (target %d)",
                    targetsVisited, newPostsAccumulated, minNewPostsTarget);

            // 5. Stop if new-post target reached (after completing the whole source step)
            if (newPostsAccumulated >= minNewPostsTarget) {
                break;
            }
        }

        // The loop has exited for any reason (exhaustion, safety cap, target reached,
        // cancellation, shutdown): a finished run must never claim to still be crawling
        // something.
        clearCurrentTarget(triggerRequestId);

        boolean noTargets = !cancelled && targetsVisited == 0 && newPostsAccumulated == 0;

        if (cancelled) {
            LOG.infof("Batch cancelled: %d targets visited, %d new posts before stop",
                    targetsVisited, newPostsAccumulated);
        } else if (noTargets) {
            // N1 (story #398): zero eligible targets is a success, never a bare
            // "crawled 0 targets" -- the outcome is machine-readable (no_targets), and
            // the summary text says exactly this so the admin screen never parses prose.
            LOG.info("Batch complete: no more targets to crawl");
        } else {
            LOG.infof("Batch complete: %d targets visited, %d new posts",
                    targetsVisited, newPostsAccumulated);
        }

        return CrawlBatchResult.builder()
                .crawled(targetsVisited)
                .newPosts(newPostsAccumulated)
                .hasMore(false)
                .cancelled(cancelled)
                .outcome(noTargets ? TriggerOutcome.NO_TARGETS : TriggerOutcome.COMPLETED)
                .build();
    }

    // Each crawlNext is its own transaction

    @Transactional
    public Optional<CrawlOutcome> crawlNext(UUID triggerRequestId) {
        return pullTargetRepository.findNextAvailableAndLock()
                .map(target -> {
                    // Marked before the (slow) HTTP fetch so the admin screen can show
                    // "crawling X" for the whole time this target takes (ADR 0029).
                    markCurrentTarget(triggerRequestId, target.getCompanyName(), target.getSourceType());
                    return doCrawl(target);
                });
    }

    // Private helpers

    private CrawlOutcome doCrawl(PullTarget target) {
        LOG.infof("Crawling: %s (%s)", target.getCompanyName(), target.getSourceType());

        // 1. set locked_by for observability
        target.lock(resolveWorkerId());

        // 2. find right client
        JobSourceClient client = findClient(target.getSourceType());

        // 3. crawl
        PullResult result = client.crawl(target);

        // 4. persist jobs + update target state
        int newPostCount = 0;
        int foundPosts = 0;
        if (result.isSuccess()) {
            foundPosts = result.getJobs().size();
            newPostCount = persistJobs(result.getJobs());
            target.recordSuccess(OffsetDateTime.now().plusHours(1));
            // Success-only (ADR 0029): a "0 found, 0 new" line right after the WARN below
            // would be exactly the noise this story removes.
            LOG.infof("Crawled %s (%s): %d found, %d new",
                    target.getCompanyName(), target.getSourceType(), foundPosts, newPostCount);
        } else {
            LOG.warnf("Crawl failed for %s: %s (HTTP %s)",
                    target.getCompanyName(), result.getErrorReason(), result.getHttpStatus());
            target.recordFailure(result.getErrorReason(), resolveCooldown(result.getHttpStatus()));
        }

        // 5. one save covers lock + result
        pullTargetRepository.save(target);

        return CrawlOutcome.builder()
                .result(result)
                .newPosts(newPostCount)
                .companyName(target.getCompanyName())
                .sourceType(target.getSourceType())
                .foundPosts(foundPosts)
                .build();
    }

    // ─── Progress reporting (ADR 0029) ─────────────────────────────────────────
    // Guarded here (null triggerRequestId = scheduler path = zero interactions with the
    // recorder) and defensively try/catch-wrapped here too: even though the adapter's own
    // contract promises never to throw, a broken progress port must never touch the crawl's
    // own outcome.

    private void markCurrentTarget(UUID triggerRequestId, String companyName, String sourceType) {
        if (triggerRequestId == null) {
            return;
        }
        try {
            progressRecorder.markCurrentTarget(triggerRequestId, companyName, sourceType);
        } catch (Exception e) {
            LOG.warnf(e, "Progress recorder failed to mark current target for %s", triggerRequestId);
        }
    }

    private void recordTargetCompleted(UUID triggerRequestId, CrawlProgress progress) {
        if (triggerRequestId == null) {
            return;
        }
        try {
            progressRecorder.recordTargetCompleted(triggerRequestId, progress);
        } catch (Exception e) {
            LOG.warnf(e, "Progress recorder failed to record target completion for %s", triggerRequestId);
        }
    }

    private void clearCurrentTarget(UUID triggerRequestId) {
        if (triggerRequestId == null) {
            return;
        }
        try {
            progressRecorder.clearCurrentTarget(triggerRequestId);
        } catch (Exception e) {
            LOG.warnf(e, "Progress recorder failed to clear current target for %s", triggerRequestId);
        }
    }

    private int persistJobs(List<JobPost> jobs) {
        // Insert only genuinely-new postings. A posting is "already stored" when its
        // content hash matches (unchanged) OR its URL matches (same posting, content
        // changed) -- in both cases we just bump last_seen_at. Inserting a row whose URL
        // already exists would violate uq_job_post_url and abort the whole batch.
        Map<String, JobPost> newByUrl = new LinkedHashMap<>();
        for (JobPost job : jobs) {
            if (isAlreadyStored(job)) {
                continue;
            }
            // De-dupe within this pull too: a board can return the same URL twice.
            newByUrl.putIfAbsent(job.getUrl(), job);
        }

        if (!newByUrl.isEmpty()) {
            jobPostRepository.saveAll(List.copyOf(newByUrl.values()));
        }

        return newByUrl.size();
    }

    private boolean isAlreadyStored(JobPost job) {
        Optional<JobPost> existing = jobPostRepository.findByContentHash(job.getContentHash());
        if (existing.isEmpty()) {
            existing = jobPostRepository.findByUrl(job.getUrl());
        }
        existing.ifPresent(stored -> {
            stored.markSeenAgain();
            jobPostRepository.save(stored);
        });
        return existing.isPresent();
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
