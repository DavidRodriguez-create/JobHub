package com.davidcreate.jobhub.job.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps {@code crawler.trigger_request} (owned by crawler-service, see
 * {@code db/init/016-crawler-trigger-request.sql}). Story #583 / ADR 0033:
 * crawler-service became the sole writer of this table and {@code db/init/061}
 * revoked INSERT and UPDATE from {@code job_user}, so every column here is mapped
 * {@code insertable = false, updatable = false}: job-service only ever SELECTs this
 * table (the admin status/history panel). Queueing and cancelling now go through
 * {@code CrawlerTriggerGateway}, a REST call to crawler-service's internal endpoints.
 */
@Getter
@Setter
@Entity
@Table(name = "trigger_request", schema = "crawler")
public class TriggerRequestEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    public UUID id;

    @Column(name = "kind", insertable = false, updatable = false)
    public String kind;

    @Column(name = "status", insertable = false, updatable = false)
    public String status;

    @Column(name = "requested_by", insertable = false, updatable = false)
    public UUID requestedBy;

    @Column(name = "requested_at", insertable = false, updatable = false)
    public OffsetDateTime requestedAt;

    @Column(name = "started_at", insertable = false, updatable = false)
    public OffsetDateTime startedAt;

    @Column(name = "finished_at", insertable = false, updatable = false)
    public OffsetDateTime finishedAt;

    @Column(name = "result_summary", insertable = false, updatable = false)
    public String resultSummary;

    @Column(name = "error_reason", insertable = false, updatable = false)
    public String errorReason;

    @Column(name = "progress_targets_visited", insertable = false, updatable = false)
    public Integer progressTargetsVisited;

    @Column(name = "progress_new_posts", insertable = false, updatable = false)
    public Integer progressNewPosts;

    @Column(name = "progress_current_company", insertable = false, updatable = false)
    public String progressCurrentCompany;

    @Column(name = "progress_current_source_type", insertable = false, updatable = false)
    public String progressCurrentSourceType;

    @Column(name = "progress_last_company", insertable = false, updatable = false)
    public String progressLastCompany;

    @Column(name = "progress_last_source_type", insertable = false, updatable = false)
    public String progressLastSourceType;

    @Column(name = "progress_last_found_posts", insertable = false, updatable = false)
    public Integer progressLastFoundPosts;

    @Column(name = "progress_last_new_posts", insertable = false, updatable = false)
    public Integer progressLastNewPosts;

    @Column(name = "progress_updated_at", insertable = false, updatable = false)
    public OffsetDateTime progressUpdatedAt;

    @Column(name = "origin", insertable = false, updatable = false)
    public String origin;

    @Column(name = "outcome", insertable = false, updatable = false)
    public String outcome;
}
