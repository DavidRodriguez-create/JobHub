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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(WireMockAppInternalResource.class)
@DisplayName("CustomReminderResource Component Tests")
class CustomReminderResourceComponentTest {

    private static final String BASE = "/notifications/custom-reminders";
    private static final String OLD_BY_APP = "/applications";

    private static final String USER_A = "ee000000-0000-0000-0000-000000000001";
    private static final String USER_B = "ee000000-0000-0000-0000-000000000002";

    private static final String APP_1 = "ea000000-0000-0000-0000-000000000001";
    private static final String APP_2 = "ea000000-0000-0000-0000-000000000002";
    private static final String APP_NOT_OWNED = "ea000000-0000-0000-0000-000000000099";

    private static final String REMINDER_1 = "ec000000-0000-0000-0000-000000000001";
    private static final String REMINDER_2 = "ec000000-0000-0000-0000-000000000002";
    private static final String REMINDER_3 = "ec000000-0000-0000-0000-000000000003";
    private static final String REMINDER_4 = "ec000000-0000-0000-0000-000000000004";
    private static final String REMINDER_5 = "ec000000-0000-0000-0000-000000000005";
    private static final String REMINDER_6 = "ec000000-0000-0000-0000-000000000006";

    @BeforeEach
    void resetWireMock() {
        appInternal().resetAll();
    }

    private WireMockServer appInternal() {
        return WireMockAppInternalResource.server();
    }

    private void stubOwnership(String applicationId, String userId, int status) {
        appInternal().stubFor(head(urlPathEqualTo("/internal/applications/" + applicationId + "/owner/" + userId))
                .willReturn(aResponse().withStatus(status)));
    }

