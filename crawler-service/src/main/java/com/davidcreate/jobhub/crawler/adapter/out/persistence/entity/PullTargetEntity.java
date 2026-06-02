package com.davidcreate.jobhub.crawler.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "pull_target", schema = "crawler")
public class PullTargetEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "source_type", nullable = false)
    public String sourceType;

    @Column(name = "company_name", nullable = false)
    public String companyName;

    @Column(name = "company_logo_url")
    public String companyLogoUrl;

    @Column(name = "token")
    public String token;

    @Column(name = "scraper_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    public String scraperConfig;

    @Column(name = "pull_priority", nullable = false)
    public short pullPriority;

    @Column(name = "next_pull_after", nullable = false)
    public OffsetDateTime nextPullAfter;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "status_reason")
    public String statusReason;

    @Column(name = "status_changed_at", nullable = false)
    public OffsetDateTime statusChangedAt;

    @Column(name = "locked_by")
    public String lockedBy;

    @Column(name = "lease_expires_at")
    public OffsetDateTime leaseExpiresAt;

    @Column(name = "last_successful_pull")
    public OffsetDateTime lastSuccessfulPull;

    @Column(name = "last_pull_attempt")
    public OffsetDateTime lastPullAttempt;

    @Column(name = "consecutive_failures", nullable = false)
    public short consecutiveFailures;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}