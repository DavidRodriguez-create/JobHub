package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.mapper.JobPostMapper;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobPostMapper Unit Tests")
class JobPostMapperTest {

    JobPostMapper mapper;

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2024-01-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        mapper = new JobPostMapper();
    }

    @Test
    @DisplayName("toDomain maps all fields from entity")
    void toDomainMapsAllFields() {
        JobPostEntity entity = new JobPostEntity();
        entity.id = ID;
        entity.targetId = TARGET_ID;
        entity.title = "Java Engineer";
        entity.url = "https://example.com/job/1";
        entity.description = "Backend role";
        entity.contentHash = "hash-abc";
        entity.city = "Madrid";
        entity.country = "Spain";
        entity.firstSeenAt = NOW;
        entity.lastSeenAt = NOW.plusDays(1);

        JobPost domain = mapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(ID);
        assertThat(domain.getTargetId()).isEqualTo(TARGET_ID);
        assertThat(domain.getTitle()).isEqualTo("Java Engineer");
        assertThat(domain.getUrl()).isEqualTo("https://example.com/job/1");
        assertThat(domain.getDescription()).isEqualTo("Backend role");
        assertThat(domain.getContentHash()).isEqualTo("hash-abc");
        assertThat(domain.getCity()).isEqualTo("Madrid");
        assertThat(domain.getCountry()).isEqualTo("Spain");
        assertThat(domain.getFirstSeenAt()).isEqualTo(NOW);
        assertThat(domain.getLastSeenAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    @DisplayName("toDomain preserves null optional fields")
    void toDomainPreservesNulls() {
        JobPostEntity entity = new JobPostEntity();
        entity.id = ID;
        entity.targetId = TARGET_ID;
        entity.title = "Engineer";
        entity.url = "https://example.com/job/2";
        entity.description = null;
        entity.city = null;
        entity.country = null;
        entity.firstSeenAt = NOW;
        entity.lastSeenAt = NOW;

        JobPost domain = mapper.toDomain(entity);

        assertThat(domain.getDescription()).isNull();
        assertThat(domain.getCity()).isNull();
        assertThat(domain.getCountry()).isNull();
    }

    @Test
    @DisplayName("toEntity maps all fields from domain")
    void toEntityMapsAllFields() {
        JobPost domain = JobPost.builder()
                .id(ID)
                .targetId(TARGET_ID)
                .title("Java Engineer")
                .url("https://example.com/job/1")
                .description("Backend role")
                .contentHash("hash-abc")
                .city("Madrid")
                .country("Spain")
                .firstSeenAt(NOW)
                .lastSeenAt(NOW.plusDays(1))
                .build();

        JobPostEntity entity = mapper.toEntity(domain);

        assertThat(entity.targetId).isEqualTo(TARGET_ID);
        assertThat(entity.title).isEqualTo("Java Engineer");
        assertThat(entity.url).isEqualTo("https://example.com/job/1");
        assertThat(entity.description).isEqualTo("Backend role");
        assertThat(entity.contentHash).isEqualTo("hash-abc");
        assertThat(entity.city).isEqualTo("Madrid");
        assertThat(entity.country).isEqualTo("Spain");
        assertThat(entity.firstSeenAt).isEqualTo(NOW);
        assertThat(entity.lastSeenAt).isEqualTo(NOW.plusDays(1));
    }

    @Test
    @DisplayName("updateEntity only updates lastSeenAt")
    void updateEntityOnlyUpdatesLastSeenAt() {
        JobPostEntity entity = new JobPostEntity();
        entity.title = "Old Title";
        entity.lastSeenAt = NOW;

        JobPost domain = JobPost.builder()
                .targetId(TARGET_ID)
                .title("New Title")
                .url("https://example.com")
                .lastSeenAt(NOW.plusDays(5))
                .build();

        mapper.updateEntity(entity, domain);

        assertThat(entity.lastSeenAt).isEqualTo(NOW.plusDays(5));
        assertThat(entity.title).isEqualTo("Old Title");
    }
}
