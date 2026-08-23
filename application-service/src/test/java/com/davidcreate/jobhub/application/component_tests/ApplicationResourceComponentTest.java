package com.davidcreate.jobhub.application.component_tests;

import com.davidcreate.jobhub.application.application.port.out.JobPostGateway;
import com.davidcreate.jobhub.application.application.port.out.VerificationGateway;
import com.davidcreate.jobhub.application.domain.exception.InvalidVerificationException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Component tests for the application-service REST surface, exercising the contract
 * defined in {@code api-contracts/openapi/application-service.yaml}.
 *
 * <p>Server-error (500) and gateway cases live in {@link ApplicationResourceFailureComponentTest}.
 */
@QuarkusTest
@DisplayName("Application Resource Component Tests")
class ApplicationResourceComponentTest {

    private static final String BASE = "/applications";
    private static final String USER_A = "33333333-3333-3333-3333-333333333333";

    @InjectMock
    JobPostGateway jobPostGateway;

    @InjectMock
    VerificationGateway verificationGateway;

    private UUID jobPostId;

    @BeforeEach
    void stubGateway() {
        jobPostId = UUID.randomUUID();
        when(jobPostGateway.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.of(new JobPostGateway.JobPostView(
                    id, "Senior Dev", "https://job/" + id, "great role", "Madrid, Spain",
                    "Acme Corp", "https://cdn.example/acme.png"));
        });
    }

    @Test
    @DisplayName("requires authentication")
    void unauthenticated() {
        given().when().get(BASE).then().statusCode(401);
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("POST crawled job-post → 201, snapshot created, job summary populated")
    void applyToCrawled() {
        UUID id = given().contentType("application/json")
                .body(Map.of("jobPostId", jobPostId.toString()))
                .when().post(BASE)
                .then().statusCode(201)
                .body("status", equalTo("applied"))
                .body("jobPostId", equalTo(jobPostId.toString()))
                .body("jobPostSnapshotId", notNullValue())
                .body("job.title", equalTo("Senior Dev"))
                .body("job.location", equalTo("Madrid, Spain"))
                .extract().jsonPath().getUUID("id");

        given().when().get(BASE + "/" + id)
                .then().statusCode(200)
                .body("id", equalTo(id.toString()));
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("AS244-C-04: POST crawled job-post with a job-service company logo → 201, job.companyLogoUrl frozen on the snapshot")
    void applyToCrawledCapturesCompanyLogo() {
        UUID id = given().contentType("application/json")
                .body(Map.of("jobPostId", jobPostId.toString()))
                .when().post(BASE)
                .then().statusCode(201)
                .body("job.companyLogoUrl", equalTo("https://cdn.example/acme.png"))
                .extract().jsonPath().getUUID("id");

        given().when().get(BASE + "/" + id)
                .then().statusCode(200)
                .body("job.companyLogoUrl", equalTo("https://cdn.example/acme.png"));
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("AS244-CW-04/EC-244-2: job-service company.logoUrl=\"\" is normalised to null, never an empty string, on the snapshot read")
    void applyToCrawledEmptyLogoUrlNormalisedToNull() {
        UUID emptyLogoJobPostId = UUID.randomUUID();
        when(jobPostGateway.findById(emptyLogoJobPostId)).thenReturn(Optional.of(new JobPostGateway.JobPostView(
                emptyLogoJobPostId, "Other Dev", "https://job/" + emptyLogoJobPostId, "role", "Remote",
                "Foo Inc", "")));

        given().contentType("application/json")
                .body(Map.of("jobPostId", emptyLogoJobPostId.toString()))
                .when().post(BASE)
                .then().statusCode(201)
                .body("job.company", equalTo("Foo Inc"))
                .body("job.companyLogoUrl", nullValue());
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("AS244-C-06: a crawled application backed by a pre-fix snapshot (company=null, "
            + "companyLogoUrl=null) returns 200 with both null and job.title still populated, no 500")
    void applyToCrawledPreFixSnapshotShapeReturnsNullsNotError() {
        UUID preFixJobPostId = UUID.randomUUID();
        when(jobPostGateway.findById(preFixJobPostId)).thenReturn(Optional.of(new JobPostGateway.JobPostView(
                preFixJobPostId, "Legacy Crawled Role", "https://job/" + preFixJobPostId, "role", "Remote",
                null, null)));

        UUID id = given().contentType("application/json")
                .body(Map.of("jobPostId", preFixJobPostId.toString()))
                .when().post(BASE)
                .then().statusCode(201)
                .body("job.title", equalTo("Legacy Crawled Role"))
                .body("job.company", nullValue())
                .body("job.companyLogoUrl", nullValue())
                .extract().jsonPath().getUUID("id");

        given().when().get(BASE + "/" + id)
                .then().statusCode(200)
                .body("job.title", equalTo("Legacy Crawled Role"))
                .body("job.company", nullValue())
                .body("job.companyLogoUrl", nullValue());
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("POST manual jobDetails → 201, no snapshot, live job summary, no logo (manual entries never have one)")
    void applyManual() {
        given().contentType("application/json")
                .body(Map.of("jobDetails", Map.of(
                        "title", "Indie Role", "company", "Acme",
                        "url", "https://acme.example/jobs/9", "location", "Remote")))
                .when().post(BASE)
                .then().statusCode(201)
                .body("jobPostSnapshotId", nullValue())
                .body("job.title", equalTo("Indie Role"))
                .body("job.company", equalTo("Acme"))
                .body("job.location", equalTo("Remote"))
                .body("job.companyLogoUrl", nullValue());
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("POST with neither jobPostId nor jobDetails → 400")
    void applyNeither() {
        given().contentType("application/json").body(Map.of())
                .when().post(BASE).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("POST manual jobDetails without title or url → 400")
    void applyManualNoTitleOrUrl() {
        given().contentType("application/json")
                .body(Map.of("jobDetails", Map.of("company", "Acme", "location", "Remote")))
                .when().post(BASE).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("applying twice to the same crawled post → 409 (one application per job post)")
    void rejectsDuplicateApplication() {
        var body = Map.of("jobPostId", jobPostId.toString());

        given().contentType("application/json").body(body)
                .when().post(BASE).then().statusCode(201);

        given().contentType("application/json").body(body)
                .when().post(BASE).then().statusCode(409)
                .body("error", equalTo("Conflict"));
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("GET list returns a paginated ApplicationPage and filters by status")
    void listAndFilter() {
        createCrawledApplication();
        createCrawledApplication();

        given().when().get(BASE)
                .then().statusCode(200)
                .body("content", notNullValue())
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalElements", notNullValue());

        given().when().get(BASE + "?status=interviewing")
                .then().statusCode(200)
                .body("totalElements", equalTo(0));
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("GET list accepts the screening status filter → 200")
    void listFilterByScreening() {
        given().when().get(BASE + "?status=screening")
                .then().statusCode(200)
                .body("totalElements", notNullValue());
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("PATCH status to ghosted is terminal and stamps endedAt")
    void updateStatusGhosted() {
        UUID id = createCrawledApplication();

        given().contentType("application/json")
                .body(Map.of("status", "ghosted"))
                .when().patch(BASE + "/" + id + "/status")
                .then().statusCode(200)
                .body("status", equalTo("ghosted"))
                .body("endedAt", notNullValue());
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("PATCH status to screening is non-terminal (endedAt stays null)")
    void updateStatusScreening() {
        UUID id = createCrawledApplication();

        given().contentType("application/json")
                .body(Map.of("status", "screening"))
                .when().patch(BASE + "/" + id + "/status")
                .then().statusCode(200)
                .body("status", equalTo("screening"))
                .body("endedAt", nullValue());
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("GET /stats returns total and per-status counts")
    void stats() {
        createCrawledApplication();
        createCrawledApplication();

        given().when().get(BASE + "/stats")
                .then().statusCode(200)
                .body("total", notNullValue())
                .body("byStatus.applied", notNullValue())
                .body("byStatus.interviewing", equalTo(0));
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("PATCH status transitions to a terminal state and stamps endedAt")
    void updateStatus() {
        UUID id = createCrawledApplication();

        given().contentType("application/json")
                .body(Map.of("status", "rejected"))
                .when().patch(BASE + "/" + id + "/status")
                .then().statusCode(200)
                .body("status", equalTo("rejected"))
                .body("endedAt", notNullValue());
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("PATCH status rejects unknown status value → 400")
    void updateStatusUnknown() {
        UUID id = createCrawledApplication();
        given().contentType("application/json")
                .body(Map.of("status", "banana"))
                .when().patch(BASE + "/" + id + "/status")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("PATCH /{id}/job patches job details of a manual entry")
    void patchManualJob() {
        UUID id = given().contentType("application/json")
                .body(Map.of("jobDetails", Map.of("title", "Old Title", "company", "OldCo")))
                .when().post(BASE).then().statusCode(201)
                .extract().jsonPath().getUUID("id");

        given().contentType("application/json")
                .body(Map.of("company", "NewCo"))
                .when().patch(BASE + "/" + id + "/job")
                .then().statusCode(200)
                .body("job.title", equalTo("Old Title"))
                .body("job.company", equalTo("NewCo"));
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("PATCH /{id}/job on a crawled-job application → 409 (snapshot is immutable)")
    void patchCrawledJobConflict() {
        UUID id = createCrawledApplication();

        given().contentType("application/json")
                .body(Map.of("company", "NewCo"))
                .when().patch(BASE + "/" + id + "/job")
                .then().statusCode(409);
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("DELETE removes the application; subsequent GET → 404")
    void deleteOk() {
        UUID id = createCrawledApplication();
        given().when().delete(BASE + "/" + id).then().statusCode(204);
        given().when().get(BASE + "/" + id).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("GET a missing application → 404")
    void getMissing() {
        given().when().get(BASE + "/" + UUID.randomUUID()).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("GET /stats/history returns one MonthlyCount per requested month")
    void statsHistory() {
        given().when().get(BASE + "/stats/history?months=3")
                .then().statusCode(200)
                .body("size()", equalTo(3))
                .body("[0].year", notNullValue())
                .body("[0].month", notNullValue())
                .body("[0].byStatus", notNullValue());
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("GET /stats/history rejects out-of-range months → 400")
    void statsHistoryBadRange() {
        given().when().get(BASE + "/stats/history?months=50").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("PATCH /applications/{id} updates notes, contact, portalUrl and nextStep")
    void updateApplicationFields() {
        UUID id = createCrawledApplication();

        given().contentType("application/json")
                .body(Map.of(
                        "notes", "Spoke with the recruiter",
                        "contact", "Jane Recruiter",
                        "portalUrl", "https://portal.example/apply",
                        "nextStep", Map.of("label", "Tech interview", "date", "2026-06-15")))
                .when().patch(BASE + "/" + id)
                .then().statusCode(200)
                .body("notes", equalTo("Spoke with the recruiter"))
                .body("contact", equalTo("Jane Recruiter"))
                .body("portalUrl", equalTo("https://portal.example/apply"))
                .body("nextStep.label", equalTo("Tech interview"))
                .body("nextStep.date", equalTo("2026-06-15"));
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("GET /{id} returns the status-change timeline (omitted from the list view)")
    void timelineOnDetailView() {
        UUID id = createCrawledApplication();
        given().contentType("application/json").body(Map.of("status", "screening"))
                .when().patch(BASE + "/" + id + "/status").then().statusCode(200);

        given().when().get(BASE + "/" + id)
                .then().statusCode(200)
                .body("timeline.size()", greaterThanOrEqualTo(2))
                .body("timeline[0].status", equalTo("applied"))
                .body("timeline[1].status", equalTo("screening"));

        // list view carries no timeline data (empty for every entry)
        given().when().get(BASE)
                .then().statusCode(200)
                .body("content[0].timeline.size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("DELETE /applications consumes the verification code and wipes the user's applications → 204")
    void deleteAllOk() {
        createCrawledApplication();

        given().contentType("application/json")
                .body(Map.of("verificationId", UUID.randomUUID().toString(), "code", "123456"))
                .when().delete(BASE)
                .then().statusCode(204);

        given().when().get(BASE)
                .then().statusCode(200)
                .body("totalElements", equalTo(0));
    }

    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("DELETE /applications with an invalid verification code → 400")
    void deleteAllInvalidCode() {
        doThrow(new InvalidVerificationException("verification code is invalid or expired"))
                .when(verificationGateway).consumeDeleteAllApplications(any(), any(), any());

        given().contentType("application/json")
                .body(Map.of("verificationId", UUID.randomUUID().toString(), "code", "000000"))
                .when().delete(BASE)
                .then().statusCode(400);
    }

    private UUID createCrawledApplication() {
        return given().contentType("application/json")
                .body(Map.of("jobPostId", UUID.randomUUID().toString()))
                .when().post(BASE).then().statusCode(201)
                .extract().jsonPath().getUUID("id");
    }
}
