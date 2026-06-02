package com.davidcreate.jobhub.application.adapter.out.persistence.entity;

import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "application", schema = "applications")
public class ApplicationEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "job_post_snapshot_id")
    public UUID jobPostSnapshotId;

    @Column(name = "user_job_post_id")
    public UUID userJobPostId;

    @Column(name = "job_post_id")
    public UUID jobPostId;

    @Column(name = "status", nullable = false, columnDefinition = "applications.status")
    @Convert(converter = ApplicationStatusConverter.class)
    @ColumnTransformer(read = "status::text", write = "?::applications.status")
    public ApplicationStatus status;

    @Column(name = "applied_at", nullable = false)
    public OffsetDateTime appliedAt;

    @Column(name = "ended_at")
    public OffsetDateTime endedAt;

    @Column(name = "notes", columnDefinition = "text")
    public String notes;

    @Column(name = "contact", columnDefinition = "text")
    public String contact;

    @Column(name = "portal_url", columnDefinition = "text")
    public String portalUrl;

    @Column(name = "next_step_label", columnDefinition = "text")
    public String nextStepLabel;

    @Column(name = "next_step_date")
    public LocalDate nextStepDate;

    @Column(name = "next_step_reminder_at")
    public OffsetDateTime nextStepReminderAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
