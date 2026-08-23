package com.davidcreate.jobhub.application.component_tests;

import com.davidcreate.jobhub.application.application.port.out.JobPostGateway;
import com.davidcreate.jobhub.application.component_tests.support.WireMockJobServiceResource;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component tests for {@code JobPostGatewayAdapter.findById}, story #244 (ADR 0015 §3:
 * "widen JobPostView/JobPostRemoteResponse/JobPostGatewayAdapter"). Application-service's
 * first outbound-HTTP test class - WireMock stands in for job-service's {@code GET /jobs/{id}}
 * per CLAUDE.md's "WireMock only where the service has outbound HTTP" rule.
 *
 * <p>AS244-CW-04 deliberately asserts the adapter passes an empty-string {@code logoUrl}
 * through unmodified - normalisation to null happens one layer up, in
 * {@code ApplicationService.resolveSnapshot} (see AS244-U-04/05 and the no-backfill tests),
 * not inside this adapter.
 */
@QuarkusTest
@QuarkusTestResource(WireMockJobServiceResource.class)
@DisplayName("JobPostGatewayAdapter Component Tests (WireMock)")
class JobPostGatewayAdapterComponentTest {

    @Inject
    JobPostGateway jobPostGateway;

    @AfterEach
    void resetStubs() {
        jobService().resetAll();
    }

    @Test
    @DisplayName("AS244-CW-01: a stub with company.name + company.logoUrl populated maps onto "
            + "JobPostView.companyName/companyLogoUrl")
    void mapsPopulatedCompanyAndLogo() {
        UUID jobPostId = UUID.randomUUID();
        stubJob(jobPostId, """
                {
                  "id": "%s",
                  "title": "Backend Developer",
                  "url": "https://job.example/%s",
                  "description": "desc",
                  "location": "Madrid, Spain",
                  "company": { "name": "Acme Corp", "logoUrl": "https://cdn.example/acme.png" }
                }
                """.formatted(jobPostId, jobPostId));

        Optional<JobPostGateway.JobPostView> result = jobPostGateway.findById(jobPostId);

        assertThat(result).isPresent();
        assertThat(result.get().companyName()).isEqualTo("Acme Corp");
        assertThat(result.get().companyLogoUrl()).isEqualTo("https://cdn.example/acme.png");
    }

    @Test
    @DisplayName("AS244-CW-02: a stub with company present but logoUrl omitted maps companyName populated, "
            + "companyLogoUrl null, no exception")
    void mapsMissingLogoUrlAsNull() {
        UUID jobPostId = UUID.randomUUID();
        stubJob(jobPostId, """
                {
                  "id": "%s",
                  "title": "Backend Developer",
                  "url": "https://job.example/%s",
                  "description": "desc",
                  "location": "Madrid, Spain",
                  "company": { "name": "Acme Corp" }
                }
                """.formatted(jobPostId, jobPostId));

        Optional<JobPostGateway.JobPostView> result = jobPostGateway.findById(jobPostId);

        assertThat(result).isPresent();
        assertThat(result.get().companyName()).isEqualTo("Acme Corp");
        assertThat(result.get().companyLogoUrl()).isNull();
    }

    @Test
    @DisplayName("AS244-CW-03: a stub whose company object is entirely absent maps both companyName and "
            + "companyLogoUrl to null, no NullPointerException on the nested-object access")
    void mapsAbsentCompanyAsNull() {
        UUID jobPostId = UUID.randomUUID();
        stubJob(jobPostId, """
                {
                  "id": "%s",
                  "title": "Backend Developer",
                  "url": "https://job.example/%s",
                  "description": "desc",
                  "location": "Madrid, Spain"
                }
                """.formatted(jobPostId, jobPostId));

        Optional<JobPostGateway.JobPostView> result = jobPostGateway.findById(jobPostId);

        assertThat(result).isPresent();
        assertThat(result.get().companyName()).isNull();
        assertThat(result.get().companyLogoUrl()).isNull();
    }

    @Test
    @DisplayName("AS244-CW-04: a stub whose company.logoUrl is an empty string passes the raw empty string "
            + "through unmodified (normalisation, if any, happens one layer up, not in this adapter)")
    void passesEmptyStringLogoUrlThroughUnmodified() {
        UUID jobPostId = UUID.randomUUID();
        stubJob(jobPostId, """
                {
                  "id": "%s",
                  "title": "Backend Developer",
                  "url": "https://job.example/%s",
                  "description": "desc",
                  "location": "Madrid, Spain",
                  "company": { "name": "Acme Corp", "logoUrl": "" }
                }
                """.formatted(jobPostId, jobPostId));

        Optional<JobPostGateway.JobPostView> result = jobPostGateway.findById(jobPostId);

        assertThat(result).isPresent();
        assertThat(result.get().companyLogoUrl()).isEqualTo("");
    }

    @Test
    @DisplayName("AS244-CW-05: a 404 from job-service still returns Optional.empty(), no exception "
            + "(regression of existing not-found handling, unaffected by the widened response shape)")
    void notFoundStillReturnsEmpty() {
        UUID jobPostId = UUID.randomUUID();
        jobService().stubFor(get(urlPathEqualTo("/jobs/" + jobPostId))
                .willReturn(aResponse().withStatus(404)));

        Optional<JobPostGateway.JobPostView> result = jobPostGateway.findById(jobPostId);

        assertThat(result).isEmpty();
    }

    private void stubJob(UUID jobPostId, String body) {
        jobService().stubFor(get(urlPathEqualTo("/jobs/" + jobPostId))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private WireMockServer jobService() {
        return WireMockJobServiceResource.server();
    }
}
