package com.davidcreate.jobhub.job.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Story #428 (ADR 0023 D1): {@code crawler.company}, owned/written/modelled by job-service
 * even though it physically lives in the crawler schema - exactly like {@code saved_job},
 * {@code saved_filter} and {@code trigger_request} already are.
 */
@Getter
@Setter
@Entity
@Table(name = "company", schema = "crawler")
public class CompanyEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    // Story #429: unique=true mirrors crawler.company's real uq_company_slug constraint
    // (db/init/051-job-company.sql) so the DevServices drop-and-create TEST schema also
    // enforces it - CompanyPanacheRepository#upsertBySlug's "ON CONFLICT (slug)" requires a
    // matching unique constraint to exist in test, just as it already does in prod.
    @Column(name = "slug", nullable = false, unique = true)
    public String slug;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "website")
    public String website;

    @Column(name = "industry")
    public String industry;

    @Column(name = "size")
    public String size;

    @Column(name = "headquarters")
    public String headquarters;

    @Column(name = "description")
    public String description;

    @Column(name = "logo_url")
    public String logoUrl;

    // Story #407 lesson: explicit columnDefinition pins the DevServices drop-and-create test
    // schema to text[] so it cannot silently drift to Hibernate's inferred varchar[] - that
    // drift is what hid the array-operator type-mismatch bug (#407) behind green-but-vacuous
    // tests. No filter/facet reads this column yet (that is a future story), but the column
    // shape must be correct from day one.
    @Column(name = "tags", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> tags;

    // DB-internal provenance (ADR 0023 D4): not exposed on the domain Company or the
    // CompanyInfo contract. Kept here only so job-service's own writes are self-describing.
    @Column(name = "source", nullable = false)
    public String source;

    @Column(name = "manually_edited", nullable = false)
    public Boolean manuallyEdited;

    // DB-internal bookkeeping (ADR 0023 D6 note): the contract has no createdAt property,
    // only updatedAt, so this is deliberately not mapped onto the domain Company.
    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
