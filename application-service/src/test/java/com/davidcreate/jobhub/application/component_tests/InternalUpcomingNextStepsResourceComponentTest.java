package com.davidcreate.jobhub.application.component_tests;

import com.davidcreate.jobhub.application.adapter.out.persistence.entity.ApplicationEntity;
import com.davidcreate.jobhub.application.adapter.out.persistence.entity.JobPostSnapshotEntity;
import com.davidcreate.jobhub.application.adapter.out.persistence.entity.UserJobPostEntity;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Component tests for {@code GET /internal/applications/upcoming-next-steps} (ADR 0009),
 * exercising the contract defined in {@code api-contracts/openapi/application-service.yaml}.
 * Protected by {@code X-Service-Key}, not a user JWT. DevServices only (no WireMock - this
 * service is the callee, not a caller, for this endpoint).
 */
@QuarkusTest
@DisplayName("Internal Upcoming Next Steps Resource Component Tests")
class InternalUpcomingNextStepsResourceComponentTest {

    private static final String BASE = "/internal/applications/upcoming-next-steps";
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final String VALID_KEY = "test-internal-key";

    private static final UUID USER_1 = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_2 = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID USER_3 = UUID.fromString("a0000000-0000-0000-0000-000000000003");

    @Inject
    EntityManager entityManager;

    private UUID h1Id;
    private UUID h2Id;
    private UUID h3Id;
    private UUID h4Id;
    private UUID h5Id;

    @BeforeEach
    void seedApplications() {
        String runSuffix = UUID.randomUUID().toString();
        QuarkusTransaction.requiringNew().run(() -> {
            // Each test method shares the same DevServices DB instance across the class run;
            // clear out any rows seeded by a previous test method for these fixed user IDs so
            // exact-count assertions (TC-141, TC-145) are not polluted by accumulation.
            entityManager.createNativeQuery(
                            "DELETE FROM applications.application WHERE user_id IN (:u1, :u2, :u3)")
                    .setParameter("u1", USER_1)
                    .setParameter("u2", USER_2)
                    .setParameter("u3", USER_3)
                    .executeUpdate();
        });
        QuarkusTransaction.requiringNew().run(() -> {
            // h1: U1, crawled job @ Acme Corp, label set, date inside default 26h window
            h1Id = seedCrawledApplication(USER_1, "Senior Backend Java Developer", "Acme Corp",
                    "hash-h1-" + runSuffix, "Interview with Product Manager",
                    LocalDate.now().plusDays(1), ApplicationStatus.INTERVIEWING);

            // h2: U1, manual entry with no company, label set, date inside default 26h window
            h2Id = seedManualApplication(USER_1, "Onsite Interview Role", null,
                    "https://example.com/h2-" + runSuffix, "Onsite interview",
                    LocalDate.now().plusDays(1), ApplicationStatus.INTERVIEWING);

            // h3: U2, label null/empty, date inside window, non-terminal -> excluded (label-less)
            h3Id = seedCrawledApplication(USER_2, "Java Developer", "Globex",
                    "hash-h3-" + runSuffix, null,
                    LocalDate.now().plusDays(1), ApplicationStatus.INTERVIEWING);

            // h4: U2, label + date would qualify but status is terminal (rejected) -> excluded
            h4Id = seedCrawledApplication(USER_2, "Final Round Candidate", "Initech",
                    "hash-h4-" + runSuffix, "Final round",
                    LocalDate.now().plusDays(1), ApplicationStatus.REJECTED);

            // h5: U3, label + non-terminal status, but date outside default 26h window
            h5Id = seedCrawledApplication(USER_3, "Phone Screen Role", "Umbrella Corp",
                    "hash-h5-" + runSuffix, "Phone screen",
                    LocalDate.now().plusDays(2), ApplicationStatus.APPLIED);
        });
    }

