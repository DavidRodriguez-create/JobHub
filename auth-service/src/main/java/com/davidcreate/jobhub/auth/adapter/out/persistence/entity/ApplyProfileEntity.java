package com.davidcreate.jobhub.auth.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "apply_profile", schema = "auth")
public class ApplyProfileEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    public UUID userId;

    @Column(name = "work_authorization")
    public String workAuthorization;

    @Column(name = "requires_sponsorship")
    public Boolean requiresSponsorship;

    @Column(name = "notice_period")
    public String noticePeriod;

    @Column(name = "salary_expectation")
    public String salaryExpectation;

    @Column(name = "current_location")
    public String currentLocation;

    @Column(name = "willing_to_relocate")
    public Boolean willingToRelocate;

    @Column(name = "linkedin_url")
    public String linkedinUrl;

    @Column(name = "github_url")
    public String githubUrl;

    @Column(name = "portfolio_url")
    public String portfolioUrl;

    @Column(name = "languages", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    public List<String> languages;

    @Column(name = "room_to_grow")
    public String roomToGrow;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
