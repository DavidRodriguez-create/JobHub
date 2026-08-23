package com.davidcreate.jobhub.job.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.CompanyEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.entity.JobPostLocationEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.entity.PullTargetEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.CompanyMapper;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.JobPostMapper;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobLocation;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobPostMapper Unit Tests")
class JobPostMapperTest {

    private JobPostMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new JobPostMapper(new CompanyMapper());
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

    // ── QAE-428-JPMAP-01: resolved company path, decoy companyName/companyLogoUrl ignored ──

    @Test
    @DisplayName("QAE-428-JPMAP-01: target.company != null -> mapped entirely from CompanyMapper, "
            + "decoy target.companyName/companyLogoUrl completely ignored")
    void resolvedCompanyIgnoresDecoyTargetFields() {
        CompanyEntity companyEntity = new CompanyEntity();
        companyEntity.id = UUID.randomUUID();
        companyEntity.slug = "stripe";
        companyEntity.name = "Stripe";
        companyEntity.website = "https://stripe.com";
        companyEntity.industry = "Fintech";
        companyEntity.size = "5001-10000";
        companyEntity.headquarters = "San Francisco, United States";
        companyEntity.description = "Financial infrastructure for the internet.";
        companyEntity.logoUrl = "https://example.com/logos/stripe-real.png";
        companyEntity.tags = null;
        companyEntity.manuallyEdited = false;
        companyEntity.updatedAt = OffsetDateTime.parse("2026-06-01T00:00:00Z");

        PullTargetEntity target = new PullTargetEntity();
        target.id = UUID.randomUUID();
        target.sourceType = "greenhouse";
        target.companyName = "DECOY Name";
        target.companyLogoUrl = "https://example.com/logos/DECOY.png";
        target.company = companyEntity;

        JobPostEntity entity = new JobPostEntity();
        entity.id = UUID.randomUUID();
        entity.targetId = target.id;
        entity.target = target;
        entity.title = "t";
        entity.url = "https://example.com/jobs/x";
        entity.firstSeenAt = OffsetDateTime.parse("2024-01-01T10:00:00Z");
        entity.lastSeenAt = OffsetDateTime.parse("2024-01-10T10:00:00Z");

        JobPost domain = mapper.toDomain(entity);

        assertThat(domain.getCompany().getId()).isEqualTo(companyEntity.id);
        assertThat(domain.getCompany().getSlug()).isEqualTo("stripe");
        assertThat(domain.getCompany().getName()).isEqualTo("Stripe");
        assertThat(domain.getCompany().getWebsite()).isEqualTo("https://stripe.com");
        assertThat(domain.getCompany().getIndustry()).isEqualTo("Fintech");
        assertThat(domain.getCompany().getSize()).isEqualTo("5001-10000");
        assertThat(domain.getCompany().getHeadquarters()).isEqualTo("San Francisco, United States");
        assertThat(domain.getCompany().getDescription())
                .isEqualTo("Financial infrastructure for the internet.");
        assertThat(domain.getCompany().getLogoUrl()).isEqualTo("https://example.com/logos/stripe-real.png");
        assertThat(domain.getCompany().getManuallyEdited()).isFalse();
        assertThat(domain.getCompany().getUpdatedAt()).isEqualTo(companyEntity.updatedAt);

        // Decoy values must never leak through once the company is resolved.
        assertThat(domain.getCompany().getName()).isNotEqualTo("DECOY Name");
        assertThat(domain.getCompany().getLogoUrl()).isNotEqualTo("https://example.com/logos/DECOY.png");
    }

    // ── QAE-428-JPMAP-02: fallback path, unresolved target ──────────────────────────

    @Test
    @DisplayName("QAE-428-JPMAP-02: target.company == null -> fallback carries only name/logoUrl, "
            + "every other field null")
    void fallbackCompanyCarriesOnlyNameAndLogoUrl() {
        PullTargetEntity target = new PullTargetEntity();
        target.id = UUID.randomUUID();
        target.sourceType = "workday";
        target.companyName = "Northwind Freight";
        target.companyLogoUrl = null;
        target.company = null;

        JobPostEntity entity = new JobPostEntity();
        entity.id = UUID.randomUUID();
        entity.targetId = target.id;
        entity.target = target;
        entity.title = "t";
        entity.url = "https://example.com/jobs/x";
        entity.firstSeenAt = OffsetDateTime.parse("2024-01-01T10:00:00Z");
        entity.lastSeenAt = OffsetDateTime.parse("2024-01-10T10:00:00Z");

        JobPost domain = mapper.toDomain(entity);

        assertThat(domain.getCompany().getName()).isEqualTo("Northwind Freight");
        assertThat(domain.getCompany().getLogoUrl()).isNull();
        assertThat(domain.getCompany().getId()).isNull();
        assertThat(domain.getCompany().getSlug()).isNull();
        assertThat(domain.getCompany().getWebsite()).isNull();
        assertThat(domain.getCompany().getIndustry()).isNull();
        assertThat(domain.getCompany().getSize()).isNull();
        assertThat(domain.getCompany().getHeadquarters()).isNull();
        assertThat(domain.getCompany().getDescription()).isNull();
        assertThat(domain.getCompany().getTags()).isNull();
        assertThat(domain.getCompany().getManuallyEdited()).isNull();
        assertThat(domain.getCompany().getUpdatedAt()).isNull();
    }

