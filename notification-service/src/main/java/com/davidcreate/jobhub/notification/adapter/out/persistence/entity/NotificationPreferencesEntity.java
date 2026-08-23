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
@Table(name = "notification_preferences", schema = "notification")
public class NotificationPreferencesEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    public UUID userId;

    @Column(name = "weekly_digest_email", nullable = false)
    public boolean weeklyDigestEmail;

    @Column(name = "in_app_notifications_enabled", nullable = false)
    public boolean inAppNotificationsEnabled;

    @Column(name = "interview_reminders", nullable = false)
    public boolean interviewReminders;

    @Column(name = "interview_reminder_email", nullable = false)
    public boolean interviewReminderEmail;

    @Column(name = "ghosted_alert", nullable = false)
    public boolean ghostedAlert;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
