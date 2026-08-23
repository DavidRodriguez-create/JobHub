package com.davidcreate.jobhub.job.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.adapter.in.rest.dto.JobPostResponseMapper;
import com.davidcreate.jobhub.job.contract.model.JobLocation;
import com.davidcreate.jobhub.job.contract.model.JobPostResponse;
import com.davidcreate.jobhub.job.contract.model.JobPostSummary;
import com.davidcreate.jobhub.job.contract.model.JobSearchPage;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobCount;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobPostResponseMapper Unit Tests")
class JobPostResponseMapperTest {

    // ── QAE-428-DTO-01: detail projection maps all twelve CompanyInfo properties ────

    @Test
    @DisplayName("QAE-428-DTO-01: toResponse maps every one of the twelve CompanyInfo properties "
            + "from a fully-populated domain Company, description included (detail endpoint)")
    void toResponseMapsEveryCompanyInfoField() {
        UUID companyId = UUID.randomUUID();
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-06-01T00:00:00Z");
        Company company = Company.builder()
                .id(companyId)
                .slug("stripe")
                .name("Stripe")
                .website("https://stripe.com")
                .industry("Fintech")
                .size("5001-10000")
                .headquarters("San Francisco, United States")
                .description("Financial infrastructure for the internet.")
                .logoUrl("https://example.com/logos/stripe.png")
                .tags(List.of("remote-first", "b2b"))
                .manuallyEdited(false)
                .updatedAt(updatedAt)
                .build();
        JobPost domain = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/jobs/1")
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .company(company)
                .build();

        var info = JobPostResponseMapper.toResponse(domain).getCompany();

        assertThat(info.getId()).isEqualTo(companyId);
        assertThat(info.getSlug()).isEqualTo("stripe");
        assertThat(info.getName()).isEqualTo("Stripe");
        assertThat(info.getLogoUrl()).isEqualTo(URI.create("https://example.com/logos/stripe.png"));
        assertThat(info.getWebsite()).isEqualTo(URI.create("https://stripe.com"));
        assertThat(info.getIndustry()).isEqualTo("Fintech");
        assertThat(info.getSize()).isEqualTo("5001-10000");
        assertThat(info.getHeadquarters()).isEqualTo("San Francisco, United States");
        assertThat(info.getDescription()).isEqualTo("Financial infrastructure for the internet.");
        assertThat(info.getTags()).containsExactly("remote-first", "b2b");
        assertThat(info.getManuallyEdited()).isFalse();
        assertThat(info.getUpdatedAt()).isEqualTo(updatedAt);
    }

    // ── QAE-428-DTO-03: blank-string guard ───────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-DTO-03: blank industry/headquarters/website come out null, never blank/empty")
    void blankCompanyStringsComeOutNull() {
        Company company = Company.builder()
                .name("Acme")
                .industry(" ")
                .headquarters("")
                .website(" ")
                .build();
        JobPost domain = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/jobs/1")
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .company(company)
                .build();

        var info = JobPostResponseMapper.toResponse(domain).getCompany();

        assertThat(info.getIndustry()).isNull();
        assertThat(info.getHeadquarters()).isNull();
        assertThat(info.getWebsite()).isNull();
    }

    // ── QAE-428-DTO-04: tags null stays null ─────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-DTO-04: Company.tags == null -> CompanyInfo.tags is null")
    void nullTagsStayNull() {
        Company company = Company.builder().name("Acme").tags(null).build();
        JobPost domain = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/jobs/1")
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .company(company)
                .build();

        assertThat(JobPostResponseMapper.toResponse(domain).getCompany().getTags()).isNull();
    }

    // ── QAE-428-DTO-05: fallback shape at the DTO level ──────────────────────────────

    @Test
    @DisplayName("QAE-428-DTO-05: fallback Company (name+logoUrl only) -> CompanyInfo has "
            + "every other field null, including manuallyEdited")
    void fallbackCompanyShapeAtDtoLevel() {
        Company company = Company.builder()
                .name("Northwind Freight")
                .logoUrl(null)
                .build();
        JobPost domain = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/jobs/1")
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .company(company)
                .build();

        var info = JobPostResponseMapper.toResponse(domain).getCompany();

        assertThat(info.getName()).isEqualTo("Northwind Freight");
        assertThat(info.getLogoUrl()).isNull();
        assertThat(info.getId()).isNull();
        assertThat(info.getSlug()).isNull();
        assertThat(info.getWebsite()).isNull();
        assertThat(info.getIndustry()).isNull();
        assertThat(info.getSize()).isNull();
        assertThat(info.getHeadquarters()).isNull();
        assertThat(info.getDescription()).isNull();
        assertThat(info.getTags()).isNull();
        assertThat(info.getManuallyEdited()).isNull();
        assertThat(info.getUpdatedAt()).isNull();
    }

    // ── QAE-428-DTO-06: job.getCompany() == null stays null (re-run-unmodified guard) ──