    private String futureIso(long secondsFromNow) {
        return Instant.now().plusSeconds(secondsFromNow).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private String pastIso(long secondsAgo) {
        return Instant.now().minusSeconds(secondsAgo).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    // CR-C-001
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-001: POST happy path returns 201 with Location header")
    void postHappyPath() {
        stubOwnership(APP_1, USER_A, 204);

        given().contentType("application/json")
                .body(Map.of(
                        "applicationId", APP_1,
                        "title", "Prep call",
                        "triggerAtUtc", futureIso(3600),
                        "channels", List.of("IN_APP"),
                        "stage", "INTERVIEW"))
                .when().post(BASE)
                .then().statusCode(201)
                .header("Location", containsString("/notifications/custom-reminders/"))
                .body("id", notNullValue())
                .body("status", equalTo("SCHEDULED"))
                .body("channels", hasSize(1))
                .body("stage", equalTo("INTERVIEW"))
                .body("title", equalTo("Prep call"));
    }

    // CR-C-002
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-002: POST 400 trigger in past")
    void postTriggerInPast() {
        given().contentType("application/json")
                .body(Map.of(
                        "applicationId", APP_1,
                        "title", "Prep call",
                        "triggerAtUtc", pastIso(60),
                        "channels", List.of("IN_APP")))
                .when().post(BASE)
                .then().statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // CR-C-003
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-003: POST 400 empty channels array")
    void postEmptyChannels() {
        given().contentType("application/json")
                .body(Map.of(
                        "applicationId", APP_1,
                        "title", "Prep call",
                        "triggerAtUtc", futureIso(3600),
                        "channels", List.of()))
                .when().post(BASE)
                .then().statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // CR-C-004
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-004: POST 400 title blank")
    void postTitleBlank() {
        given().contentType("application/json")
                .body(Map.of(
                        "applicationId", APP_1,
                        "title", "",
                        "triggerAtUtc", futureIso(3600),
                        "channels", List.of("IN_APP")))
                .when().post(BASE)
                .then().statusCode(400);
    }

    // CR-C-005
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-005: POST 400 title too long (201 chars)")
    void postTitleTooLong() {
        stubOwnership(APP_1, USER_A, 204);

        given().contentType("application/json")
                .body(Map.of(
                        "applicationId", APP_1,
                        "title", "a".repeat(201),
                        "triggerAtUtc", futureIso(3600),
                        "channels", List.of("IN_APP")))
                .when().post(BASE)
                .then().statusCode(400);
    }

    // CR-C-006
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-006: POST 404 application not owned by user")
    void postApplicationNotOwned() {
        stubOwnership(APP_NOT_OWNED, USER_A, 404);

        given().contentType("application/json")
                .body(Map.of(
                        "applicationId", APP_NOT_OWNED,
                        "title", "Prep call",
                        "triggerAtUtc", futureIso(3600),
                        "channels", List.of("IN_APP")))
                .when().post(BASE)
                .then().statusCode(404)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // CR-C-007
    @Test
    @DisplayName("CR-C-007: POST 401 no bearer token")
    void postUnauthenticated() {
        given().contentType("application/json")
                .body(Map.of(
                        "applicationId", APP_1,
                        "title", "Prep call",
                        "triggerAtUtc", futureIso(3600),
                        "channels", List.of("IN_APP")))
                .when().post(BASE)
                .then().statusCode(401);
    }

    // CR-C-008
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-008: POST duplicate channels normalised in response")
    void postDuplicateChannelsNormalised() {
        stubOwnership(APP_1, USER_A, 204);

        given().contentType("application/json")
                .body(Map.of(
                        "applicationId", APP_1,
                        "title", "Prep call",
                        "triggerAtUtc", futureIso(3600),
                        "channels", List.of("IN_APP", "IN_APP")))
                .when().post(BASE)
                .then().statusCode(201)
                .body("channels", hasSize(1))
                .body("channels[0]", equalTo("IN_APP"));
    }

    // CR-C-010
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-010: GET single happy path 200")
    void getHappyPath() {
        given().when().get(BASE + "/" + REMINDER_1)
                .then().statusCode(200)
                .body("id", equalTo(REMINDER_1))
                .body("status", equalTo("SCHEDULED"))
                .body("applicationId", equalTo(APP_1));
    }

    // CR-C-011
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-011: GET 404 wrong user (not owner)")
    void getWrongUser() {
        given().when().get(BASE + "/" + REMINDER_4)
                .then().statusCode(404)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // CR-C-012
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-012: GET 404 unknown id")
    void getUnknownId() {
        given().when().get(BASE + "/" + UUID.randomUUID())
                .then().statusCode(404);
    }

    // CR-C-013
    @Test
    @DisplayName("CR-C-013: GET 401 unauthenticated")
    void getUnauthenticated() {
        given().when().get(BASE + "/" + REMINDER_1).then().statusCode(401);
    }

    // CR-C-020 / NS-C-10
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-020/NS-C-10: PUT happy path partial (body-only) update 200, title is preserved from create time")
    void putPartialUpdate() {
        given().contentType("application/json")
                .body(Map.of("note", "Updated note"))
                .when().put(BASE + "/" + REMINDER_1)
                .then().statusCode(200)
                .body("note", equalTo("Updated note"))
                .body("title", equalTo("Prep notes A1"));
    }

    // CR-C-021 / NS-C-10 (updated for story #211: IN_APP is force-added, channels EMAIL -> [IN_APP, EMAIL])
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-021/NS-C-10: PUT happy path full (body-only) update 200, title is preserved from create time")
    void putFullUpdate() {
        given().contentType("application/json")
                .body(Map.of(
                        "note", "Brand new note",
                        "triggerAtUtc", futureIso(99999),
                        "channels", List.of("EMAIL"),
                        "stage", "OFFER"))
                .when().put(BASE + "/" + REMINDER_2)
                .then().statusCode(200)
                .body("title", equalTo("Prep notes A2"))
                .body("note", equalTo("Brand new note"))
                .body("channels", hasSize(2))
                .body("channels", org.hamcrest.Matchers.containsInAnyOrder("IN_APP", "EMAIL"))
                .body("stage", equalTo("OFFER"));
    }

    // NS-C-11
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("NS-C-11: PUT body that still includes a title key is accepted (not 400), the value is silently ignored")
    void putBodyWithTitleKeyIsAcceptedAndIgnored() {
        given().contentType("application/json")
                .body(Map.of("title", "Attempted new title", "note", "Note via legacy client"))
                .when().put(BASE + "/" + REMINDER_1)
                .then().statusCode(200)
                .body("title", equalTo("Prep notes A1"))
                .body("note", equalTo("Note via legacy client"));
    }

    // NS-C-12
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("NS-C-12: PUT updating only triggerAtUtc leaves note and title at their prior values")
    void putScheduleOnlyUpdateLeavesNoteAndTitleUnchanged() {
        String newTrigger = futureIso(123456);

        given().contentType("application/json")
                .body(Map.of("triggerAtUtc", newTrigger))
                .when().put(BASE + "/" + REMINDER_3)
                .then().statusCode(200)
                .body("triggerAtUtc", equalTo(newTrigger))
                .body("note", equalTo((Object) null))
                .body("title", equalTo("Prep notes A3"));
    }

    // NS-C-13
    @Test
    @DisplayName("NS-C-13: generated UpdateCustomReminderRequest contract model has no title property")
    void updateCustomReminderRequestHasNoTitleProperty() {
        java.util.List<String> methodNames = java.util.Arrays.stream(
                        com.davidcreate.jobhub.notification.contract.model.UpdateCustomReminderRequest.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        org.assertj.core.api.Assertions.assertThat(methodNames)
                .doesNotContain("getTitle", "setTitle", "title");
    }

    // CR-C-022
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-022: PUT 400 trigger moved to past")
    void putTriggerMovedToPast() {
        given().contentType("application/json")
                .body(Map.of("triggerAtUtc", pastIso(60)))
                .when().put(BASE + "/" + REMINDER_1)
                .then().statusCode(400);
    }

    // CR-C-023
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-023: PUT 400 channels set to empty")
    void putChannelsEmpty() {
        given().contentType("application/json")
                .body(Map.of("channels", List.of()))
                .when().put(BASE + "/" + REMINDER_1)
                .then().statusCode(400);
    }

    // CR-C-024
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-024: PUT 409 reminder is FIRED")
    void putReminderFired() {
        given().contentType("application/json")
                .body(Map.of("title", "New"))
                .when().put(BASE + "/" + REMINDER_5)
                .then().statusCode(409)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // CR-C-025
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-025: PUT 409 reminder is CANCELLED")
    void putReminderCancelled() {
        given().contentType("application/json")
                .body(Map.of("title", "New"))
                .when().put(BASE + "/" + REMINDER_6)
                .then().statusCode(409);
    }

    // CR-C-026
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-026: PUT 404 not owner")
    void putNotOwner() {
        given().contentType("application/json")
                .body(Map.of("title", "New"))
                .when().put(BASE + "/" + REMINDER_4)
                .then().statusCode(404);
    }

    // CR-C-027
    @Test
    @DisplayName("CR-C-027: PUT 401 unauthenticated")
    void putUnauthenticated() {
        given().contentType("application/json")
                .body(Map.of("title", "New"))
                .when().put(BASE + "/" + REMINDER_1)
                .then().statusCode(401);
    }

    // CR-C-030
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-030: DELETE happy path 204 (SCHEDULED -> CANCELLED)")
    void deleteHappyPath() {
        given().when().delete(BASE + "/" + REMINDER_2)
                .then().statusCode(204);

        given().when().get(BASE + "/" + REMINDER_2)
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    // CR-C-031
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-031: DELETE idempotent on already-CANCELLED")
    void deleteIdempotentOnCancelled() {
        given().when().delete(BASE + "/" + REMINDER_6)
                .then().statusCode(204);
    }

    // CR-C-032
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-032: DELETE 409 reminder is FIRED")
    void deleteReminderFired() {
        given().when().delete(BASE + "/" + REMINDER_5)
                .then().statusCode(409);
    }

    // CR-C-033
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-033: DELETE 404 not owner")
    void deleteNotOwner() {
        given().when().delete(BASE + "/" + REMINDER_4)
                .then().statusCode(404);
    }

    // CR-C-034
    @Test
    @DisplayName("CR-C-034: DELETE 401 unauthenticated")
    void deleteUnauthenticated() {
        given().when().delete(BASE + "/" + REMINDER_1).then().statusCode(401);
    }

    // CR-C-040
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-040: GET list-mine default returns only SCHEDULED asc by trigger")
    void listMineDefault() {
        given().when().get(BASE)
                .then().statusCode(200)
                .body("content.findAll { it.status != 'SCHEDULED' }", hasSize(0));
    }

    // CR-C-041
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-041: GET list-mine includeFired=true returns all statuses")
    void listMineIncludeFired() {
        given().when().get(BASE + "?includeFired=true")
                .then().statusCode(200)
                .body("content.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(3));
    }

    // CR-C-042
    @Test
    @TestSecurity(user = "ee000000-0000-0000-0000-000000000099", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "ee000000-0000-0000-0000-000000000099"))
    @DisplayName("CR-C-042: GET list-mine empty list returns 200 with empty content")
    void listMineEmpty() {
        given().when().get(BASE)
                .then().statusCode(200)
                .body("content", hasSize(0));
    }

    // CR-C-043
    @Test
    @DisplayName("CR-C-043: GET list-mine 401 unauthenticated")
    void listMineUnauthenticated() {
        given().when().get(BASE).then().statusCode(401);
    }

    // CR-NS-C50 (replaces CR-C-050)
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-NS-C50: GET filtered-by-application happy path via query param")
    void listFilteredByApplicationHappyPath() {
        given().when().get(BASE + "?applicationId=" + APP_2)
                .then().statusCode(200)
                .body("content.size()", equalTo(1))
                .body("content.findAll { it.applicationId != '" + APP_2 + "' }", hasSize(0));
    }

    // CR-NS-C51 (replaces CR-C-051, per addendum A1: empty list, not 404, for non-owner application)
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-NS-C51: GET filtered non-owner/absent application returns 200 + empty list (addendum A1)")
    void listFilteredNonOwnerReturnsEmptyList() {
        given().when().get(BASE + "?applicationId=" + APP_NOT_OWNED)
                .then().statusCode(200)
                .body("content", hasSize(0));
    }

    // CR-NS-C52 (replaces CR-C-052)
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-NS-C52: GET filtered includeFired=true returns fired and cancelled too")
    void listFilteredIncludeFired() {
        given().when().get(BASE + "?applicationId=" + APP_1 + "&includeFired=true")
                .then().statusCode(200)
                .body("content.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(4))
                .body("content.findAll { it.status == 'FIRED' }.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
                .body("content.findAll { it.status == 'CANCELLED' }.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
    }

    // CR-NS-C53 (replaces CR-C-053)
    @Test
    @DisplayName("CR-NS-C53: GET filtered-by-application 401 unauthenticated")
    void listFilteredUnauthenticated() {
        given().when().get(BASE + "?applicationId=" + APP_1).then().statusCode(401);
    }

    // CR-NS-C54 (new)
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-NS-C54: GET omitting applicationId still returns the caller's full list")
    void listAllWhenApplicationIdOmitted() {
        given().when().get(BASE)
                .then().statusCode(200)
                .body("content.findAll { it.applicationId == '" + APP_1 + "' }.size()",
                        org.hamcrest.Matchers.greaterThanOrEqualTo(2))
                .body("content.findAll { it.applicationId == '" + APP_2 + "' }.size()", equalTo(1));
    }

    // CR-NS-C55 (new)
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-NS-C55: old /applications/{id}/custom-reminders path is no longer served (404)")
    void oldByApplicationPathNoLongerServed() {
        given().when().get(OLD_BY_APP + "/" + APP_1 + "/custom-reminders")
                .then().statusCode(404);
    }

    // CR-NS-C56 (new)
    @Test
    @TestSecurity(user = USER_B, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_B))
    @DisplayName("CR-NS-C56: applicationId filter is owner-scoped even when the application has reminders for another user")
    void listFilteredOwnerScopedAcrossUsers() {
        given().when().get(BASE + "?applicationId=" + APP_1)
                .then().statusCode(200)
                .body("content", hasSize(0));
    }

    // CR-C-090 (story #211: IN_APP-always invariant)
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-090: POST channels=[EMAIL] returns 201 with channels=[IN_APP, EMAIL]")
    void postEmailOnlyForceAddsInApp() {
        stubOwnership(APP_1, USER_A, 204);

        given().contentType("application/json")
                .body(Map.of(
                        "applicationId", APP_1,
                        "title", "Prep call",
                        "triggerAtUtc", futureIso(3600),
                        "channels", List.of("EMAIL")))
                .when().post(BASE)
                .then().statusCode(201)
                .body("channels", hasSize(2))
                .body("channels", org.hamcrest.Matchers.containsInAnyOrder("IN_APP", "EMAIL"));
    }

    // CR-C-091 (story #211: empty channels still rejected after force-add logic)
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-091: POST channels=[] still returns 400")
    void postEmptyChannelsStillRejected() {
        given().contentType("application/json")
                .body(Map.of(
                        "applicationId", APP_1,
                        "title", "Prep call",
                        "triggerAtUtc", futureIso(3600),
                        "channels", List.of()))
                .when().post(BASE)
                .then().statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // CR-C-092 (story #211: IN_APP-always invariant)
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-092: PUT channels=[EMAIL] returns 200 with channels=[IN_APP, EMAIL]")
    void putEmailOnlyForceAddsInApp() {
        given().contentType("application/json")
                .body(Map.of("channels", List.of("EMAIL")))
                .when().put(BASE + "/" + REMINDER_3)
                .then().statusCode(200)
                .body("channels", hasSize(2))
                .body("channels", org.hamcrest.Matchers.containsInAnyOrder("IN_APP", "EMAIL"));
    }

    // CR-C-093 (story #211: channels omitted on PUT keeps existing set, still containing IN_APP)
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-093: PUT with channels omitted (null) keeps existing channels including IN_APP")
    void putChannelsNullKeepsExisting() {
        given().contentType("application/json")
                .body(Map.of("note", "Just a note update"))
                .when().put(BASE + "/" + REMINDER_1)
                .then().statusCode(200)
                .body("channels", hasSize(1))
                .body("channels", org.hamcrest.Matchers.containsInAnyOrder("IN_APP"));
    }

    // CR-C-094 (story #211: empty channels still rejected on PUT after force-add logic)
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-094: PUT channels=[] still returns 400")
    void putEmptyChannelsStillRejected() {
        given().contentType("application/json")
                .body(Map.of("channels", List.of()))
                .when().put(BASE + "/" + REMINDER_3)
                .then().statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }
}
