package com.davidcreate.jobhub.auth.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "user_identity", schema = "auth",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_user_identity_provider_subject", columnNames = {"provider", "provider_user_id"}),
            @UniqueConstraint(name = "uq_user_identity_user_provider", columnNames = {"user_id", "provider"})
        })
public class UserIdentityEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "provider", nullable = false, length = 20)
    public String provider;

    @Column(name = "provider_user_id", nullable = false)
    public String providerUserId;

    @Column(name = "email")
    public String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;
}
