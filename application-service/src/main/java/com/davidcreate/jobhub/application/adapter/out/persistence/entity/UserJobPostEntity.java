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
@Table(name = "user_job_post", schema = "applications")
public class UserJobPostEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "title", nullable = false)
    public String title;

    @Column(name = "company")
    public String company;

    @Column(name = "url")
    public String url;

    @Column(name = "location")
    public String location;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
