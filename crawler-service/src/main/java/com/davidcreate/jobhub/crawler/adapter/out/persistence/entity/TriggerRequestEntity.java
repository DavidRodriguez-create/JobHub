package com.davidcreate.jobhub.crawler.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "trigger_request", schema = "crawler")
public class TriggerRequestEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "kind", nullable = false)
    public String kind;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "requested_by")
    public UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    public OffsetDateTime requestedAt;

    @Column(name = "started_at")
    public OffsetDateTime startedAt;

    @Column(name = "finished_at")
    public OffsetDateTime finishedAt;

    @Column(name = "result_summary")
    public String resultSummary;

    @Column(name = "error_reason")
    public String errorReason;

    @Column(name = "locked_by")
    public String lockedBy;

    @Column(name = "lease_expires_at")
    public OffsetDateTime leaseExpiresAt;

    // ─── Origin / outcome (ADR 0032, story #398) ────────────────────────────────
    // origin distinguishes the automatic scheduled crawl from an admin-triggered run
    // (both now flow through the same trigger_request pipeline); outcome is the
    // machine-readable terminal result (completed/no_targets/cancelled/interrupted/failed),
    // status stays succeeded for no_targets.

    // Defaults to "manual" so existing test/helper code that builds a bare entity (pre-#398
    // call sites) keeps working without every call site having to set it explicitly --
    // matches the DB column's own DEFAULT 'manual' (db/init/059).
    @Column(name = "origin", nullable = false)
    public String origin = "manual";

    @Column(name = "outcome")
    public String outcome;

    // ─── Live progress (ADR 0029, story #513) ──────────────────────────────────
    // crawler-service is the sole writer (targeted REQUIRES_NEW updates via
    // CrawlProgressRecorder); job-service maps these read-only. All nullable, no
    // default: progressUpdatedAt == null means "never reported" (queued/enrichment/
    // pre-feature runs), distinct from progressNewPosts == 0 ("reported, nothing new yet").

    @Column(name = "progress_targets_visited")
    public Integer progressTargetsVisited;

    @Column(name = "progress_new_posts")
    public Integer progressNewPosts;

    @Column(name = "progress_current_company")
    public String progressCurrentCompany;

    @Column(name = "progress_current_source_type")
    public String progressCurrentSourceType;

    @Column(name = "progress_last_company")
    public String progressLastCompany;

    @Column(name = "progress_last_source_type")
    public String progressLastSourceType;

    @Column(name = "progress_last_found_posts")
    public Integer progressLastFoundPosts;

    @Column(name = "progress_last_new_posts")
    public Integer progressLastNewPosts;

    @Column(name = "progress_updated_at")
    public OffsetDateTime progressUpdatedAt;
}
