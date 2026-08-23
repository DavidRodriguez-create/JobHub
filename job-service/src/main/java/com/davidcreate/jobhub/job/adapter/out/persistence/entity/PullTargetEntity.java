package com.davidcreate.jobhub.job.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "pull_target", schema = "crawler")
public class PullTargetEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "source_type", nullable = false)
    public String sourceType;

    @Column(name = "company_name", nullable = false)
    public String companyName;

    @Column(name = "company_logo_url")
    public String companyLogoUrl;

    // Story #428 (ADR 0023 D2): read-only mirror of the FK column, kept in sync by the
    // @ManyToOne relation below; writes go through a targeted bulk UPDATE
    // (PullTargetPanacheRepository.assignCompany) matching job_user's narrow
    // "UPDATE (company_id)" grant, not a full-entity merge/flush.
    @Column(name = "company_id", insertable = false, updatable = false)
    public UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", referencedColumnName = "id")
    public CompanyEntity company;
}
