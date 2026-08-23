package com.davidcreate.jobhub.application.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "job_post_snapshot", schema = "applications")
public class JobPostSnapshotEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "job_post_id", nullable = false)
    public UUID jobPostId;

    @Column(name = "content_hash", nullable = false, unique = true)
    public String contentHash;

    @Column(name = "title", nullable = false)
    public String title;

    @Column(name = "company")
    public String company;

    @Column(name = "company_logo_url")
    public String companyLogoUrl;

    @Column(name = "url")
    public String url;

    @Column(name = "location")
    public String location;

    @Column(name = "snapshotted_at", nullable = false, updatable = false)
    public OffsetDateTime snapshottedAt;
}
