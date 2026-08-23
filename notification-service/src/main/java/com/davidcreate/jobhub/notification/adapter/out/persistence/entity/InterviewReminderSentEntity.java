package com.davidcreate.jobhub.notification.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "interview_reminder_sent", schema = "notification")
public class InterviewReminderSentEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "application_id", nullable = false)
    public UUID applicationId;

    @Column(name = "reminder_offset", nullable = false)
    public String reminderOffset;

    @Column(name = "next_step_date", nullable = false)
    public LocalDate nextStepDate;

    @Column(name = "channels", nullable = false)
    public String channels;

    @Column(name = "sent_at", nullable = false)
    public Instant sentAt;
}
