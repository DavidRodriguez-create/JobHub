package com.davidcreate.jobhub.application.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.application.adapter.out.persistence.entity.ApplicationEntity;
import com.davidcreate.jobhub.application.adapter.out.persistence.mapper.ApplicationMapper;
import com.davidcreate.jobhub.application.domain.entity.Application;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApplicationMapper Unit Tests")
class ApplicationMapperTest {

    private final ApplicationMapper mapper = new ApplicationMapper();

    @Test
    @DisplayName("toDomain copies all fields from entity")
    void toDomain() {
        var e = new ApplicationEntity();
        e.id = UUID.randomUUID();
        e.userId = UUID.randomUUID();
        e.jobPostSnapshotId = UUID.randomUUID();
        e.userJobPostId = null;
        e.status = ApplicationStatus.APPLIED;
        e.appliedAt = OffsetDateTime.now();
        e.endedAt = null;
        e.createdAt = OffsetDateTime.now();
        e.updatedAt = OffsetDateTime.now();

        Application d = mapper.toDomain(e);

        assertThat(d.getId()).isEqualTo(e.id);
        assertThat(d.getUserId()).isEqualTo(e.userId);
        assertThat(d.getJobPostSnapshotId()).isEqualTo(e.jobPostSnapshotId);
        assertThat(d.getUserJobPostId()).isNull();
        assertThat(d.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(d.getAppliedAt()).isEqualTo(e.appliedAt);
    }

    @Test
    @DisplayName("toEntity copies all fields from domain")
    void toEntity() {
        var d = Application.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .userJobPostId(UUID.randomUUID())
                .status(ApplicationStatus.INTERVIEWING)
                .appliedAt(OffsetDateTime.now())
                .build();

        ApplicationEntity e = mapper.toEntity(d);
        assertThat(e.id).isEqualTo(d.getId());
        assertThat(e.userId).isEqualTo(d.getUserId());
        assertThat(e.userJobPostId).isEqualTo(d.getUserJobPostId());
        assertThat(e.status).isEqualTo(ApplicationStatus.INTERVIEWING);
    }

    @Test
    @DisplayName("updateEntity touches only status and endedAt")
    void updateEntity() {
        var e = new ApplicationEntity();
        UUID origUserId = UUID.randomUUID();
        e.userId = origUserId;
        e.status = ApplicationStatus.APPLIED;
        e.endedAt = null;

        var d = Application.builder()
                .userId(UUID.randomUUID())
                .status(ApplicationStatus.REJECTED)
                .endedAt(OffsetDateTime.now())
                .build();
        mapper.updateEntity(e, d);

        assertThat(e.status).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(e.endedAt).isNotNull();
        assertThat(e.userId).isEqualTo(origUserId);
    }
}
