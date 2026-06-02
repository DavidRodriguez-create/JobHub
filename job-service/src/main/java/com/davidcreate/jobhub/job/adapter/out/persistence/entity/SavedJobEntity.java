package com.davidcreate.jobhub.job.adapter.out.persistence.entity;

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
@Table(name = "saved_job", schema = "crawler")
public class SavedJobEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "job_id", nullable = false)
    public UUID jobId;

    @Column(name = "saved_at", nullable = false)
    public OffsetDateTime savedAt;
}
