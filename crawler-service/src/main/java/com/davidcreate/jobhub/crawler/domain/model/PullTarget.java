package com.davidcreate.jobhub.crawler.domain.model;

import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class PullTarget {

    private final UUID id;
    private final String sourceType;
    private final String companyName;
    private final String companyLogoUrl;
    private final String token;
    private final String scraperConfig;

    @Builder.Default
    private final short pullPriority = 100;

    @Builder.Default
    private OffsetDateTime nextPullAfter = OffsetDateTime.now();

    @Builder.Default
    private PullTargetStatus status = PullTargetStatus.ACTIVE;
    private String statusReason;
    private OffsetDateTime statusChangedAt;

    private String lockedBy;
    private OffsetDateTime leaseExpiresAt;

    private OffsetDateTime lastSuccessfulPull;
    private OffsetDateTime lastPullAttempt;

    @Builder.Default
    private short consecutiveFailures = 0;

    @Builder.Default
    private final OffsetDateTime createdAt = OffsetDateTime.now();
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    // ─── Domain behaviour ────────────────────────────────────────────────────

    public boolean isAvailable() {
        return status == PullTargetStatus.ACTIVE
                && lockedBy == null
                && (leaseExpiresAt == null || leaseExpiresAt.isBefore(OffsetDateTime.now()));
    }

    public void lock(String workerId) {
        this.lockedBy = workerId;
        this.leaseExpiresAt = OffsetDateTime.now().plusMinutes(30);
        this.lastPullAttempt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public void unlock() {
        this.lockedBy = null;
        this.leaseExpiresAt = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void recordSuccess(OffsetDateTime nextPullAfter) {
        this.lastSuccessfulPull = OffsetDateTime.now();
        this.consecutiveFailures = 0;
        this.statusReason = null;
        this.nextPullAfter = nextPullAfter;
        updateStatus(PullTargetStatus.ACTIVE);
        unlock();
    }

    public void recordFailure(String reason, OffsetDateTime cooldownUntil) {
        this.consecutiveFailures++;
        this.statusReason = reason;
        this.nextPullAfter = cooldownUntil;
        updateStatus(consecutiveFailures >= 5
                ? PullTargetStatus.DISABLED_TRANSIENT
                : PullTargetStatus.COOLDOWN);
        unlock();
    }

    public void disable(String reason) {
        this.statusReason = reason;
        updateStatus(PullTargetStatus.DISABLED_PERMANENT);
        unlock();
    }

    private void updateStatus(PullTargetStatus newStatus) {
        if (this.status != newStatus) {
            this.status = newStatus;
            this.statusChangedAt = OffsetDateTime.now();
        }
        this.updatedAt = OffsetDateTime.now();
    }
}