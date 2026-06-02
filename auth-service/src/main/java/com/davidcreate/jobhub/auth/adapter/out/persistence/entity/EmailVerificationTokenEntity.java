package com.davidcreate.jobhub.auth.adapter.out.persistence.entity;

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
@Table(name = "email_verification_token", schema = "auth")
public class EmailVerificationTokenEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "token", nullable = false, unique = true, columnDefinition = "text")
    public String token;

    @Column(name = "expires_at", nullable = false)
    public OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    public OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;
}
