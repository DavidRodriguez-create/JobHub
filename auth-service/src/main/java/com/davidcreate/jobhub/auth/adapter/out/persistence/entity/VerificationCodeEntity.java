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
@Table(name = "verification_code", schema = "auth")
public class VerificationCodeEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "action", nullable = false, length = 40)
    public String action;

    @Column(name = "code_hash", nullable = false, columnDefinition = "text")
    public String codeHash;

    @Column(name = "expires_at", nullable = false)
    public OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    public OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;
}