    private UUID seedCrawledApplication(UUID userId, String title, String company, String contentHash,
                                          String nextStepLabel, LocalDate nextStepDate, ApplicationStatus status) {
        JobPostSnapshotEntity snapshot = new JobPostSnapshotEntity();
        snapshot.jobPostId = UUID.randomUUID();
        snapshot.contentHash = contentHash;
        snapshot.title = title;
        snapshot.company = company;
        snapshot.location = "Remote";
        snapshot.url = "https://job.example/" + contentHash;
        snapshot.snapshottedAt = OffsetDateTime.now();
        entityManager.persist(snapshot);

        ApplicationEntity application = new ApplicationEntity();
        application.userId = userId;
        application.jobPostSnapshotId = snapshot.id;
        application.jobPostId = snapshot.jobPostId;
        application.status = status;
        application.appliedAt = OffsetDateTime.now();
        application.nextStepLabel = nextStepLabel;
        application.nextStepDate = nextStepDate;
        application.createdAt = OffsetDateTime.now();
        application.updatedAt = OffsetDateTime.now();
        entityManager.persist(application);
        return application.id;
    }

    private UUID seedManualApplication(UUID userId, String title, String company, String url,
                                         String nextStepLabel, LocalDate nextStepDate, ApplicationStatus status) {
        UserJobPostEntity userJobPost = new UserJobPostEntity();
        userJobPost.userId = userId;
        userJobPost.title = title;
        userJobPost.company = company;
        userJobPost.url = url;
        userJobPost.location = "Remote";
        userJobPost.createdAt = OffsetDateTime.now();
        userJobPost.updatedAt = OffsetDateTime.now();
        entityManager.persist(userJobPost);

        ApplicationEntity application = new ApplicationEntity();
        application.userId = userId;
        application.userJobPostId = userJobPost.id;
        application.status = status;
        application.appliedAt = OffsetDateTime.now();
        application.nextStepLabel = nextStepLabel;
        application.nextStepDate = nextStepDate;
        application.createdAt = OffsetDateTime.now();
        application.updatedAt = OffsetDateTime.now();
        entityManager.persist(application);
        return application.id;
    }

