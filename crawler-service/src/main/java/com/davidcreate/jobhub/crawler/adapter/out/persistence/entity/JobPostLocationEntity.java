package com.davidcreate.jobhub.crawler.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One opening (country/city pair) of a {@code crawler.job_post} row. Mirrors
 * {@code db/init/014-crawler-job-post-location.sql} (frozen migration, do not fork).
 * Exactly one row per {@code jobPostId} has {@code isPrimary = true} (DB partial unique
 * index {@code uq_job_post_location_one_primary}); {@code (jobPostId, country, city)} is
 * unique (DB constraint {@code uq_job_post_location_post_country_city}).
 */
@Getter
@Setter
@Entity
@Table(
        name = "job_post_location",
        schema = "crawler",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_job_post_location_post_country_city",
                columnNames = {"job_post_id", "country", "city"}))
public class JobPostLocationEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id")
    public UUID id;

    @Column(name = "job_post_id", nullable = false)
    public UUID jobPostId;

    // Read-only mapping of the same job_post_id column, purely so Hibernate's DDL
    // generation (dev/test drop-and-create) declares the FK WITH ON DELETE CASCADE,
    // matching the frozen migration (014-crawler-job-post-location.sql). Reads/writes go
    // through the plain jobPostId column above, not this relationship.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_post_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public JobPostEntity jobPost;

    @Column(name = "country")
    public String country;

    @Column(name = "city")
    public String city;

    @Column(name = "is_primary", nullable = false)
    @ColumnDefault("false")
    public boolean isPrimary;

    @Column(name = "position", nullable = false)
    @ColumnDefault("0")
    public short position;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("now()")
    public OffsetDateTime createdAt;
}
