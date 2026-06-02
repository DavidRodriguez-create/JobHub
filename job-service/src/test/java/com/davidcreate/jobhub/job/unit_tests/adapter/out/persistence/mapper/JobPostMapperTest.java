package com.davidcreate.jobhub.job.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.entity.PullTargetEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.JobPostMapper;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobPostMapper Unit Tests")
class JobPostMapperTest {

    private JobPostMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new JobPostMapper();
    }

    @Test
    @DisplayName("toDomain maps every field 1:1 from entity + target to domain")
    void mapsAllFields() {
        PullTargetEntity target = new PullTargetEntity();
        target.id = UUID.randomUUID();
        target.sourceType = "greenhouse";
        target.companyName = "Stripe";
        target.companyLogoUrl = "https://example.com/logos/stripe.png";

        JobPostEntity entity = new JobPostEntity();
        entity.id = UUID.randomUUID();
        entity.targetId = target.id;
        entity.target = target;
        entity.title = "Senior Java Developer";
        entity.url = "https://example.com/jobs/1";
        entity.description = "Backend role";
        entity.contentHash = "hash-1";
        entity.city = "Madrid";
        entity.country = "Spain";
        entity.compensationMin = 70000;
        entity.compensationMax = 90000;
        entity.employmentType = "full-time";
        entity.languages = List.of("English", "Spanish");
        entity.requirements = List.of("Java", "Spring");
        entity.firstSeenAt = OffsetDateTime.parse("2024-01-01T10:00:00Z");
        entity.lastSeenAt = OffsetDateTime.parse("2024-01-10T10:00:00Z");

        JobPost domain = mapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(entity.id);
        assertThat(domain.getTargetId()).isEqualTo(entity.targetId);
        assertThat(domain.getTitle()).isEqualTo(entity.title);
        assertThat(domain.getUrl()).isEqualTo(entity.url);
        assertThat(domain.getDescription()).isEqualTo(entity.description);
        assertThat(domain.getContentHash()).isEqualTo(entity.contentHash);
        assertThat(domain.getCity()).isEqualTo(entity.city);
        assertThat(domain.getCountry()).isEqualTo(entity.country);
        assertThat(domain.getCompensationMin()).isEqualTo(70000);
        assertThat(domain.getCompensationMax()).isEqualTo(90000);
        assertThat(domain.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(domain.getLanguages()).containsExactly("English", "Spanish");
        assertThat(domain.getRequirements()).containsExactly("Java", "Spring");
        assertThat(domain.getFirstSeenAt()).isEqualTo(entity.firstSeenAt);
        assertThat(domain.getLastSeenAt()).isEqualTo(entity.lastSeenAt);
        assertThat(domain.getCompany().getName()).isEqualTo("Stripe");
        assertThat(domain.getCompany().getLogoUrl()).isEqualTo("https://example.com/logos/stripe.png");
        assertThat(domain.getSource()).isEqualTo("greenhouse");
    }

    @Test
    @DisplayName("toDomain preserves nullable fields as null when target and extras are missing")
    void preservesNullableFieldsAsNull() {
        JobPostEntity entity = new JobPostEntity();
        entity.id = UUID.randomUUID();
        entity.targetId = UUID.randomUUID();
        entity.title = "Title";
        entity.url = "https://example.com/jobs/x";
        entity.firstSeenAt = OffsetDateTime.parse("2024-01-01T10:00:00Z");
        entity.lastSeenAt = OffsetDateTime.parse("2024-01-10T10:00:00Z");

        JobPost domain = mapper.toDomain(entity);

        assertThat(domain.getDescription()).isNull();
        assertThat(domain.getContentHash()).isNull();
        assertThat(domain.getCity()).isNull();
        assertThat(domain.getCountry()).isNull();
        assertThat(domain.getCompensationMin()).isNull();
        assertThat(domain.getEmploymentType()).isNull();
        assertThat(domain.getCompany()).isNull();
        assertThat(domain.getSource()).isNull();
    }

    @Test
    @DisplayName("location() combines city + country, or returns the populated one when only one is set")
    void locationCombination() {
        JobPost both = JobPost.builder().city("Madrid").country("Spain").build();
        JobPost cityOnly = JobPost.builder().city("Barcelona").build();
        JobPost countryOnly = JobPost.builder().country("Germany").build();

        assertThat(both.location()).isEqualTo("Madrid, Spain");
        assertThat(cityOnly.location()).isEqualTo("Barcelona");
        assertThat(countryOnly.location()).isEqualTo("Germany");
    }
}