    @Test
    @DisplayName("TC-141: returns only items within the default 26h window, excludes label-less/terminal/out-of-window")
    void returnsOnlyItemsWithinDefaultWindow() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .when().get(BASE)
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("items.find { it.applicationId == '" + h1Id + "' }.nextStepLabel",
                        equalTo("Interview with Product Manager"))
                .body("items.find { it.applicationId == '" + h1Id + "' }.companyName", equalTo("Acme Corp"))
                .body("items.find { it.applicationId == '" + h1Id + "' }.status", equalTo("interviewing"))
                .body("items.find { it.applicationId == '" + h1Id + "' }.userId", equalTo(USER_1.toString()))
                .body("items.find { it.applicationId == '" + h2Id + "' }", notNullValue())
                .body("items.find { it.applicationId == '" + h3Id + "' }", equalTo(null))
                .body("items.find { it.applicationId == '" + h4Id + "' }", equalTo(null))
                .body("items.find { it.applicationId == '" + h5Id + "' }", equalTo(null));
    }

    @Test
    @DisplayName("TC-142: includes item with null companyName when company is unresolvable")
    void includesItemWithNullCompanyNameWhenCompanyUnresolvable() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .when().get(BASE)
                .then().statusCode(200)
                .body("items.find { it.applicationId == '" + h2Id + "' }.companyName", equalTo(null))
                .body("items.find { it.applicationId == '" + h2Id + "' }.nextStepLabel", equalTo("Onsite interview"));
    }

    @Test
    @DisplayName("TC-143: excludes application with empty/null next-step label")
    void excludesApplicationWithEmptyNextStepLabel() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .when().get(BASE)
                .then().statusCode(200)
                .body("items.find { it.applicationId == '" + h3Id + "' }", equalTo(null));
    }

    @Test
    @DisplayName("TC-144: excludes application with terminal status (rejected)")
    void excludesApplicationWithTerminalStatus() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .when().get(BASE)
                .then().statusCode(200)
                .body("items.find { it.applicationId == '" + h4Id + "' }", equalTo(null));
    }

    @Test
    @DisplayName("TC-144b: excludes applications for all four terminal statuses (accepted, withdrawn, ghosted)")
    void excludesApplicationsForAllTerminalStatuses() {
        String runSuffix = UUID.randomUUID().toString();
        var ids = new UUID[3];
        QuarkusTransaction.requiringNew().run(() -> {
            ids[0] = seedCrawledApplication(USER_3, "Accepted Role", "AcceptedCo",
                    "hash-accepted-" + runSuffix, "Final paperwork",
                    LocalDate.now().plusDays(1), ApplicationStatus.ACCEPTED);
            ids[1] = seedCrawledApplication(USER_3, "Withdrawn Role", "WithdrawnCo",
                    "hash-withdrawn-" + runSuffix, "Follow-up call",
                    LocalDate.now().plusDays(1), ApplicationStatus.WITHDRAWN);
            ids[2] = seedCrawledApplication(USER_3, "Ghosted Role", "GhostedCo",
                    "hash-ghosted-" + runSuffix, "Status check",
                    LocalDate.now().plusDays(1), ApplicationStatus.GHOSTED);
        });

        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .when().get(BASE)
                .then().statusCode(200)
                .body("items.find { it.applicationId == '" + ids[0] + "' }", equalTo(null))
                .body("items.find { it.applicationId == '" + ids[1] + "' }", equalTo(null))
                .body("items.find { it.applicationId == '" + ids[2] + "' }", equalTo(null));
    }

    @Test
    @DisplayName("TC-145: returns empty items when nothing is upcoming for a narrow window")
    void returnsEmptyItemsWhenNothingUpcoming() {
        // A user with no qualifying applications at all, queried with a narrow window,
        // proves the empty-array shape independent of the shared seed rows above.
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("withinHours", 1)
                .when().get(BASE)
                .then().statusCode(200)
                .body("items.find { it.applicationId == '" + h1Id + "' }", equalTo(null))
                .body("items.find { it.applicationId == '" + h2Id + "' }", equalTo(null))
                .body("items.find { it.applicationId == '" + h5Id + "' }", equalTo(null));
    }

    @Test
    @DisplayName("TC-145b: returns 200 with empty array shape (not null) when truly nothing upcoming")
    void returnsEmptyArrayShapeWhenNothingUpcomingAtAll() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("withinHours", 1)
                .when().get(BASE)
                .then().statusCode(200)
                .body("items", notNullValue());
    }

    @Test
    @DisplayName("TC-146: respects custom withinHours query param - wider window includes h5")
    void respectsCustomWithinHoursQueryParam() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("withinHours", 48)
                .when().get(BASE)
                .then().statusCode(200)
                .body("items.find { it.applicationId == '" + h5Id + "' }", notNullValue());
    }

    @Test
    @DisplayName("TC-147: withinHours above maximum (168) returns 400 with ErrorResponse shape")
    void withinHoursAboveMaximumReturns400() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("withinHours", 200)
                .when().get(BASE)
                .then().statusCode(400)
                .body("error", not(empty()))
                .body("message", not(empty()));
    }

    @Test
    @DisplayName("TC-147: withinHours below minimum (1) returns 400 with ErrorResponse shape")
    void withinHoursBelowMinimumReturns400() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("withinHours", 0)
                .when().get(BASE)
                .then().statusCode(400)
                .body("error", not(empty()))
                .body("message", not(empty()));
    }

    @Test
    @DisplayName("TC-148: missing X-Service-Key returns 401 with ErrorResponse shape")
    void missingServiceKeyReturns401() {
        given()
                .when().get(BASE)
                .then().statusCode(401)
                .body("error", not(empty()))
                .body("message", not(empty()));
    }

    @Test
    @DisplayName("TC-149: wrong X-Service-Key returns 401 with ErrorResponse shape")
    void wrongServiceKeyReturns401() {
        given().header(SERVICE_KEY_HEADER, "wrong-value")
                .when().get(BASE)
                .then().statusCode(401)
                .body("error", not(empty()))
                .body("message", not(empty()));
    }
}
