package com.davidcreate.jobhub.notification.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "digest_run", schema = "notification")
public class DigestRunEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "sent_at", nullable = false)
    public Instant sentAt;

    @Column(name = "job_count", nullable = false)
    public int jobCount;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "error_message")
    public String errorMessage;
}
