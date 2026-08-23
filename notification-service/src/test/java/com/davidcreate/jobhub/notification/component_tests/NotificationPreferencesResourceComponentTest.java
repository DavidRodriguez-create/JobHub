package com.davidcreate.jobhub.notification.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Component tests for {@code GET}/{@code PUT /notifications/preferences}, exercising the
 * contract defined in {@code api-contracts/openapi/notification-service.yaml}.
 *
 * <p>Server-error (500) cases live in {@link NotificationPreferencesResourceFailureComponentTest}.
 */
@QuarkusTest
@DisplayName("Notification Preferences Resource Component Tests")
class NotificationPreferencesResourceComponentTest {

    private static final String BASE = "/notifications/preferences";

    // Seeded in db/test-seeds.sql — all defaults (true, false, true, true).
    private static final String USER_DEFAULTS = "b0000000-0000-0000-0000-000000000001";
    // Seeded in db/test-seeds.sql — all flipped from defaults (false, true, false, false).
    private static final String USER_FLIPPED = "b0000000-0000-0000-0000-000000000002";
    // Seeded in db/test-seeds.sql — all defaults, dedicated to TC-16a (isolated from
    // TC-11's mutation of USER_DEFAULTS within this same test class).
    private static final String USER_DEFAULTS_FOR_TC16A = "b0000000-0000-0000-0000-000000000003";

    // #153 regression — dedicated prefix ee100000-... (see test-seeds.sql).
    private static final String USER_153_EMAIL_TRUE = "ee100000-0000-0000-0000-000000000001";
    private static final String USER_153_EMAIL_FALSE = "ee100000-0000-0000-0000-000000000002";

