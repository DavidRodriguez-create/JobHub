package com.davidcreate.jobhub.application.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.application.adapter.out.persistence.entity.JobPostSnapshotEntity;
import com.davidcreate.jobhub.application.adapter.out.persistence.mapper.JobPostSnapshotMapper;
import com.davidcreate.jobhub.application.domain.entity.JobPostSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobPostSnapshotMapper Unit Tests")
class JobPostSnapshotMapperTest {

    private final JobPostSnapshotMapper mapper = new JobPostSnapshotMapper();

    @Test
    @DisplayName("round-trips all fields")
    void roundTrip() {
        var e = new JobPostSnapshotEntity();
        e.id = UUID.randomUUID();
        e.jobPostId = UUID.randomUUID();
        e.contentHash = "deadbeef";
        e.title = "Dev";
        e.company = "Acme";
        e.url = "https://x";
        e.location = "Madrid, Spain";
        e.snapshottedAt = OffsetDateTime.now();

        JobPostSnapshot d = mapper.toDomain(e);
        assertThat(d.getId()).isEqualTo(e.id);
        assertThat(d.getJobPostId()).isEqualTo(e.jobPostId);
        assertThat(d.getContentHash()).isEqualTo("deadbeef");

        JobPostSnapshotEntity back = mapper.toEntity(d);
        assertThat(back.contentHash).isEqualTo("deadbeef");
        assertThat(back.company).isEqualTo("Acme");
        assertThat(back.location).isEqualTo("Madrid, Spain");
        assertThat(back.snapshottedAt).isEqualTo(e.snapshottedAt);
    }
}
