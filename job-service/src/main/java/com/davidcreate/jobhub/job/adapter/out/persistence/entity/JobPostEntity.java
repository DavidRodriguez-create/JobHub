package com.davidcreate.jobhub.job.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "job_post", schema = "crawler")
public class JobPostEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "target_id", nullable = false, insertable = false, updatable = false)
    public UUID targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", referencedColumnName = "id")
    public PullTargetEntity target;

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

    @Column(name = "career_level")
    public String careerLevel;

    // Story #407: explicit columnDefinition matches prod's crawler.job_post.languages
    // (TEXT[], db/init/010-crawler.sql) exactly, so the DevServices drop-and-create test
    // schema stops silently drifting to Hibernate's default varchar[] inference for
    // List<String> — that drift is what let the array-type-mismatch bug (#407) hide behind
    // green-but-vacuous tests (varchar[] && varchar[] never errors; prod's text[] && the
    // bound varchar[] parameter does).
    @Column(name = "languages", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> languages;

    @Column(name = "requirements")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> requirements;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    public OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    public OffsetDateTime lastSeenAt;

    // Read-only (Story #332 / ADR 0020): crawler-service's own enrichment pass owns writes
    // to this column (its JobPostEntity maps it read-write); job-service only ever reads it,
    // as the crawl-generation stamp's GREATEST(MAX(last_seen_at), MAX(enriched_at)) needs a
    // mapping to exist so Hibernate's drop-and-create test schema has the column at all.
    @Column(name = "enriched_at", insertable = false, updatable = false)
    public OffsetDateTime enrichedAt;

    @Column(name = "search_vector", insertable = false, updatable = false, columnDefinition = "tsvector")
    public String searchVector;

    // Read-only: crawler.job_post_location (ADR 0017). Every field on the child entity is
    // insertable=false/updatable=false — job-service only ever SELECTs this table (see the
    // grant in db/init/014-crawler-job-post-location.sql). Order is arbitrary here; the
    // mapper (not JPA/DB ordering) guarantees primary-first when composing the domain list.
    //
    // Story #333 (#405): @BatchSize (not a page-level JOIN FETCH) keeps this lazy collection
    // off the paginated main query entirely — no risk of Hibernate paginating the LEFT JOIN
    // FETCH result set in memory (HHH000104) or duplicating a multi-opening post's parent row.
    // Instead, the first post in a result page whose locations are accessed triggers one
    // "WHERE job_post_id = ANY(:ids)" batch query covering every still-uninitialized post from
    // that page, sized to job.search.max-size (100, see application.properties) so even the
    // largest allowed page resolves in exactly one extra statement, not one per row.
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_post_id", referencedColumnName = "id", insertable = false, updatable = false)
    @BatchSize(size = 100)
    public List<JobPostLocationEntity> locations = new ArrayList<>();
}
