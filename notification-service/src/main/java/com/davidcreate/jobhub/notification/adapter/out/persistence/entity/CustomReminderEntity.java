package com.davidcreate.jobhub.notification.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "custom_reminder", schema = "notification")
public class CustomReminderEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "application_id", nullable = false)
    public UUID applicationId;

    @Column(name = "title", nullable = false)
    public String title;

    @Column(name = "note")
    public String note;

    @Column(name = "trigger_at_utc", nullable = false)
    public OffsetDateTime triggerAtUtc;

    @Column(name = "channels", nullable = false)
    public String channels;

    @Column(name = "stage")
    public String stage;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "channels_fired")
    public String channelsFired;

    @Column(name = "fired_at_utc")
    public OffsetDateTime firedAtUtc;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
