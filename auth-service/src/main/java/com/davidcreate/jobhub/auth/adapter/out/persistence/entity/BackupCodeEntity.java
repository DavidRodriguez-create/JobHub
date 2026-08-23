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
@Table(name = "totp_backup_code", schema = "auth")
public class BackupCodeEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "totp_secret_id", nullable = false)
    public UUID totpSecretId;

    @Column(name = "code_hash", nullable = false, columnDefinition = "text")
    public String codeHash;

    @Column(name = "consumed_at")
    public OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;
}
