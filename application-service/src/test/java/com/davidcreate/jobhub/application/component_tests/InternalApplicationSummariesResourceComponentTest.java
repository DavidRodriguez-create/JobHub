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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Component tests for {@code GET /internal/applications/summaries} (ADR 0014, story #207,
 * ticket #217), exercising the frozen contract in
 * {@code api-contracts/openapi/application-service.yaml}. Protected by {@code X-Service-Key},
 * not a user JWT. DevServices only (no WireMock - this service is the callee, not a caller,
 * for this endpoint, matching CLAUDE.md's WireMock-only-for-outbound-HTTP rule).
 *
 * <p>Note on ownership: the frozen contract has no userId/scoping parameter (just {@code ids}),
 * matching the cross-user shape of the sibling stale/upcoming-next-steps internal endpoints.
 * An existing application id therefore resolves regardless of which user owns it; AS-C-04
 * documents this explicitly since the QAE catalogue's "owned by a different user" framing
 * does not map to a distinguishable filter on this particular endpoint (see PR notes).
 */
@QuarkusTest
@DisplayName("Internal Application Summaries Resource Component Tests")
class InternalApplicationSummariesResourceComponentTest {

    private static final String BASE = "/internal/applications/summaries";
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final String VALID_KEY = "test-internal-key";

    private static final UUID USER_1 = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID USER_2 = UUID.fromString("b0000000-0000-0000-0000-000000000002");

    @Inject
    EntityManager entityManager;

    private UUID crawledAppId;
    private UUID manualAppId;
    private UUID otherUserAppId;
    private UUID crawledWithLogoAppId;
    private UUID preFixSnapshotAppId;

    @BeforeEach
    void seedApplications() {
        String runSuffix = UUID.randomUUID().toString();
        QuarkusTransaction.requiringNew().run(() -> {
            // Each test method shares the same DevServices DB instance across the class run;
            // clear out any rows seeded by a previous test method for these fixed user IDs so
            // exact-count assertions (AS-C-01, AS-C-02, AS-C-05) are not polluted by accumulation.
            entityManager.createNativeQuery(
                            "DELETE FROM applications.application WHERE user_id IN (:u1, :u2)")
                    .setParameter("u1", USER_1)
                    .setParameter("u2", USER_2)
                    .executeUpdate();
        });
        QuarkusTransaction.requiringNew().run(() -> {
            crawledAppId = seedCrawledApplication(USER_1, "Senior Backend Java Developer", "Acme Corp",
                    "summaries-crawled-" + runSuffix, null);
            manualAppId = seedManualApplication(USER_1, "Onsite Interview Role", "Initech",
                    "https://example.com/summaries-manual-" + runSuffix);
            otherUserAppId = seedCrawledApplication(USER_2, "Final Round Candidate", "Globex",
                    "summaries-other-user-" + runSuffix, null);
            crawledWithLogoAppId = seedCrawledApplication(USER_1, "Platform Engineer", "Stark Industries",
                    "summaries-with-logo-" + runSuffix, "https://cdn.example/stark.png");
            // S4 shape (ADR 0015 "no backfill"): a pre-fix crawled snapshot has company=null and
            // companyLogoUrl=null even though jobTitle is populated.
            preFixSnapshotAppId = seedCrawledApplication(USER_1, "Legacy Crawled Role", null,
                    "summaries-pre-fix-" + runSuffix, null);
        });
    }

    private UUID seedCrawledApplication(UUID userId, String title, String company, String contentHash) {
        return seedCrawledApplication(userId, title, company, contentHash, null);
    }

    private UUID seedCrawledApplication(UUID userId, String title, String company, String contentHash,
            String companyLogoUrl) {
        JobPostSnapshotEntity snapshot = new JobPostSnapshotEntity();
        snapshot.jobPostId = UUID.randomUUID();
        snapshot.contentHash = contentHash;
        snapshot.title = title;
        snapshot.company = company;
        snapshot.companyLogoUrl = companyLogoUrl;
        snapshot.location = "Remote";
        snapshot.url = "https://job.example/" + contentHash;
        snapshot.snapshottedAt = OffsetDateTime.now();
        entityManager.persist(snapshot);

        ApplicationEntity application = new ApplicationEntity();
        application.userId = userId;
        application.jobPostSnapshotId = snapshot.id;
        application.jobPostId = snapshot.jobPostId;
        application.status = ApplicationStatus.APPLIED;
        application.appliedAt = OffsetDateTime.now();
        application.createdAt = OffsetDateTime.now();
        application.updatedAt = OffsetDateTime.now();
        entityManager.persist(application);
        return application.id;
    }

    private UUID seedManualApplication(UUID userId, String title, String company, String url) {
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
        application.status = ApplicationStatus.APPLIED;
        application.appliedAt = OffsetDateTime.now();
        application.createdAt = OffsetDateTime.now();
        application.updatedAt = OffsetDateTime.now();
        entityManager.persist(application);
        return application.id;
    }

    @Test
    @DisplayName("AS-C-01: one valid id with valid X-Service-Key returns 200 with exactly one item, fields populated")
    void singleValidIdReturnsOneItem() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", crawledAppId.toString())
                .when().get(BASE)
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].applicationId", equalTo(crawledAppId.toString()))
                .body("items[0].company", equalTo("Acme Corp"))
                .body("items[0].jobTitle", equalTo("Senior Backend Java Developer"));
    }

    @Test
    @DisplayName("AS-C-02: two ids that both resolve return 200 with both items, order-independent")
    void twoValidIdsReturnBothItems() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", crawledAppId + "," + manualAppId)
                .when().get(BASE)
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("items.find { it.applicationId == '" + crawledAppId + "' }.company", equalTo("Acme Corp"))
                .body("items.find { it.applicationId == '" + manualAppId + "' }.company", equalTo("Initech"));
    }

    @Test
    @DisplayName("AS-C-03: one resolvable + one unknown id returns 200 with exactly the resolvable item")
    void oneValidOneUnknownReturnsOnlyValid() {
        UUID unknown = UUID.randomUUID();
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", crawledAppId + "," + unknown)
                .when().get(BASE)
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].applicationId", equalTo(crawledAppId.toString()));
    }

    @Test
    @DisplayName("AS-C-04: an id owned by a different user resolves (no caller-scoping param on this "
            + "frozen contract; cross-user by design, like the sibling stale/upcoming-next-steps endpoints)")
    void idOwnedByDifferentUserStillResolves() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", otherUserAppId.toString())
                .when().get(BASE)
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].applicationId", equalTo(otherUserAppId.toString()))
                .body("items[0].company", equalTo("Globex"));
    }

    @Test
    @DisplayName("AS-C-05: a duplicate id in the request collapses to a single item")
    void duplicateIdCollapsesToOneItem() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", crawledAppId + "," + crawledAppId)
                .when().get(BASE)
                .then().statusCode(200)
                .body("items", hasSize(1));
    }

    @Test
    @DisplayName("AS-C-06: all requested ids unresolvable returns 200 with an empty items array")
    void allUnresolvableReturnsEmptyItems() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", UUID.randomUUID() + "," + UUID.randomUUID())
                .when().get(BASE)
                .then().statusCode(200)
                .body("items", empty());
    }

    @Test
    @DisplayName("AS-C-07: missing ids query parameter returns 400 with ErrorResponse shape")
    void missingIdsReturns400() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .when().get(BASE)
                .then().statusCode(400)
                .body("error", not(empty()))
                .body("message", not(empty()));
    }

    @Test
    @DisplayName("AS-C-08: ids present but empty value returns 400")
    void emptyIdsValueReturns400() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", "")
                .when().get(BASE)
                .then().statusCode(400)
                .body("error", not(empty()))
                .body("message", not(empty()));
    }

    @Test
    @DisplayName("AS-C-09: a malformed UUID in ids returns 400")
    void malformedUuidReturns400() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", "not-a-uuid")
                .when().get(BASE)
                .then().statusCode(400)
                .body("error", not(empty()))
                .body("message", not(empty()));
    }

    @Test
    @DisplayName("AS-C-10: 101 distinct synthetic ids (maxItems: 100 exceeded) returns 400")
    void moreThan100IdsReturns400() {
        String ids = syntheticIds(101);
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", ids)
                .when().get(BASE)
                .then().statusCode(400)
                .body("error", not(empty()))
                .body("message", not(empty()));
    }

    @Test
    @DisplayName("AS-C-11: exactly 100 distinct ids (inclusive upper boundary) is accepted, not a 400")
    void exactly100IdsIsAccepted() {
        String ids = syntheticIds(100);
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", ids)
                .when().get(BASE)
                .then().statusCode(200);
    }

    @Test
    @DisplayName("AS-C-12: missing X-Service-Key header returns 401 with ErrorResponse shape")
    void missingServiceKeyReturns401() {
        given()
                .queryParam("ids", crawledAppId.toString())
                .when().get(BASE)
                .then().statusCode(401)
                .body("error", not(empty()))
                .body("message", not(empty()));
    }

    @Test
    @DisplayName("AS-C-13: wrong X-Service-Key value returns 401")
    void wrongServiceKeyReturns401() {
        given().header(SERVICE_KEY_HEADER, "wrong-value")
                .queryParam("ids", crawledAppId.toString())
                .when().get(BASE)
                .then().statusCode(401)
                .body("error", not(empty()))
                .body("message", not(empty()));
    }

    @Test
    @DisplayName("AS244-C-01: an id backed by a snapshot with a populated companyLogoUrl returns 200 with "
            + "items[0].companyLogoUrl equal to the seeded value, company/jobTitle unaffected (regression of AS-C-01)")
    void idWithPopulatedLogoReturnsLogo() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", crawledWithLogoAppId.toString())
                .when().get(BASE)
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].companyLogoUrl", equalTo("https://cdn.example/stark.png"))
                .body("items[0].company", equalTo("Stark Industries"))
                .body("items[0].jobTitle", equalTo("Platform Engineer"));
    }

    @Test
    @DisplayName("AS244-C-02: an id backed by a snapshot with companyLogoUrl=null returns 200 with "
            + "items[0].companyLogoUrl explicitly JSON null (key present, value null), company/jobTitle populated")
    void idWithNullLogoReturnsNullLogo() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", crawledAppId.toString())
                .when().get(BASE)
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0]", org.hamcrest.Matchers.hasKey("companyLogoUrl"))
                .body("items[0].companyLogoUrl", nullValue())
                .body("items[0].company", equalTo("Acme Corp"))
                .body("items[0].jobTitle", equalTo("Senior Backend Java Developer"));
    }

    @Test
    @DisplayName("AS244-C-03: a pre-fix snapshot (company=null, companyLogoUrl=null, jobTitle populated) "
            + "returns 200 with that exact S4 combination, the entry still included")
    void preFixSnapshotShapeStillIncluded() {
        given().header(SERVICE_KEY_HEADER, VALID_KEY)
                .queryParam("ids", preFixSnapshotAppId.toString())
                .when().get(BASE)
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].jobTitle", equalTo("Legacy Crawled Role"))
                .body("items[0].company", equalTo(""))
                .body("items[0].companyLogoUrl", nullValue());
    }

    private static String syntheticIds(int count) {
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(UUID.randomUUID().toString());
        }
        return ids.stream().collect(Collectors.joining(","));
    }
}
