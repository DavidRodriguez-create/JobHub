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

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

/**
 * Component tests for {@code GET /internal/users/{userId}/interest-profile}, exercising the
 * contract defined in {@code api-contracts/openapi/application-service.yaml}. Protected by
 * {@code X-Service-Key} (ADR 0008), not a user JWT.
 */
@QuarkusTest
@DisplayName("Internal Interest Profile Resource Component Tests")
class InternalInterestProfileResourceComponentTest {

    private static final String BASE = "/internal/users";
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final String VALID_KEY = "test-internal-key";

    private static final UUID USER_WITH_HISTORY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_NO_HISTORY = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_NONEXISTENT = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void seedHistory() {
        // content_hash has a unique constraint; suffix per-run so re-seeding across
        // this class's multiple @QuarkusTest methods never collides.
        String runSuffix = UUID.randomUUID().toString();
        QuarkusTransaction.requiringNew().run(() -> {
            // app-1: "Senior Backend Java Developer" @ Acme Corp, Barcelona, Spain (crawled)
            seedCrawledApplication(
                    "Senior Backend Java Developer", "Acme Corp", "Barcelona, Spain", "hash-1-" + runSuffix);
            // app-2: "Backend Java Engineer" @ Acme Corp, Barcelona, Spain (crawled)
            seedCrawledApplication(
                    "Backend Java Engineer", "Acme Corp", "Barcelona, Spain", "hash-2-" + runSuffix);
            // app-3: "Java Developer" @ Globex, Madrid, Spain (crawled)
            seedCrawledApplication(
                    "Java Developer", "Globex", "Madrid, Spain", "hash-3-" + runSuffix);
            // app-4: "Frontend Developer" @ Initech, Remote (manual)
            seedManualApplication(
                    "Frontend Developer", "Initech", "Remote", "https://initech.example/jobs/1-" + runSuffix);
        });
    }

    private void seedCrawledApplication(String title, String company, String location, String contentHash) {
        JobPostSnapshotEntity snapshot = new JobPostSnapshotEntity();
        snapshot.jobPostId = UUID.randomUUID();
        snapshot.contentHash = contentHash;
        snapshot.title = title;
        snapshot.company = company;
        snapshot.location = location;
        snapshot.url = "https://job.example/" + contentHash;
        snapshot.snapshottedAt = OffsetDateTime.now();
        entityManager.persist(snapshot);

        ApplicationEntity application = new ApplicationEntity();
        application.userId = USER_WITH_HISTORY;
        application.jobPostSnapshotId = snapshot.id;
        application.jobPostId = snapshot.jobPostId;
        application.status = ApplicationStatus.APPLIED;
        application.appliedAt = OffsetDateTime.now();
        application.createdAt = OffsetDateTime.now();
        application.updatedAt = OffsetDateTime.now();
        entityManager.persist(application);
    }

    private void seedManualApplication(String title, String company, String location, String url) {
        UserJobPostEntity userJobPost = new UserJobPostEntity();
        userJobPost.userId = USER_WITH_HISTORY;
        userJobPost.title = title;
        userJobPost.company = company;
        userJobPost.location = location;
        userJobPost.url = url;
        userJobPost.createdAt = OffsetDateTime.now();
        userJobPost.updatedAt = OffsetDateTime.now();
        entityManager.persist(userJobPost);

        ApplicationEntity application = new ApplicationEntity();
        application.userId = USER_WITH_HISTORY;
        application.userJobPostId = userJobPost.id;
        application.status = ApplicationStatus.APPLIED;
        application.appliedAt = OffsetDateTime.now();
        application.createdAt = OffsetDateTime.now();
        application.updatedAt = OffsetDateTime.now();
        entityManager.persist(application);
    }

    @Test
    @DisplayName("returns aggregated top-5 locations/companies/keywords for a user with history")
    void returnsAggregatedProfileForUserWithHistory() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .when().get(BASE + "/" + USER_WITH_HISTORY + "/interest-profile")
                .then().statusCode(200)
                .body("userId", org.hamcrest.Matchers.equalTo(USER_WITH_HISTORY.toString()))
                .body("locations", hasItem("Barcelona, Spain"))
                .body("locations.size()", lessThanOrEqualTo(5))
                .body("companies", hasItem("Acme Corp"))
                .body("companies.size()", lessThanOrEqualTo(5))
                .body("keywords", hasItem("java"))
                .body("keywords", hasItem("developer"))
                .body("keywords.size()", lessThanOrEqualTo(5));
    }

    @Test
    @DisplayName("returns empty arrays for a user with no application history")
    void returnsEmptyArraysForUserWithNoHistory() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .when().get(BASE + "/" + USER_NO_HISTORY + "/interest-profile")
                .then().statusCode(200)
                .body("userId", org.hamcrest.Matchers.equalTo(USER_NO_HISTORY.toString()))
                .body("locations", empty())
                .body("companies", empty())
                .body("keywords", empty());
    }

    @Test
    @DisplayName("returns empty arrays (not 404) for a nonexistent userId")
    void returnsEmptyArraysForNonexistentUser() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .when().get(BASE + "/" + USER_NONEXISTENT + "/interest-profile")
                .then().statusCode(200)
                .body("userId", org.hamcrest.Matchers.equalTo(USER_NONEXISTENT.toString()))
                .body("locations", empty())
                .body("companies", empty())
                .body("keywords", empty());
    }

    @Test
    @DisplayName("invalid userId format → 400")
    void invalidUserIdFormatReturns400() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .when().get(BASE + "/not-a-uuid/interest-profile")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("missing X-Service-Key → 401")
    void missingServiceKeyReturns401() {
        given()
                .when().get(BASE + "/" + USER_WITH_HISTORY + "/interest-profile")
                .then().statusCode(401);
    }

    @Test
    @DisplayName("wrong X-Service-Key → 401")
    void wrongServiceKeyReturns401() {
        given().header(SERVICE_KEY_HEADER, "wrong-value")
                .when().get(BASE + "/" + USER_WITH_HISTORY + "/interest-profile")
                .then().statusCode(401);
    }
}
