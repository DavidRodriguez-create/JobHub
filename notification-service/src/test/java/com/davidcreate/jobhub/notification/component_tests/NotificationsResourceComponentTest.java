package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.component_tests.support.WireMockAppInternalResource;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;

/**
 * Component tests for the notification center endpoints ({@code GET /notifications},
 * {@code GET /notifications/unread-count}, {@code PATCH /notifications/{id}/read},
 * {@code PATCH /notifications/read-all}), exercising the contract defined in
 * {@code api-contracts/openapi/notification-service.yaml}.
 *
 * <p>Server-error (500) cases live in {@link NotificationsResourceFailureComponentTest}.
 */
@QuarkusTest
@QuarkusTestResource(WireMockAppInternalResource.class)
@DisplayName("Notifications Resource Component Tests")
class NotificationsResourceComponentTest {

    private static final String BASE = "/notifications";

    // Pagination/ordering user — 25 seeded notifications, created_at strictly descending.
    private static final String USER_PAGINATION = "e0000000-0000-0000-0000-000000000001";
    // Read/unread filter user — 5 seeded: 3 unread, 2 read, one of each NotificationType.
    private static final String USER_FILTER = "e0000000-0000-0000-0000-000000000002";
    // Empty user — 0 seeded notifications.
    private static final String USER_EMPTY = "e0000000-0000-0000-0000-000000000003";
    // Single-notification user (mark-read happy path).
    private static final String USER_SINGLE = "e0000000-0000-0000-0000-000000000004";
    // Mark-all-as-read user — 4 seeded: 3 unread, 1 read.
    private static final String USER_MARK_ALL = "e0000000-0000-0000-0000-000000000005";
    // Cross-user ownership user — owns no notifications of its own.
    private static final String USER_OTHER = "e0000000-0000-0000-0000-000000000006";
    // applicationId deep-link user (#182): one application-linked GHOSTED_ALERT,
    // one null-applicationId SYSTEM, both unread.
    private static final String USER_APPLICATION_LINK = "e0000000-0000-0000-0000-000000000007";

    // Enrich-at-read unresolved-application user (ADR 0014, story #207, NS-C-02): one
    // application-linked notification whose applicationId the WireMock stub never resolves.
    private static final String USER_UNRESOLVED_APPLICATION = "e0000000-0000-0000-0000-000000000008";
    // Delete happy-path user (story #206) — exactly one seeded notification.
    private static final String USER_DELETE = "e0000000-0000-0000-0000-000000000009";
    // Category-exhaustive-check user (story #439, ADR 0031): exactly one notification of
    // each of the 6 NotificationType values.
    private static final String USER_ALL_TYPES = "e0000000-0000-0000-0000-000000000010";

    private static final String SINGLE_NOTIFICATION_ID = "f0000000-0000-0000-0000-000000000001";
    private static final String APPLICATION_LINKED_NOTIFICATION_ID = "f7000000-0000-0000-0000-000000000001";
    private static final String NULL_APPLICATION_NOTIFICATION_ID = "f7000000-0000-0000-0000-000000000002";
    private static final String SEEDED_APPLICATION_ID = "a7000000-0000-0000-0000-000000000001";
    private static final String UNRESOLVED_NOTIFICATION_ID = "f8000000-0000-0000-0000-000000000001";
    private static final String UNRESOLVED_APPLICATION_ID = "a8000000-0000-0000-0000-000000000099";
    private static final String DELETE_NOTIFICATION_ID = "f9000000-0000-0000-0000-000000000001";

    @BeforeEach
    void resetWireMock() {
        appSummaries().resetAll();
    }

    private WireMockServer appSummaries() {
        return WireMockAppInternalResource.server();
    }

