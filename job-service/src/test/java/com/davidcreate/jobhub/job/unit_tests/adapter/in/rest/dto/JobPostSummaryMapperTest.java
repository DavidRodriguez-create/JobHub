package com.davidcreate.jobhub.job.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.adapter.in.rest.dto.JobPostSummaryMapper;
import com.davidcreate.jobhub.job.contract.model.JobLocation;
import com.davidcreate.jobhub.job.contract.model.JobPostSummary;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobPostSummaryMapper Unit Tests")
class JobPostSummaryMapperTest {

    // ── QAE-428-DTO-02: list projection maps the same 11 fields, description ALWAYS null ──

    @Test
    @DisplayName("QAE-428-DTO-02: toSummary maps the same eleven CompanyInfo fields as the "
            + "detail mapper EXCEPT description, which stays null even when the domain "
            + "Company carries a non-null description (projection rule)")
    void toSummaryMapsElevenFieldsAndAlwaysNullsDescription() {
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

        var info = JobPostSummaryMapper.toSummary(domain).getCompany();

        assertThat(info.getId()).isEqualTo(companyId);
        assertThat(info.getSlug()).isEqualTo("stripe");
        assertThat(info.getName()).isEqualTo("Stripe");
        assertThat(info.getLogoUrl()).isEqualTo(URI.create("https://example.com/logos/stripe.png"));
        assertThat(info.getWebsite()).isEqualTo(URI.create("https://stripe.com"));
        assertThat(info.getIndustry()).isEqualTo("Fintech");
        assertThat(info.getSize()).isEqualTo("5001-10000");
        assertThat(info.getHeadquarters()).isEqualTo("San Francisco, United States");
        assertThat(info.getTags()).containsExactly("remote-first", "b2b");
        assertThat(info.getManuallyEdited()).isFalse();
        assertThat(info.getUpdatedAt()).isEqualTo(updatedAt);

        assertThat(info.getDescription())
                .as("JobPostSummary must never carry a populated company.description")
                .isNull();
    }

    // ── QAE-428-DTO-03: blank-string guard, summary mapper too ──────────────────────

    @Test
    @DisplayName("QAE-428-DTO-03: blank industry/headquarters/website come out null on the summary too")
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

        var info = JobPostSummaryMapper.toSummary(domain).getCompany();

        assertThat(info.getIndustry()).isNull();
        assertThat(info.getHeadquarters()).isNull();
        assertThat(info.getWebsite()).isNull();
    }

    // ── QAE-428-DTO-05: fallback shape at the DTO level, summary mapper too ─────────

    @Test
    @DisplayName("QAE-428-DTO-05: fallback Company on the summary mapper - every other field null")
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

        var info = JobPostSummaryMapper.toSummary(domain).getCompany();

        assertThat(info.getName()).isEqualTo("Northwind Freight");
        assertThat(info.getLogoUrl()).isNull();
        assertThat(info.getId()).isNull();
        assertThat(info.getSlug()).isNull();
        assertThat(info.getManuallyEdited()).isNull();
        assertThat(info.getUpdatedAt()).isNull();
    }

    // ── QAE-428-DTO-06: no company at all - re-run-unmodified guard, summary mapper ─

    @Test
    @DisplayName("QAE-428-DTO-06: no company at all -> summary.getCompany() stays null")
    void noCompanyStaysNull() {
        JobPost domain = JobPost.builder()
                .id(UUID.randomUUID())
                .title("t")
                .url("https://example.com/jobs/1")
                .firstSeenAt(OffsetDateTime.parse("2024-01-01T10:00:00Z"))
                .lastSeenAt(OffsetDateTime.parse("2024-01-02T10:00:00Z"))
                .build();

        assertThat(JobPostSummaryMapper.toSummary(domain).getCompany()).isNull();
    }

    // ── TC-9: toSummary copies every card field from the domain object ────────

    @Test
    @DisplayName("TC-9: toSummary copies every card field from the domain object")
    void toSummaryCopiesAllCardFields() {
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

        JobPostSummary summary = JobPostSummaryMapper.toSummary(domain);

        assertThat(summary.getId()).isEqualTo(domain.getId());
        assertThat(summary.getTitle()).isEqualTo("Senior Developer");
        assertThat(summary.getUrl()).isEqualTo(URI.create("https://example.com/jobs/1"));
        assertThat(summary.getLocation()).isEqualTo("Madrid, Spain");
        assertThat(summary.getCompensationMin()).isEqualTo(70000);
        assertThat(summary.getCompensationMax()).isEqualTo(90000);
        assertThat(summary.getEmploymentType()).isEqualTo(JobPostSummary.EmploymentTypeEnum.FULL_TIME);
        assertThat(summary.getLanguage()).containsExactly("English");
        assertThat(summary.getCompany().getName()).isEqualTo("Stripe");
        assertThat(summary.getCompany().getLogoUrl()).isEqualTo(URI.create("https://example.com/logos/stripe.png"));
        assertThat(summary.getSource()).isEqualTo("greenhouse");
        assertThat(summary.getFirstSeenAt()).isEqualTo(domain.getFirstSeenAt());
        assertThat(summary.getLastSeenAt()).isEqualTo(domain.getLastSeenAt());
    }

    // ── TC-10: JobPostSummary carries neither description nor requirements ────

    @Test
    @DisplayName("TC-10: JobPostSummary model has no description/requirements accessor at all")
    void summaryModelHasNoHeavyFieldAccessors() {
        boolean hasGetDescription = hasMethod(JobPostSummary.class, "getDescription");
        boolean hasGetRequirements = hasMethod(JobPostSummary.class, "getRequirements");

        assertThat(hasGetDescription)
                .as("JobPostSummary must not expose getDescription()")
                .isFalse();
        assertThat(hasGetRequirements)
                .as("JobPostSummary must not expose getRequirements()")
                .isFalse();
    }

    private static boolean hasMethod(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    // ── TC-12: locations composition preserved on the summary path ────────────

    @Test
    @DisplayName("TC-12: exactly one primary and N+1 entries for 0, 1, 3 additional openings")
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

        JobPostSummary summary = JobPostSummaryMapper.toSummary(domain);

        assertThat(summary.getLocations()).hasSize(expectedSize);
        long primaryCount = summary.getLocations().stream()
                .filter(JobLocation::getPrimary).count();
        assertThat(primaryCount).isEqualTo(1);
        assertThat(summary.getLocations().get(0).getPrimary()).isTrue();
    }
}
