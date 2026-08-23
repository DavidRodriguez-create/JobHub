package com.davidcreate.jobhub.auth.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.ApplyProfileEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.ApplyProfileMapper;
import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-U5: domain/persistence-entity mapper round-trips every field, including
 * languages order, without lossy null <-> [] conversion in either direction.
 */
@DisplayName("ApplyProfileMapper (persistence) Unit Tests — BE-U5")
class ApplyProfileMapperTest {

    ApplyProfileMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ApplyProfileMapper();
    }

    @Test
    @DisplayName("BE-U5: fully-populated domain object round-trips through the JPA entity unchanged")
    void roundTripsFullyPopulatedProfile() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(3);
        OffsetDateTime updatedAt = OffsetDateTime.now();

        ApplyProfile original = ApplyProfile.builder()
                .id(id)
                .userId(userId)
                .workAuthorization("US Citizen")
                .requiresSponsorship(false)
                .noticePeriod("2 weeks")
                .salaryExpectation("$120k-$140k")
                .currentLocation("Madrid, Spain")
                .willingToRelocate(true)
                .linkedinUrl("https://linkedin.com/in/alice")
                .githubUrl("https://github.com/alice")
                .portfolioUrl("https://alice.dev")
                .languages(List.of("English (native)", "Spanish (C1)", "French (B2)"))
                .roomToGrow("Grow into a staff engineer role")
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        ApplyProfileEntity entity = mapper.toEntity(original);
        ApplyProfile roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(id);
        assertThat(roundTripped.getUserId()).isEqualTo(userId);
        assertThat(roundTripped.getWorkAuthorization()).isEqualTo("US Citizen");
        assertThat(roundTripped.getRequiresSponsorship()).isFalse();
        assertThat(roundTripped.getNoticePeriod()).isEqualTo("2 weeks");
        assertThat(roundTripped.getSalaryExpectation()).isEqualTo("$120k-$140k");
        assertThat(roundTripped.getCurrentLocation()).isEqualTo("Madrid, Spain");
        assertThat(roundTripped.getWillingToRelocate()).isTrue();
        assertThat(roundTripped.getLinkedinUrl()).isEqualTo("https://linkedin.com/in/alice");
        assertThat(roundTripped.getGithubUrl()).isEqualTo("https://github.com/alice");
        assertThat(roundTripped.getPortfolioUrl()).isEqualTo("https://alice.dev");
        assertThat(roundTripped.getLanguages())
                .containsExactly("English (native)", "Spanish (C1)", "French (B2)");
        assertThat(roundTripped.getRoomToGrow()).isEqualTo("Grow into a staff engineer role");
        assertThat(roundTripped.getCreatedAt()).isEqualTo(createdAt);
        assertThat(roundTripped.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("BE-U5: null fields on the domain object stay null after the round trip (no null -> \"\"/[] leak)")
    void roundTripsAllNullFields() {
        ApplyProfile allNull = ApplyProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .build();

        ApplyProfileEntity entity = mapper.toEntity(allNull);
        // The NOT NULL jsonb column always stores at least '[]'; the mapper still
        // presents that back as null to the domain (BE-U3's "no languages" shape).
        assertThat(entity.languages).isEmpty();

        ApplyProfile roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped.getWorkAuthorization()).isNull();
        assertThat(roundTripped.getRequiresSponsorship()).isNull();
        assertThat(roundTripped.getNoticePeriod()).isNull();
        assertThat(roundTripped.getSalaryExpectation()).isNull();
        assertThat(roundTripped.getCurrentLocation()).isNull();
        assertThat(roundTripped.getWillingToRelocate()).isNull();
        assertThat(roundTripped.getLinkedinUrl()).isNull();
        assertThat(roundTripped.getGithubUrl()).isNull();
        assertThat(roundTripped.getPortfolioUrl()).isNull();
        assertThat(roundTripped.getLanguages()).isNull();
        assertThat(roundTripped.getRoomToGrow()).isNull();
    }

    @Test
    @DisplayName("updateEntity leaves id/userId/createdAt untouched, updates answer fields only")
    void updateEntityKeepsImmutableFields() {
        ApplyProfileEntity entity = new ApplyProfileEntity();
        UUID originalId = UUID.randomUUID();
        UUID originalUserId = UUID.randomUUID();
        OffsetDateTime originalCreatedAt = OffsetDateTime.now().minusDays(10);
        entity.id = originalId;
        entity.userId = originalUserId;
        entity.createdAt = originalCreatedAt;
        entity.workAuthorization = "old";

        ApplyProfile update = ApplyProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .workAuthorization("new")
                .languages(List.of("English"))
                .build();

        mapper.updateEntity(entity, update);

        assertThat(entity.id).isEqualTo(originalId);
        assertThat(entity.userId).isEqualTo(originalUserId);
        assertThat(entity.createdAt).isEqualTo(originalCreatedAt);
        assertThat(entity.workAuthorization).isEqualTo("new");
        assertThat(entity.languages).containsExactly("English");
    }
}