    // ── QAE-428-JPMAP-03: target == null entirely - re-run-unmodified, see below ────

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

    // ── QAE-JOB-RETURN-1: mapper composes locations[] primary-first from unordered rows ──

    @Test
    @DisplayName("QAE-JOB-RETURN-1: toDomain composes locations primary-first from an unordered child collection")
    void mapsLocationsPrimaryFirstFromUnorderedChildRows() {
        JobPostEntity entity = new JobPostEntity();
        entity.id = UUID.randomUUID();
        entity.title = "Multi-Location Backend Engineer";
        entity.url = "https://example.com/jobs/multi";
        entity.firstSeenAt = OffsetDateTime.parse("2024-01-01T10:00:00Z");
        entity.lastSeenAt = OffsetDateTime.parse("2024-01-10T10:00:00Z");

        // Arbitrary insertion order: additional rows appear BEFORE the primary row.
        JobPostLocationEntity netherlands = childRow("Netherlands", "Amsterdam", false, (short) 1);
        JobPostLocationEntity france = childRow("France", "Paris", false, (short) 2);
        JobPostLocationEntity spainPrimary = childRow("Spain", "Barcelona", true, (short) 0);
        entity.locations = new ArrayList<>(List.of(netherlands, france, spainPrimary));

        JobPost domain = mapper.toDomain(entity);

        assertThat(domain.getLocations()).hasSize(3);
        assertThat(domain.getLocations().get(0).isPrimary()).isTrue();
        assertThat(domain.getLocations().get(0).getCountry()).isEqualTo("Spain");
        assertThat(domain.getLocations().get(0).getCity()).isEqualTo("Barcelona");
        assertThat(domain.getLocations().get(1).isPrimary()).isFalse();
        assertThat(domain.getLocations().get(1).getCountry()).isEqualTo("Netherlands");
        assertThat(domain.getLocations().get(2).isPrimary()).isFalse();
        assertThat(domain.getLocations().get(2).getCountry()).isEqualTo("France");
    }

    @Test
    @DisplayName("QAE-CRAWL-STORE-1B analogue: exactly one primary regardless of collection order")
    void exactlyOnePrimaryRegardlessOfOrder() {
        JobPostEntity entity = new JobPostEntity();
        entity.id = UUID.randomUUID();
        entity.title = "t";
        entity.url = "https://example.com/jobs/x";
        entity.firstSeenAt = OffsetDateTime.parse("2024-01-01T10:00:00Z");
        entity.lastSeenAt = OffsetDateTime.parse("2024-01-10T10:00:00Z");
        entity.locations = new ArrayList<>(List.of(
                childRow("Germany", "Berlin", false, (short) 1),
                childRow("Spain", "Madrid", true, (short) 0)
        ));

        JobPost domain = mapper.toDomain(entity);

        long primaryCount = domain.getLocations().stream().filter(JobLocation::isPrimary).count();
        assertThat(primaryCount).isEqualTo(1);
    }

    @Test
    @DisplayName("toDomain returns an empty locations list when the entity has no child rows")
    void emptyChildRowsMapToEmptyLocations() {
        JobPostEntity entity = new JobPostEntity();
        entity.id = UUID.randomUUID();
        entity.title = "t";
        entity.url = "https://example.com/jobs/x";
        entity.firstSeenAt = OffsetDateTime.parse("2024-01-01T10:00:00Z");
        entity.lastSeenAt = OffsetDateTime.parse("2024-01-10T10:00:00Z");
        entity.locations = new ArrayList<>();

        JobPost domain = mapper.toDomain(entity);

        assertThat(domain.getLocations()).isEmpty();
    }

    private static JobPostLocationEntity childRow(String country, String city, boolean primary, short position) {
        JobPostLocationEntity row = new JobPostLocationEntity();
        row.country = country;
        row.city = city;
        row.isPrimary = primary;
        row.position = position;
        return row;
    }
}
