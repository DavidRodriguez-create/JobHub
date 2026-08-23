package com.davidcreate.jobhub.crawler.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "job_post", schema = "crawler")
public class JobPostEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "target_id", nullable = false)
    public UUID targetId;

    @Column(name = "title", nullable = false)
    public String title;

    @Column(name = "url", nullable = false)
    public String url;

    @Column(name = "description")
    public String description;

    @Column(name = "content_hash")
    public String contentHash;

    @Column(name = "city")
    public String city;

    @Column(name = "country")
    public String country;

    @Column(name = "compensation_min")
    public Integer compensationMin;

    @Column(name = "compensation_max")
    public Integer compensationMax;

    @Column(name = "employment_type")
    public String employmentType;

    @Column(name = "languages")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> languages;

    @Column(name = "requirements")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> requirements;

    @Column(name = "career_level")
    public String careerLevel;

    @Column(name = "enrichment_status", nullable = false)
    @ColumnDefault("'pending'")
    public String enrichmentStatus = "pending";

    @Column(name = "enriched_at")
    public OffsetDateTime enrichedAt;

    @Column(name = "enrichment_attempts", nullable = false)
    @ColumnDefault("0")
    public short enrichmentAttempts;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    public OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    public OffsetDateTime lastSeenAt;

    @Column(name = "search_vector", insertable = false, updatable = false, columnDefinition = "tsvector")
    public String searchVector;
}