    // TC-08
    @Test
    @TestSecurity(user = "c0000000-0000-0000-0000-000000000001", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "c0000000-0000-0000-0000-000000000001"))
    @DisplayName("TC-08: GET returns 200 with contract defaults for a first-time user (no row created)")
    void getReturnsDefaultsForNewUser() {
        given().when().get(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(true))
                .body("inAppNotificationsEnabled", equalTo(false))
                .body("interviewReminders", equalTo(true))
                .body("ghostedAlert", equalTo(true));

        // BR-2 — a second GET returns the identical (still-default) body, proving no row was created.
        given().when().get(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(true))
                .body("inAppNotificationsEnabled", equalTo(false))
                .body("interviewReminders", equalTo(true))
                .body("ghostedAlert", equalTo(true));
    }

    // TC-09
    @Test
    @TestSecurity(user = USER_FLIPPED, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_FLIPPED))
    @DisplayName("TC-09: GET returns 200 with the stored (non-default) preferences for an existing user")
    void getReturnsStoredPreferences() {
        given().when().get(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(false))
                .body("inAppNotificationsEnabled", equalTo(true))
                .body("interviewReminders", equalTo(false))
                .body("ghostedAlert", equalTo(false));
    }

    // TC-10
    @Test
    @TestSecurity(user = "c0000000-0000-0000-0000-000000000002", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "c0000000-0000-0000-0000-000000000002"))
    @DisplayName("TC-10: PUT with a partial body creates a row seeded with defaults for a first-time user")
    void putCreatesRowWithDefaultsForNewUser() {
        given().contentType("application/json")
                .body(Map.of("ghostedAlert", false))
                .when().put(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(true))
                .body("inAppNotificationsEnabled", equalTo(false))
                .body("interviewReminders", equalTo(true))
                .body("ghostedAlert", equalTo(false));
    }

    // TC-11
    @Test
    @TestSecurity(user = USER_DEFAULTS, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_DEFAULTS))
    @DisplayName("TC-11: PUT with a single field preserves the other stored values")
    void putPartialUpdatePreservesOtherFields() {
        given().contentType("application/json")
                .body(Map.of("ghostedAlert", false))
                .when().put(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(true))
                .body("inAppNotificationsEnabled", equalTo(false))
                .body("interviewReminders", equalTo(true))
                .body("ghostedAlert", equalTo(false));
    }

    // TC-12
    @Test
    @TestSecurity(user = "c0000000-0000-0000-0000-000000000003", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "c0000000-0000-0000-0000-000000000003"))
    @DisplayName("TC-12: PUT with all four fields fully replaces the (absent) stored state")
    void putFullUpdateReplacesAllFields() {
        given().contentType("application/json")
                .body(Map.of(
                        "weeklyDigestEmail", false,
                        "inAppNotificationsEnabled", true,
                        "interviewReminders", false,
                        "ghostedAlert", false))
                .when().put(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(false))
                .body("inAppNotificationsEnabled", equalTo(true))
                .body("interviewReminders", equalTo(false))
                .body("ghostedAlert", equalTo(false));
    }

    // TC-13
    @Test
    @TestSecurity(user = "c0000000-0000-0000-0000-000000000004", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "c0000000-0000-0000-0000-000000000004"))
    @DisplayName("TC-13: PUT then GET returns the same persisted values")
    void putThenGetPersists() {
        given().contentType("application/json")
                .body(Map.of("inAppNotificationsEnabled", true, "weeklyDigestEmail", false))
                .when().put(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(false))
                .body("inAppNotificationsEnabled", equalTo(true))
                .body("interviewReminders", equalTo(true))
                .body("ghostedAlert", equalTo(true));

        given().when().get(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(false))
                .body("inAppNotificationsEnabled", equalTo(true))
                .body("interviewReminders", equalTo(true))
                .body("ghostedAlert", equalTo(true));
    }

    // TC-14
    @Test
    @DisplayName("TC-14: GET without a Bearer token returns 401")
    void getUnauthenticated() {
        given().when().get(BASE).then().statusCode(401);
    }

    // TC-15
    @Test
    @DisplayName("TC-15: PUT without a Bearer token returns 401")
    void putUnauthenticated() {
        given().contentType("application/json")
                .body(Map.of("ghostedAlert", false))
                .when().put(BASE)
                .then().statusCode(401);
    }

    // TC-16a
    @Test
    @TestSecurity(user = USER_DEFAULTS_FOR_TC16A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_DEFAULTS_FOR_TC16A))
    @DisplayName("TC-16a: PUT with only an unrecognised field returns 400 and leaves stored preferences unchanged")
    void putWithOnlyUnrecognisedFieldReturns400() {
        given().contentType("application/json")
                .body(Map.of("smsAlerts", true))
                .when().put(BASE)
                .then().statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());

        given().when().get(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(true))
                .body("inAppNotificationsEnabled", equalTo(false))
                .body("interviewReminders", equalTo(true))
                .body("ghostedAlert", equalTo(true));
    }

    // TC-16b
    @Test
    @TestSecurity(user = USER_FLIPPED, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_FLIPPED))
    @DisplayName("TC-16b: PUT with a non-boolean value for a recognised field returns 400 and leaves stored preferences unchanged")
    void putWithNonBooleanValueReturns400() {
        given().contentType("application/json")
                .body(Map.of("ghostedAlert", "yes"))
                .when().put(BASE)
                .then().statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());

        given().when().get(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(false))
                .body("inAppNotificationsEnabled", equalTo(true))
                .body("interviewReminders", equalTo(false))
                .body("ghostedAlert", equalTo(false));
    }

    // TC-M1 (Story #136): two successive PUTs for the same user must converge on a single
    // merged preferences row. Proves the UNIQUE constraint on user_id survives migration 045
    // (which drops the redundant secondary index but keeps the unique constraint intact).
    // Uses a dedicated user (c0000000-...-000a) so no other test is affected.
    @Test
    @TestSecurity(user = "c0000000-0000-0000-0000-00000000000a", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "c0000000-0000-0000-0000-00000000000a"))
    @DisplayName("TC-M1: two PUTs for the same user converge on one merged row (unique constraint intact after migration 045)")
    void tcM1TwoPutsConvergeOnOneMergedRow() {
        given().contentType("application/json")
                .body(Map.of("weeklyDigestEmail", false))
                .when().put(BASE)
                .then().statusCode(200);

        given().contentType("application/json")
                .body(Map.of("ghostedAlert", false))
                .when().put(BASE)
                .then().statusCode(200);

        given().when().get(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(false))
                .body("ghostedAlert", equalTo(false))
                .body("inAppNotificationsEnabled", equalTo(false))
                .body("interviewReminders", equalTo(true));
    }

    // CR-153-C-001: PUT interviewReminderEmail=true (fresh user) round-trips true on GET
    @Test
    @TestSecurity(user = "ee100000-0000-0000-0000-000000000099", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "ee100000-0000-0000-0000-000000000099"))
    @DisplayName("CR-153-C-001: PUT interviewReminderEmail=true round-trips true on GET for a fresh user")
    void cr153c001PutInterviewReminderEmailTrueRoundTrips() {
        given().contentType("application/json")
                .body(Map.of("interviewReminderEmail", true))
                .when().put(BASE)
                .then().statusCode(200)
                .body("interviewReminderEmail", equalTo(true));

        given().when().get(BASE)
                .then().statusCode(200)
                .body("interviewReminderEmail", equalTo(true));
    }

    // CR-153-C-002: PUT interviewReminderEmail=false on a stored-true user round-trips false.
    // Uses a dedicated fresh user (not USER_153_EMAIL_TRUE) so this mutation does not leak
    // into CR-153-C-003 / CR-153-C-004 within the same test class (CLAUDE.md seed-isolation rule).
    @Test
    @TestSecurity(user = "ee100000-0000-0000-0000-000000000098", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "ee100000-0000-0000-0000-000000000098"))
    @DisplayName("CR-153-C-002: PUT interviewReminderEmail=false round-trips false on GET")
    void cr153c002PutInterviewReminderEmailFalseRoundTrips() {
        given().contentType("application/json")
                .body(Map.of("interviewReminderEmail", true))
                .when().put(BASE)
                .then().statusCode(200);

        given().contentType("application/json")
                .body(Map.of("interviewReminderEmail", false))
                .when().put(BASE)
                .then().statusCode(200)
                .body("interviewReminderEmail", equalTo(false));

        given().when().get(BASE)
                .then().statusCode(200)
                .body("interviewReminderEmail", equalTo(false));
    }

    // CR-153-C-003: GET always returns the interviewReminderEmail field (not null/absent)
    @Test
    @TestSecurity(user = USER_153_EMAIL_TRUE, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_153_EMAIL_TRUE))
    @DisplayName("CR-153-C-003: GET returns interviewReminderEmail field present and non-null")
    void cr153c003GetReturnsInterviewReminderEmailField() {
        given().when().get(BASE)
                .then().statusCode(200)
                .body("interviewReminderEmail", equalTo(true));
    }

    // CR-153-C-004: PUT with a partial body (no interviewReminderEmail field) preserves the stored value
    @Test
    @TestSecurity(user = USER_153_EMAIL_TRUE, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_153_EMAIL_TRUE))
    @DisplayName("CR-153-C-004: PUT partial body without interviewReminderEmail preserves stored value")
    void cr153c004PutPartialBodyPreservesInterviewReminderEmail() {
        given().contentType("application/json")
                .body(Map.of("ghostedAlert", false))
                .when().put(BASE)
                .then().statusCode(200)
                .body("interviewReminderEmail", equalTo(true))
                .body("ghostedAlert", equalTo(false));
    }

    // TC-209-C-10a (AC-10): PUT that sets only interviewReminderEmail leaves the other three
    // fields (weeklyDigestEmail, interviewReminders, ghostedAlert) exactly as a prior baseline
    // PUT established them - not reset to contract defaults.
    @Test
    @TestSecurity(user = "ee100000-0000-0000-0000-000000000097", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "ee100000-0000-0000-0000-000000000097"))
    @DisplayName("TC-209-C-10a (AC-10): PUT of only interviewReminderEmail leaves the other three fields at their established baseline")
    void tc209c10aPutOnlyInterviewReminderEmailLeavesOtherThreeFieldsAtBaseline() {
        given().contentType("application/json")
                .body(Map.of(
                        "weeklyDigestEmail", false,
                        "interviewReminders", false,
                        "ghostedAlert", false))
                .when().put(BASE)
                .then().statusCode(200);

        given().contentType("application/json")
                .body(Map.of("interviewReminderEmail", false))
                .when().put(BASE)
                .then().statusCode(200)
                .body("interviewReminderEmail", equalTo(false))
                .body("weeklyDigestEmail", equalTo(false))
                .body("interviewReminders", equalTo(false))
                .body("ghostedAlert", equalTo(false));

        given().when().get(BASE)
                .then().statusCode(200)
                .body("interviewReminderEmail", equalTo(false))
                .body("weeklyDigestEmail", equalTo(false))
                .body("interviewReminders", equalTo(false))
                .body("ghostedAlert", equalTo(false));
    }

    // TC-209-C-10b (AC-10): PUT of only weeklyDigestEmail leaves interviewReminderEmail
    // untouched - the digest toggle and the interview-email sub-toggle are independent fields.
    @Test
    @TestSecurity(user = "ee100000-0000-0000-0000-000000000096", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "ee100000-0000-0000-0000-000000000096"))
    @DisplayName("TC-209-C-10b (AC-10): PUT of only weeklyDigestEmail leaves interviewReminderEmail untouched")
    void tc209c10bPutOnlyWeeklyDigestEmailLeavesInterviewReminderEmailUntouched() {
        given().contentType("application/json")
                .body(Map.of("interviewReminderEmail", true))
                .when().put(BASE)
                .then().statusCode(200)
                .body("interviewReminderEmail", equalTo(true));

        given().contentType("application/json")
                .body(Map.of("weeklyDigestEmail", false))
                .when().put(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(false))
                .body("interviewReminderEmail", equalTo(true));

        given().when().get(BASE)
                .then().statusCode(200)
                .body("weeklyDigestEmail", equalTo(false))
                .body("interviewReminderEmail", equalTo(true));
    }

    // TC-209-C-10c (AC-10, contract-shape guard): GET returns all four preference fields as
    // top-level siblings, none nested under a shared sub-object - a cheap regression guard
    // against a future contract change implying a relationship between interviewReminderEmail
    // and weeklyDigestEmail that does not exist per the PDA's frozen semantics.
    @Test
    @TestSecurity(user = USER_DEFAULTS, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_DEFAULTS))
    @DisplayName("TC-209-C-10c (AC-10): GET exposes all four preference fields as top-level siblings")
    void tc209c10cGetExposesAllFourPreferenceFieldsAsTopLevelSiblings() {
        given().when().get(BASE)
                .then().statusCode(200)
                .body("$", org.hamcrest.Matchers.hasKey("weeklyDigestEmail"))
                .body("$", org.hamcrest.Matchers.hasKey("interviewReminders"))
                .body("$", org.hamcrest.Matchers.hasKey("interviewReminderEmail"))
                .body("$", org.hamcrest.Matchers.hasKey("ghostedAlert"));
    }
}
