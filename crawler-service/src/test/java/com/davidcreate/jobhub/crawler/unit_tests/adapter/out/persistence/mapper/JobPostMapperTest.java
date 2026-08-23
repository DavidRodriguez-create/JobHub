package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostLocationEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.mapper.JobPostMapper;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.JobPostLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
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
    @DisplayName("updateEntity refreshes content fields and lastSeenAt")
    void updateEntityRefreshesContentFieldsAndLastSeenAt() {
        JobPostEntity entity = new JobPostEntity();
        entity.title = "Old Title";
        entity.url = "https://example.com/old";
        entity.description = "Old description";
        entity.contentHash = "hash-abc";
        entity.firstSeenAt = NOW;
        entity.lastSeenAt = NOW;

        JobPost domain = JobPost.builder()
                .targetId(TARGET_ID)
                .title("New Title")
                .url("https://example.com/new")
                .description("New description")
                .contentHash("hash-abc")
                .lastSeenAt(NOW.plusDays(5))
                .build();

        mapper.updateEntity(entity, domain);

        assertThat(entity.title).isEqualTo("New Title");
        assertThat(entity.url).isEqualTo("https://example.com/new");
        assertThat(entity.description).isEqualTo("New description");
        assertThat(entity.lastSeenAt).isEqualTo(NOW.plusDays(5));
        // identity fields are untouched
        assertThat(entity.contentHash).isEqualTo("hash-abc");
        assertThat(entity.firstSeenAt).isEqualTo(NOW);
    }

    @Test
    @DisplayName("QAE-CRAWL-STORE-1B: toDomain orders the primary child row first regardless of "
            + "the entity collection's insertion order")
    void toDomainOrdersPrimaryLocationFirstRegardlessOfInsertionOrder() {
        JobPostEntity entity = new JobPostEntity();
        entity.id = ID;
        entity.targetId = TARGET_ID;
        entity.title = "Engineer";
        entity.url = "https://example.com/job/3";
        entity.city = "Barcelona";
        entity.country = "Spain";
        entity.firstSeenAt = NOW;
        entity.lastSeenAt = NOW;

        JobPostLocationEntity additional1 = new JobPostLocationEntity();
        additional1.jobPostId = ID;
        additional1.country = "Netherlands";
        additional1.city = "Amsterdam";
        additional1.isPrimary = false;
        additional1.position = 1;

        JobPostLocationEntity additional2 = new JobPostLocationEntity();
        additional2.jobPostId = ID;
        additional2.country = "France";
        additional2.city = "Paris";
        additional2.isPrimary = false;
        additional2.position = 2;

        JobPostLocationEntity primary = new JobPostLocationEntity();
        primary.jobPostId = ID;
        primary.country = "Spain";
        primary.city = "Barcelona";
        primary.isPrimary = true;
        primary.position = 0;

        // arbitrary insertion order: additional rows before the primary row
        List<JobPostLocationEntity> childRows = List.of(additional1, additional2, primary);

        JobPost domain = mapper.toDomain(entity, childRows);

        List<JobPostLocation> locations = domain.locations();
        assertThat(locations).hasSize(3);
        assertThat(locations.get(0).isPrimary()).isTrue();
        assertThat(locations.get(0).getCity()).isEqualTo("Barcelona");
        assertThat(locations.get(0).getCountry()).isEqualTo("Spain");
        assertThat(locations.stream().filter(JobPostLocation::isPrimary).count()).isEqualTo(1);
        assertThat(locations.get(1).isPrimary()).isFalse();
        assertThat(locations.get(2).isPrimary()).isFalse();
    }

    @Test
    @DisplayName("toLocationEntities builds one child row per opening, mirroring the primary")
    void toLocationEntitiesBuildsOneChildRowPerOpening() {
        JobPost domain = JobPost.builder()
                .id(ID)
                .targetId(TARGET_ID)
                .title("Engineer")
                .url("https://example.com/job/4")
                .city("Barcelona")
                .country("Spain")
                .additionalLocations(List.of(
                        JobPostLocation.builder().country("Netherlands").city("Amsterdam").build()))
                .build();

        List<JobPostLocationEntity> childRows = mapper.toLocationEntities(ID, domain);

        assertThat(childRows).hasSize(2);
        assertThat(childRows.get(0).jobPostId).isEqualTo(ID);
        assertThat(childRows.get(0).isPrimary).isTrue();
        assertThat(childRows.get(0).city).isEqualTo("Barcelona");
        assertThat(childRows.get(0).country).isEqualTo("Spain");
        assertThat(childRows.get(1).isPrimary).isFalse();
        assertThat(childRows.get(1).city).isEqualTo("Amsterdam");
        assertThat(childRows.get(1).country).isEqualTo("Netherlands");
    }
}