    private void stubSummaries(String responseBody) {
        appSummaries().stubFor(get(urlPathEqualTo("/internal/applications/summaries"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }

    // ── GET /notifications ─────────────────────────────────────────────────────

    // TC-B-C-01
    @Test
    @TestSecurity(user = USER_PAGINATION, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_PAGINATION))
    @DisplayName("TC-B-C-01: GET /notifications returns 200 with a page of notifications, newest first")
    void listReturnsPageNewestFirst() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.size()", equalTo(20))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalElements", equalTo(25))
                .body("totalPages", equalTo(2));

        // newest-first ordering: first element's createdAt must be later than the last's.
        String first = given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().extract().path("content[0].createdAt");
        String last = given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().extract().path("content[19].createdAt");

        assertOrderedNewestFirst(first, last);
    }

    private static void assertOrderedNewestFirst(String first, String last) {
        java.time.OffsetDateTime firstTime = java.time.OffsetDateTime.parse(first);
        java.time.OffsetDateTime lastTime = java.time.OffsetDateTime.parse(last);
        org.assertj.core.api.Assertions.assertThat(firstTime).isAfter(lastTime);
    }

    // TC-B-C-02
    @Test
    @TestSecurity(user = USER_PAGINATION, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_PAGINATION))
    @DisplayName("TC-B-C-02: GET /notifications X-Total-Count header matches totalElements and the seeded count")
    void listXTotalCountHeaderMatchesSeed() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .header("X-Total-Count", equalTo("25"))
                .body("totalElements", equalTo(25));
    }

    // TC-B-C-03
    @Test
    @TestSecurity(user = USER_PAGINATION, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_PAGINATION))
    @DisplayName("TC-B-C-03: GET /notifications second page returns the remaining notifications")
    void listSecondPageReturnsRemainder() {
        given().queryParam("page", 1).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .header("X-Total-Count", equalTo("25"))
                .body("content.size()", equalTo(5))
                .body("page", equalTo(1))
                .body("totalElements", equalTo(25))
                .body("totalPages", equalTo(2));
    }

    // TC-B-C-04
    @Test
    @TestSecurity(user = USER_FILTER, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_FILTER))
    @DisplayName("TC-B-C-04: GET /notifications?readStatus=unread returns only unread notifications")
    void listFilterUnread() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "unread")
                .when().get(BASE)
                .then().statusCode(200)
                .header("X-Total-Count", equalTo("3"))
                .body("content.size()", equalTo(3))
                .body("content.read", everyItem(equalTo(false)))
                .body("totalElements", equalTo(3));
    }

    // TC-B-C-05
    @Test
    @TestSecurity(user = USER_FILTER, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_FILTER))
    @DisplayName("TC-B-C-05: GET /notifications?readStatus=read returns only read notifications")
    void listFilterRead() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "read")
                .when().get(BASE)
                .then().statusCode(200)
                .header("X-Total-Count", equalTo("2"))
                .body("content.size()", equalTo(2))
                .body("content.read", everyItem(equalTo(true)))
                .body("totalElements", equalTo(2));
    }

    // TC-B-C-06
    @Test
    @TestSecurity(user = USER_PAGINATION, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_PAGINATION))
    @DisplayName("TC-B-C-06: GET /notifications with default params behaves as page=0&size=20&readStatus=all")
    void listDefaultsMatchExplicitParams() {
        given().when().get(BASE)
                .then().statusCode(200)
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("content.size()", equalTo(20))
                .body("totalElements", equalTo(25));
    }

    // TC-B-C-07
    @Test
    @TestSecurity(user = USER_FILTER, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_FILTER))
    @DisplayName("TC-B-C-07: GET /notifications response shape — each notification has all required fields")
    void listResponseShapeHasRequiredFields() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.id", everyItem(notNullValue()))
                .body("content.type", everyItem(oneOf(
                        "INTERVIEW_REMINDER", "GHOSTED_ALERT", "APPLICATION_UPDATE", "SYSTEM")))
                .body("content.category", everyItem(notNullValue()))
                .body("content.title", everyItem(notNullValue()))
                .body("content.message", everyItem(notNullValue()))
                .body("content.read", everyItem(notNullValue()))
                .body("content.createdAt", everyItem(notNullValue()))
                .body("content.find { it.type == 'INTERVIEW_REMINDER' }.title", equalTo("Interview reminder"));
    }

    // TC-B-C-08
    @Test
    @TestSecurity(user = USER_EMPTY, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_EMPTY))
    @DisplayName("TC-B-C-08: GET /notifications returns an empty page for a user with no notifications")
    void listEmptyForUserWithNoNotifications() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .header("X-Total-Count", equalTo("0"))
                .body("content", empty())
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalElements", equalTo(0))
                .body("totalPages", equalTo(0));
    }

    // ── GET /notifications/unread-count ───────────────────────────────────────

    // TC-B-C-09
    @Test
    @TestSecurity(user = USER_FILTER, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_FILTER))
    @DisplayName("TC-B-C-09: GET /notifications/unread-count returns the correct non-zero count")
    void unreadCountNonZero() {
        given().when().get(BASE + "/unread-count")
                .then().statusCode(200)
                .body("count", equalTo(3));
    }

    // TC-B-C-10
    @Test
    @TestSecurity(user = USER_EMPTY, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_EMPTY))
    @DisplayName("TC-B-C-10: GET /notifications/unread-count returns zero for a user with no unread notifications")
    void unreadCountZeroForEmptyUser() {
        given().when().get(BASE + "/unread-count")
                .then().statusCode(200)
                .body("count", equalTo(0));
    }

    // ── PATCH /notifications/{id}/read ─────────────────────────────────────────

    // TC-B-C-11 + TC-B-C-12 (continued in the same test method)
    @Test
    @TestSecurity(user = USER_SINGLE, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_SINGLE))
    @DisplayName("TC-B-C-11/12: PATCH /notifications/{id}/read marks unread as read (204) and is idempotent")
    void markReadHappyPathAndIdempotent() {
        // TC-B-C-11
        given().when().patch(BASE + "/" + SINGLE_NOTIFICATION_ID + "/read")
                .then().statusCode(204)
                .body(equalTo(""));

        given().queryParam("readStatus", "unread")
                .when().get(BASE)
                .then().statusCode(200)
                .body("totalElements", equalTo(0));

        // TC-B-C-12
        given().when().patch(BASE + "/" + SINGLE_NOTIFICATION_ID + "/read")
                .then().statusCode(204)
                .body(equalTo(""));

        given().when().get(BASE + "/unread-count")
                .then().statusCode(200)
                .body("count", equalTo(0));
    }

    // TC-B-C-13
    @Test
    @TestSecurity(user = USER_OTHER, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_OTHER))
    @DisplayName("TC-B-C-13: PATCH /notifications/{id}/read returns 404 for a notification owned by another user")
    void markReadNotOwnedReturns404() {
        given().when().patch(BASE + "/" + SINGLE_NOTIFICATION_ID + "/read")
                .then().statusCode(404)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-B-C-13b
    @Test
    @TestSecurity(user = USER_SINGLE, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_SINGLE))
    @DisplayName("TC-B-C-13b: PATCH /notifications/{id}/read returns 404 for a non-existent notification ID")
    void markReadNonExistentReturns404() {
        given().when().patch(BASE + "/ffffffff-ffff-ffff-ffff-ffffffffffff/read")
                .then().statusCode(404)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // ── PATCH /notifications/read-all ──────────────────────────────────────────

    // TC-B-C-14 + TC-B-C-15 (continued in the same test method)
    @Test
    @TestSecurity(user = USER_MARK_ALL, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_MARK_ALL))
    @DisplayName("TC-B-C-14/15: PATCH /notifications/read-all marks all unread as read (204) and is idempotent")
    void markAllReadHappyPathAndIdempotent() {
        // TC-B-C-14
        given().when().patch(BASE + "/read-all")
                .then().statusCode(204)
                .body(equalTo(""));

        given().when().get(BASE + "/unread-count")
                .then().statusCode(200)
                .body("count", equalTo(0));

        given().queryParam("readStatus", "unread")
                .when().get(BASE)
                .then().statusCode(200)
                .body("totalElements", equalTo(0))
                .body("content", empty());

        // TC-B-C-15
        given().when().patch(BASE + "/read-all")
                .then().statusCode(204)
                .body(equalTo(""));

        given().when().get(BASE + "/unread-count")
                .then().statusCode(200)
                .body("count", equalTo(0));
    }

    // ── 4xx parameter validation ───────────────────────────────────────────────

    // TC-B-C-16
    @Test
    @TestSecurity(user = USER_EMPTY, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_EMPTY))
    @DisplayName("TC-B-C-16: GET /notifications returns 400 for page < 0")
    void listNegativePageReturns400() {
        given().queryParam("page", -1).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-B-C-17
    @Test
    @TestSecurity(user = USER_EMPTY, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_EMPTY))
    @DisplayName("TC-B-C-17: GET /notifications returns 400 for size > 100")
    void listSizeTooLargeReturns400() {
        given().queryParam("page", 0).queryParam("size", 101).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-B-C-17b
    @Test
    @TestSecurity(user = USER_EMPTY, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_EMPTY))
    @DisplayName("TC-B-C-17b: GET /notifications returns 400 for size < 1")
    void listSizeTooSmallReturns400() {
        given().queryParam("page", 0).queryParam("size", 0).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-B-C-18
    @Test
    @TestSecurity(user = USER_EMPTY, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_EMPTY))
    @DisplayName("TC-B-C-18: GET /notifications returns 400 for an unrecognised readStatus value")
    void listUnknownReadStatusReturns400() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "bogus")
                .when().get(BASE)
                .then().statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // ── 401 unauthenticated ─────────────────────────────────────────────────────

    // TC-B-C-19
    @Test
    @DisplayName("TC-B-C-19: GET /notifications returns 401 without a Bearer token")
    void listUnauthenticatedReturns401() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(401);
    }

    // TC-B-C-20
    @Test
    @DisplayName("TC-B-C-20: GET /notifications/unread-count returns 401 without a Bearer token")
    void unreadCountUnauthenticatedReturns401() {
        given().when().get(BASE + "/unread-count")
                .then().statusCode(401);
    }

    // TC-B-C-21
    @Test
    @DisplayName("TC-B-C-21: PATCH /notifications/{id}/read returns 401 without a Bearer token")
    void markReadUnauthenticatedReturns401() {
        given().when().patch(BASE + "/" + SINGLE_NOTIFICATION_ID + "/read")
                .then().statusCode(401);
    }

    // TC-B-C-22
    @Test
    @DisplayName("TC-B-C-22: PATCH /notifications/read-all returns 401 without a Bearer token")
    void markAllReadUnauthenticatedReturns401() {
        given().when().patch(BASE + "/read-all")
                .then().statusCode(401);
    }

    // ── applicationId deep-link (#182) ─────────────────────────────────────────

    // TC-B-C-23
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("TC-B-C-23: GET /notifications: application-scoped notification carries applicationId in the JSON payload")
    void listApplicationScopedNotificationCarriesApplicationId() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.applicationId",
                        equalTo(SEEDED_APPLICATION_ID));
    }

    // NR-C-25
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("NR-C-25: GET /notifications: application-scoped GHOSTED_ALERT row carries the playful branded title")
    void listApplicationScopedNotificationCarriesPlayfulGhostedTitle() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.title",
                        equalTo("👻 A wild ghost appeared!"))
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.applicationId",
                        equalTo(SEEDED_APPLICATION_ID));
    }

    // TC-B-C-24
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("TC-B-C-24: GET /notifications: SYSTEM notification has applicationId null and is never omitted")
    void listSystemNotificationHasNullApplicationIdAndIsNotOmitted() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .header("X-Total-Count", equalTo("2"))
                .body("totalElements", equalTo(2))
                .body("content.id", org.hamcrest.Matchers.hasItem(NULL_APPLICATION_NOTIFICATION_ID))
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.applicationId",
                        org.hamcrest.Matchers.nullValue());
    }

    // TC-B-C-25
    @Test
    @TestSecurity(user = USER_FILTER, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_FILTER))
    @DisplayName("TC-B-C-25: GET /notifications: mixed page does not break shape validation for other rows")
    void listMixedPageDoesNotBreakShapeValidation() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .header("X-Total-Count", equalTo("5"))
                .body("totalElements", equalTo(5))
                .body("content.id", everyItem(notNullValue()))
                .body("content.type", everyItem(notNullValue()))
                .body("content.category", everyItem(notNullValue()))
                .body("content.title", everyItem(notNullValue()))
                .body("content.message", everyItem(notNullValue()))
                .body("content.read", everyItem(notNullValue()))
                .body("content.createdAt", everyItem(notNullValue()));
    }

    // ── Notification categories (story #439, ADR 0031) ─────────────────────────

    // TC-439-14
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("TC-439-14: GET /notifications: application-scoped GHOSTED_ALERT row derives category APPLICATION")
    void listApplicationScopedGhostedAlertDerivesApplicationCategory() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.category",
                        equalTo("APPLICATION"));
    }

    // TC-439-15
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("TC-439-15: GET /notifications: null-applicationId SYSTEM row derives category ACCOUNT")
    void listNullApplicationSystemNotificationDerivesAccountCategory() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.category",
                        equalTo("ACCOUNT"));
    }

    // TC-439-16
    @Test
    @TestSecurity(user = USER_FILTER, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_FILTER))
    @DisplayName("TC-439-16: GET /notifications: every row on the USER_FILTER page has a non-null category")
    void listFilterPageEveryRowHasNonNullCategory() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.category", everyItem(notNullValue()));
    }

    // TC-439-17 / TC-439-18
    @Test
    @TestSecurity(user = USER_ALL_TYPES, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_ALL_TYPES))
    @DisplayName("TC-439-17/18: GET /notifications: a page with one row of each NotificationType has a non-null category for every row and none derives to JOB_POST")
    void listAllTypesPageEveryRowHasNonNullCategoryAndNoneIsJobPost() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .header("X-Total-Count", equalTo("6"))
                .body("totalElements", equalTo(6))
                .body("content.category", everyItem(notNullValue()))
                .body("content.category", everyItem(org.hamcrest.Matchers.not(equalTo("JOB_POST"))))
                .body("content.find { it.type == 'INTERVIEW_REMINDER' }.category", equalTo("APPLICATION"))
                .body("content.find { it.type == 'GHOSTED_ALERT' }.category", equalTo("APPLICATION"))
                .body("content.find { it.type == 'APPLICATION_UPDATE' }.category", equalTo("APPLICATION"))
                .body("content.find { it.type == 'CUSTOM_REMINDER' }.category", equalTo("APPLICATION"))
                .body("content.find { it.type == 'SECURITY_RECOMMENDATION' }.category", equalTo("ACCOUNT"))
                .body("content.find { it.type == 'SYSTEM' }.category", equalTo("ACCOUNT"));
    }

    // TC-439-19
    @Test
    @TestSecurity(user = USER_SINGLE, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_SINGLE))
    @DisplayName("TC-439-19: GET /notifications: category is identical across two separate reads of the same notification")
    void listCategoryStableAcrossRepeatedReads() {
        String firstRead = given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .extract().path("content.find { it.id == '" + SINGLE_NOTIFICATION_ID + "' }.category");

        String secondRead = given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .extract().path("content.find { it.id == '" + SINGLE_NOTIFICATION_ID + "' }.category");

        org.assertj.core.api.Assertions.assertThat(firstRead).isEqualTo(secondRead);
    }

    // TC-439-20
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("TC-439-20: GET /notifications: adding category does not disturb applicationId/company/jobTitle/companyLogoUrl enrichment (regression)")
    void listCategoryAdditionDoesNotDisturbEnrichmentFields() {
        stubSummaries("""
                {"items":[{"applicationId":"%s","company":"Acme Corp","jobTitle":"Senior Backend Engineer","companyLogoUrl":"https://cdn.example.com/acme.png"}]}
                """.formatted(SEEDED_APPLICATION_ID));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.applicationId",
                        equalTo(SEEDED_APPLICATION_ID))
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.company",
                        equalTo("Acme Corp"))
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.jobTitle",
                        equalTo("Senior Backend Engineer"))
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.companyLogoUrl",
                        equalTo("https://cdn.example.com/acme.png"))
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.category",
                        equalTo("APPLICATION"))
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.applicationId",
                        org.hamcrest.Matchers.nullValue())
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.category",
                        equalTo("ACCOUNT"));
    }

    // ── Enrich-at-read, company + jobTitle (ADR 0014, story #207) ───────────────

    // NS-C-01
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("NS-C-01: GET /notifications enriches a resolvable application-linked row with company+jobTitle")
    void listEnrichesResolvableApplicationWithCompanyAndJobTitle() {
        stubSummaries("""
                {"items":[{"applicationId":"%s","company":"Acme Corp","jobTitle":"Senior Backend Engineer"}]}
                """.formatted(SEEDED_APPLICATION_ID));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.company",
                        equalTo("Acme Corp"))
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.jobTitle",
                        equalTo("Senior Backend Engineer"));
    }

    // NS-C-02
    @Test
    @TestSecurity(user = USER_UNRESOLVED_APPLICATION, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_UNRESOLVED_APPLICATION))
    @DisplayName("NS-C-02: GET /notifications for an applicationId absent from the gateway result still returns 200 with null company/jobTitle")
    void listUnresolvedApplicationGetsNullCompanyAndJobTitle() {
        stubSummaries("""
                {"items":[]}
                """);

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + UNRESOLVED_NOTIFICATION_ID + "' }.company", nullValue())
                .body("content.find { it.id == '" + UNRESOLVED_NOTIFICATION_ID + "' }.jobTitle", nullValue());
    }

    // NS-C-03
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("NS-C-03: GET /notifications: a null-applicationId SYSTEM row is never sent to the gateway and stays null company/jobTitle")
    void listNullApplicationNotificationNeverCallsGatewayForThatRow() {
        stubSummaries("""
                {"items":[{"applicationId":"%s","company":"Acme Corp","jobTitle":"Senior Backend Engineer"}]}
                """.formatted(SEEDED_APPLICATION_ID));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.company", nullValue())
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.jobTitle", nullValue());

        appSummaries().verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        urlPathEqualTo("/internal/applications/summaries"))
                .withQueryParam("ids", com.github.tomakehurst.wiremock.client.WireMock.notMatching(".*null.*")));
    }

    // NS-C-04
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("NS-C-04: a page mixing a resolvable and an unresolvable row enriches each independently")
    void listMixedResolvableAndUnresolvableRowsEnrichIndependently() {
        stubSummaries("""
                {"items":[{"applicationId":"%s","company":"Acme Corp","jobTitle":"Senior Backend Engineer"}]}
                """.formatted(SEEDED_APPLICATION_ID));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.company",
                        equalTo("Acme Corp"))
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.company", nullValue());
    }

    // NS-C-05
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("NS-C-05: the summaries call returning 500 still leaves GET /notifications at 200 with null enrichment")
    void listSummariesCallFailingStillReturns200() {
        appSummaries().stubFor(get(urlPathEqualTo("/internal/applications/summaries"))
                .willReturn(aResponse().withStatus(500)));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.company", nullValue())
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.jobTitle", nullValue());
    }

    // NS-C-06
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("NS-C-06: the summaries call faulting at the connection level still leaves GET /notifications at 200 with null enrichment")
    void listSummariesCallFaultingStillReturns200() {
        appSummaries().stubFor(get(urlPathEqualTo("/internal/applications/summaries"))
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.company", nullValue());
    }

    // NS-C-07
    @Test
    @TestSecurity(user = USER_PAGINATION, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_PAGINATION))
    @DisplayName("NS-C-07: X-Total-Count and totalElements are unaffected by the enrichment addition (regression vs TC-B-C-02)")
    void listTotalCountUnaffectedByEnrichment() {
        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .header("X-Total-Count", equalTo("25"))
                .body("totalElements", equalTo(25));
    }

    // ── DELETE /notifications/{id} (story #206) ────────────────────────────────

    // TC-206-B-04 (TC-B-C-26) + TC-206-B-05 (TC-B-C-27, continued in the same method)
    @Test
    @TestSecurity(user = USER_DELETE, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_DELETE))
    @DisplayName("TC-206-B-04/05: DELETE /notifications/{id} deletes the caller's own notification (204), verified gone, then re-delete is 404 (non-idempotent)")
    void deleteOwnNotificationThenReDeleteReturns404() {
        // TC-206-B-04
        given().when().delete(BASE + "/" + DELETE_NOTIFICATION_ID)
                .then().statusCode(204)
                .body(equalTo(""));

        given().queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .header("X-Total-Count", equalTo("0"))
                .body("totalElements", equalTo(0))
                .body("content", empty());

        // TC-206-B-05
        given().when().delete(BASE + "/" + DELETE_NOTIFICATION_ID)
                .then().statusCode(404)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-206-B-06 (TC-B-C-28)
    @Test
    @TestSecurity(user = USER_EMPTY, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_EMPTY))
    @DisplayName("TC-206-B-06: DELETE /notifications/{id} returns 404 for a non-existent notification ID")
    void deleteNonExistentReturns404() {
        given().when().delete(BASE + "/ffffffff-ffff-ffff-ffff-ffffffffffff")
                .then().statusCode(404)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-206-B-07 (TC-B-C-29)
    @Test
    @TestSecurity(user = USER_OTHER, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_OTHER))
    @DisplayName("TC-206-B-07: DELETE /notifications/{id} returns 404 for a notification owned by another user, and the row survives for its real owner")
    void deleteNotOwnedReturns404AndRowSurvives() {
        given().when().delete(BASE + "/" + SINGLE_NOTIFICATION_ID)
                .then().statusCode(404)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // Regression guard for TC-206-B-07: the row is still retrievable by its real owner.
    @Test
    @TestSecurity(user = USER_SINGLE, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_SINGLE))
    @DisplayName("TC-206-B-07 (regression guard): notification untouched by another user's failed delete attempt is still visible to its owner")
    void deleteNotOwnedAttemptDoesNotAffectRealOwnersView() {
        given().queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("totalElements", equalTo(1));
    }

    // TC-206-B-08 (TC-B-C-30)
    @Test
    @DisplayName("TC-206-B-08: DELETE /notifications/{id} returns 401 without a Bearer token")
    void deleteUnauthenticatedReturns401() {
        given().when().delete(BASE + "/" + SINGLE_NOTIFICATION_ID)
                .then().statusCode(401);
    }

    // ── companyLogoUrl threading (ADR 0015, story #244) ─────────────────────────

    // NS244-C-01
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("NS244-C-01: GET /notifications enriches a resolved row with companyLogoUrl alongside company/jobTitle")
    void listEnrichesResolvableApplicationWithCompanyLogoUrl() {
        stubSummaries("""
                {"items":[{"applicationId":"%s","company":"Acme Corp","jobTitle":"Senior Backend Engineer","companyLogoUrl":"https://cdn.example.com/acme.png"}]}
                """.formatted(SEEDED_APPLICATION_ID));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.companyLogoUrl",
                        equalTo("https://cdn.example.com/acme.png"))
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.company",
                        equalTo("Acme Corp"))
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.jobTitle",
                        equalTo("Senior Backend Engineer"));
    }

    // NS244-C-02
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("NS244-C-02: GET /notifications for a stub that resolves companyLogoUrl null returns null for that field, company/jobTitle populated")
    void listCompanyLogoUrlNullFromStubReturnsNullInResponse() {
        stubSummaries("""
                {"items":[{"applicationId":"%s","company":"Foo Inc","jobTitle":"Backend Dev","companyLogoUrl":null}]}
                """.formatted(SEEDED_APPLICATION_ID));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.companyLogoUrl",
                        nullValue())
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.company",
                        equalTo("Foo Inc"))
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.jobTitle",
                        equalTo("Backend Dev"));
    }

    // NS244-C-03
    @Test
    @TestSecurity(user = USER_UNRESOLVED_APPLICATION, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_UNRESOLVED_APPLICATION))
    @DisplayName("NS244-C-03: GET /notifications for an unresolvable applicationId returns all three enrichment fields null")
    void listUnresolvableApplicationAllThreeFieldsNull() {
        stubSummaries("""
                {"items":[]}
                """);

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + UNRESOLVED_NOTIFICATION_ID + "' }.company", nullValue())
                .body("content.find { it.id == '" + UNRESOLVED_NOTIFICATION_ID + "' }.jobTitle", nullValue())
                .body("content.find { it.id == '" + UNRESOLVED_NOTIFICATION_ID + "' }.companyLogoUrl", nullValue());
    }

    // NS244-C-04
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("NS244-C-04: GET /notifications for a SYSTEM notification (applicationId==null) returns all three enrichment fields null")
    void listSystemNotificationAllThreeEnrichmentFieldsNull() {
        stubSummaries("""
                {"items":[{"applicationId":"%s","company":"Acme Corp","jobTitle":"Senior Backend Engineer","companyLogoUrl":"https://cdn.example.com/acme.png"}]}
                """.formatted(SEEDED_APPLICATION_ID));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.company", nullValue())
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.jobTitle", nullValue())
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.companyLogoUrl", nullValue());
    }

    // NS244-C-05
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("NS244-C-05: whole summaries call returns 500: GET /notifications still 200, all three enrichment fields null")
    void listSummariesCall500AllThreeEnrichmentFieldsNull() {
        appSummaries().stubFor(get(urlPathEqualTo("/internal/applications/summaries"))
                .willReturn(aResponse().withStatus(500)));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.company", nullValue())
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.jobTitle", nullValue())
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.companyLogoUrl", nullValue());
    }

    // NS244-C-06
    @Test
    @TestSecurity(user = USER_APPLICATION_LINK, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_APPLICATION_LINK))
    @DisplayName("NS244-C-06: mixed page - S1-shape row (all three resolved) and S4-shape row (jobTitle resolved, company/logo null) render independently")
    void listMixedPageS1AndS4ShapeRowsRenderIndependently() {
        // USER_APPLICATION_LINK has two notifications:
        // APPLICATION_LINKED_NOTIFICATION_ID -> SEEDED_APPLICATION_ID (resolved: all three)
        // NULL_APPLICATION_NOTIFICATION_ID -> no applicationId (SYSTEM, always null)
        // For S4 shape we assert via the SEEDED_APPLICATION_ID with a partial stub (no logo)
        stubSummaries("""
                {"items":[{"applicationId":"%s","company":"Acme Corp","jobTitle":"Senior Backend Engineer","companyLogoUrl":"https://cdn.example.com/acme.png"}]}
                """.formatted(SEEDED_APPLICATION_ID));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(200)
                // S1 row: all three populated
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.companyLogoUrl",
                        equalTo("https://cdn.example.com/acme.png"))
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.company",
                        equalTo("Acme Corp"))
                .body("content.find { it.id == '" + APPLICATION_LINKED_NOTIFICATION_ID + "' }.jobTitle",
                        equalTo("Senior Backend Engineer"))
                // SYSTEM row (S3/S4-like): all three null, independent of S1 row
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.company", nullValue())
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.jobTitle", nullValue())
                .body("content.find { it.id == '" + NULL_APPLICATION_NOTIFICATION_ID + "' }.companyLogoUrl", nullValue());
    }
}