    @Test
    @DisplayName("QAE-428-DTO-06: no company at all -> response.getCompany() stays null")
    void noCompanyStaysNull() {
        JobPost domain = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/jobs/1")
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .build();

        assertThat(JobPostResponseMapper.toResponse(domain).getCompany()).isNull();
    }

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
    @DisplayName("TC-11: toPage wraps summary content with correct pagination metadata")
    void toPageWrapsResults() {
        JobPost job = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/x")
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .build();

        JobSearchPage page = JobPostResponseMapper.toPage(List.of(job), 1, 10, new JobCount(25, false));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0)).isInstanceOf(JobPostSummary.class);
        assertThat(page.getContent().get(0).getId()).isEqualTo(job.getId());
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getCountIsEstimate()).isFalse();
    }

    @Test
    @DisplayName("TC-11: toPage returns an empty summary content list and zero totals for no matches")
    void toPageEmpty() {
        JobSearchPage page = JobPostResponseMapper.toPage(List.of(), 0, 20, new JobCount(0, false));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
        assertThat(page.getCountIsEstimate()).isFalse();
    }

    @Test
    @DisplayName("Story #331 (ADR 0018): toPage surfaces countIsEstimate=true when the count is a planner estimate")
    void toPageSurfacesEstimateFlag() {
        JobSearchPage page = JobPostResponseMapper.toPage(List.of(), 0, 20, new JobCount(4500, true));

        assertThat(page.getTotalElements()).isEqualTo(4500);
        assertThat(page.getCountIsEstimate()).isTrue();
    }

    // ── QAE-JOB-RETURN-2: single-opening post — locations[0] equals location ──────────

    @Test
    @DisplayName("QAE-JOB-RETURN-2: single-opening post composes locations[0] equal to location")
    void singleOpeningLocationsMatchesLocationString() {
        com.davidcreate.jobhub.job.domain.model.JobLocation opening =
                com.davidcreate.jobhub.job.domain.model.JobLocation.builder()
                        .country("Spain").city("Barcelona").primary(true).build();
        JobPost domain = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/jobs/1")
                .city("Barcelona")
                .country("Spain")
                .locations(List.of(opening))
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .build();

        JobPostResponse response = JobPostResponseMapper.toResponse(domain);

        assertThat(response.getLocation()).isEqualTo("Barcelona, Spain");
        assertThat(response.getLocations()).hasSize(1);
        JobLocation entry = response.getLocations().get(0);
        assertThat(entry.getPrimary()).isTrue();
        assertThat(entry.getCity()).isEqualTo("Barcelona");
        assertThat(entry.getCountry()).isEqualTo("Spain");
    }

    // ── QAE-JOB-RETURN-3: no-location post — location absent, locations empty ─────────

    @Test
    @DisplayName("QAE-JOB-RETURN-3: no-location post has null location and empty locations, not a blank entry")
    void noLocationDataYieldsAbsentLocationAndEmptyLocations() {
        JobPost domain = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/jobs/1")
                .locations(Collections.emptyList())
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .build();

        JobPostResponse response = JobPostResponseMapper.toResponse(domain);

        assertThat(response.getLocation()).isNull();
        assertThat(response.getLocations()).isEmpty();
    }

    // ── QAE-JOB-RETURN-4: Remote-only post ─────────────────────────────────────────────

    @Test
    @DisplayName("QAE-JOB-RETURN-4: Remote-only post maps location and locations[0] to Remote")
    void remoteOnlyPostMapsToRemoteEntry() {
        com.davidcreate.jobhub.job.domain.model.JobLocation remote =
                com.davidcreate.jobhub.job.domain.model.JobLocation.builder()
                        .country("Remote").primary(true).build();
        JobPost domain = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/jobs/1")
                .country("Remote")
                .locations(List.of(remote))
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .build();

        JobPostResponse response = JobPostResponseMapper.toResponse(domain);

        assertThat(response.getLocation()).isEqualTo("Remote");
        assertThat(response.getLocations()).hasSize(1);
        assertThat(response.getLocations().get(0).getPrimary()).isTrue();
        assertThat(response.getLocations().get(0).getCountry()).isEqualTo("Remote");
    }

    // ── QAE-JOB-RETURN-1B: exactly one primary=true regardless of opening count ───────

    @Test
    @DisplayName("QAE-JOB-RETURN-1B: exactly one primary and N+1 entries for 0, 1, 3 additional openings")
    void exactlyOnePrimaryRegardlessOfOpeningCount() {
        assertPrimaryCountAndSize(0, 1);
        assertPrimaryCountAndSize(1, 2);
        assertPrimaryCountAndSize(3, 4);
    }

    private void assertPrimaryCountAndSize(int additionalCount, int expectedSize) {
        List<com.davidcreate.jobhub.job.domain.model.JobLocation> openings = new java.util.ArrayList<>();
        openings.add(com.davidcreate.jobhub.job.domain.model.JobLocation.builder()
                .country("Spain").city("Barcelona").primary(true).build());
        for (int i = 0; i < additionalCount; i++) {
            openings.add(com.davidcreate.jobhub.job.domain.model.JobLocation.builder()
                    .country("Country" + i).city("City" + i).primary(false).build());
        }

        JobPost domain = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/jobs/1")
                .locations(openings)
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .build();

        JobPostResponse response = JobPostResponseMapper.toResponse(domain);

        assertThat(response.getLocations()).hasSize(expectedSize);
        long primaryCount = response.getLocations().stream()
                .filter(JobLocation::getPrimary).count();
        assertThat(primaryCount).isEqualTo(1);
    }
}
