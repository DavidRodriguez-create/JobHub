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
@Table(name = "notifications", schema = "notification")
public class NotificationEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "type", nullable = false)
    public String type;

    @Column(name = "title", nullable = false, length = 500)
    public String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    public String message;

    @Column(name = "read", nullable = false)
    public boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    @Column(name = "application_id", nullable = true)
    public UUID applicationId;
}
