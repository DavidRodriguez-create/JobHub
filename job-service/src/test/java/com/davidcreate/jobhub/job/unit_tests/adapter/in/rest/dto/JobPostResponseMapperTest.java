package com.davidcreate.jobhub.job.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.adapter.in.rest.dto.JobPostResponseMapper;
import com.davidcreate.jobhub.job.contract.model.JobPostResponse;
import com.davidcreate.jobhub.job.contract.model.JobSearchPage;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobPostResponseMapper Unit Tests")
class JobPostResponseMapperTest {

    @Test
    @DisplayName("toResponse copies every contract field from the domain object")
    void copiesAllFields() {
        JobPost domain = JobPost.builder()
                .id(UUID.randomUUID())
                .title("Senior Developer")
                .url("https://example.com/jobs/1")
                .description("desc")
                .city("Madrid")
                .country("Spain")
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-10T10:00:00Z"))
                .compensationMin(70000)
                .compensationMax(90000)
                .employmentType(EmploymentType.FULL_TIME)
                .languages(List.of("English"))
                .requirements(List.of("Java"))
                .company(Company.builder().name("Stripe").logoUrl("https://example.com/logos/stripe.png").build())
                .source("greenhouse")
                .build();

        JobPostResponse response = JobPostResponseMapper.toResponse(domain);

        assertThat(response.getId()).isEqualTo(domain.getId());
        assertThat(response.getTitle()).isEqualTo("Senior Developer");
        assertThat(response.getUrl()).isEqualTo(URI.create("https://example.com/jobs/1"));
        assertThat(response.getDescription()).isEqualTo("desc");
        assertThat(response.getLocation()).isEqualTo("Madrid, Spain");
        assertThat(response.getCompensationMin()).isEqualTo(70000);
        assertThat(response.getCompensationMax()).isEqualTo(90000);
        assertThat(response.getEmploymentType()).isEqualTo(JobPostResponse.EmploymentTypeEnum.FULL_TIME);
        assertThat(response.getLanguage()).containsExactly("English");
        assertThat(response.getRequirements()).containsExactly("Java");
        assertThat(response.getCompany().getName()).isEqualTo("Stripe");
        assertThat(response.getCompany().getLogoUrl()).isEqualTo(URI.create("https://example.com/logos/stripe.png"));
        assertThat(response.getSource()).isEqualTo("greenhouse");
    }

    @Test
    @DisplayName("toPage wraps content with correct pagination metadata")
    void toPageWrapsResults() {
        JobPost job = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/x")
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .build();

        JobSearchPage page = JobPostResponseMapper.toPage(List.of(job), 1, 10, 25);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("toPage returns an empty content list and zero totals for no matches")
    void toPageEmpty() {
        JobSearchPage page = JobPostResponseMapper.toPage(List.of(), 0, 20, 0);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
    }
}
