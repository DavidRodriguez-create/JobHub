package com.davidcreate.jobhub.job.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only mapping of {@code crawler.job_post_location} (owned by crawler-service,
 * see {@code db/init/014-crawler-job-post-location.sql}, frozen by ADR 0017). job_user
 * has SELECT-only on this table in prod; every field is {@code insertable=false,
 * updatable=false} so job-service can never attempt a write, while still letting
 * Hibernate's test-time {@code drop-and-create} DDL generation create the table before
 * {@code test-seeds.sql} runs.
 */
@Getter
@Setter
@Entity
@Table(name = "job_post_location", schema = "crawler")
public class JobPostLocationEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", insertable = false, updatable = false)
    public UUID id;

    @Column(name = "job_post_id", nullable = false, insertable = false, updatable = false)
    public UUID jobPostId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_post_id", insertable = false, updatable = false)
    public JobPostEntity jobPost;

    @Column(name = "country", insertable = false, updatable = false)
    public String country;

    @Column(name = "city", insertable = false, updatable = false)
    public String city;

    @Column(name = "is_primary", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("false")
    public boolean isPrimary;

    @Column(name = "position", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("0")
    public short position;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("now()")
    public OffsetDateTime createdAt;
}
