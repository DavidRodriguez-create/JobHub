package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Component tests for GET/PUT /auth/account/apply-profile (story #336, ADR 0022),
 * exercising BE-C1..BE-C13 against real DevServices Postgres. Server-error (500)
 * cases live in {@link ApplyProfileResourceFailureComponentTest}.
 */
@QuarkusTest
@DisplayName("Apply Profile Resource Component Tests — BE-C1..BE-C13")
class ApplyProfileResourceComponentTest {

    private static final String BASE = "/account/apply-profile";
    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";
    private static final String VERIFY_EMAIL_PATH = ACCOUNT + "/verify-email";

    @InjectMock
    VerificationNotifier notifier;

    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        Mockito.reset(notifier);
        uniqueEmail = "apply-profile-" + UUID.randomUUID() + "@example.com";
    }

    private static Map<String, Object> fullProfileBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("workAuthorization", "US Citizen");
        body.put("requiresSponsorship", false);
        body.put("noticePeriod", "2 weeks");
        body.put("salaryExpectation", "$120k-$140k");
        body.put("currentLocation", "Madrid, Spain");
        body.put("willingToRelocate", true);
        body.put("linkedinUrl", "https://linkedin.com/in/alice");
        body.put("githubUrl", "https://github.com/alice");
        body.put("portfolioUrl", "https://alice.dev");
        body.put("languages", List.of("English (native)", "Spanish (C1)"));
        body.put("roomToGrow", "Grow into a staff engineer role");
        return body;
    }

    @Nested
    @DisplayName("GET /auth/account/apply-profile")
    class Get {

        @Test
        @DisplayName("BE-C1: never-saved user -> 200, every field null, updatedAt null (AC1)")
        void neverSavedReturnsAllNull() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("workAuthorization", nullValue())
                    .body("requiresSponsorship", nullValue())
                    .body("noticePeriod", nullValue())
                    .body("salaryExpectation", nullValue())
                    .body("currentLocation", nullValue())
                    .body("willingToRelocate", nullValue())
                    .body("linkedinUrl", nullValue())
                    .body("githubUrl", nullValue())
                    .body("portfolioUrl", nullValue())
                    .body("languages", nullValue())
                    .body("roomToGrow", nullValue())
                    .body("updatedAt", nullValue());
        }

        @Test
        @DisplayName("BE-C11: no Authorization header -> 401 (AC12)")
        void noTokenUnauthorized() {
            given().when().get(BASE).then().statusCode(401);
        }

        @Test
        @DisplayName("BE-C11: garbage Bearer token -> 401 (AC12)")
        void garbageTokenUnauthorized() {
            given().header("Authorization", "Bearer not-a-real-jwt")
                    .when().get(BASE)
                    .then().statusCode(401);
        }
    }

    @Nested
    @DisplayName("PUT /auth/account/apply-profile")
    class Put {

        @Test
        @DisplayName("BE-C2: first-ever save round-trips every field, updatedAt non-null (AC2)")
        void firstSaveRoundTripsEveryField() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(fullProfileBody())
                    .when().put(BASE)
                    .then()
                    .statusCode(200)
                    .body("workAuthorization", equalTo("US Citizen"))
                    .body("requiresSponsorship", equalTo(false))
                    .body("noticePeriod", equalTo("2 weeks"))
                    .body("salaryExpectation", equalTo("$120k-$140k"))
                    .body("currentLocation", equalTo("Madrid, Spain"))
                    .body("willingToRelocate", equalTo(true))
                    .body("linkedinUrl", equalTo("https://linkedin.com/in/alice"))
                    .body("githubUrl", equalTo("https://github.com/alice"))
                    .body("portfolioUrl", equalTo("https://alice.dev"))
                    .body("languages", equalTo(List.of("English (native)", "Spanish (C1)")))
                    .body("roomToGrow", equalTo("Grow into a staff engineer role"))
                    .body("updatedAt", notNullValue());

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("workAuthorization", equalTo("US Citizen"))
                    .body("languages", equalTo(List.of("English (native)", "Spanish (C1)")))
                    .body("updatedAt", notNullValue());
        }

        @Test
        @DisplayName("BE-C3: partial save leaves unsent fields null (AC3)")
        void partialSaveLeavesUnsentFieldsNull() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            Map<String, Object> partial = Map.of(
                    "workAuthorization", "US Citizen",
                    "currentLocation", "Madrid, Spain");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(partial)
                    .when().put(BASE)
                    .then()
                    .statusCode(200)
                    .body("workAuthorization", equalTo("US Citizen"))
                    .body("currentLocation", equalTo("Madrid, Spain"))
                    .body("noticePeriod", nullValue())
                    .body("salaryExpectation", nullValue())
                    .body("linkedinUrl", nullValue())
                    .body("languages", nullValue());

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("workAuthorization", equalTo("US Citizen"))
                    .body("currentLocation", equalTo("Madrid, Spain"))
                    .body("noticePeriod", nullValue());
        }

        @Test
        @DisplayName("BE-C4: editing one field preserves the rest (AC4)")
        void editingOneFieldPreservesRest() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            Map<String, Object> secondPut = new HashMap<>(fullProfileBody());
            secondPut.put("noticePeriod", "Immediate");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(secondPut)
                    .when().put(BASE)
                    .then()
                    .statusCode(200)
                    .body("noticePeriod", equalTo("Immediate"))
                    .body("workAuthorization", equalTo("US Citizen"))
                    .body("salaryExpectation", equalTo("$120k-$140k"))
                    .body("currentLocation", equalTo("Madrid, Spain"));
        }

        @Test
        @DisplayName("BE-C5: clearing one field nulls only that field (AC5)")
        void clearingOneFieldNullsOnlyThatField() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            Map<String, Object> secondPut = new HashMap<>(fullProfileBody());
            secondPut.put("salaryExpectation", null);

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(secondPut)
                    .when().put(BASE)
                    .then()
                    .statusCode(200)
                    .body("salaryExpectation", nullValue())
                    .body("workAuthorization", equalTo("US Citizen"))
                    .body("currentLocation", equalTo("Madrid, Spain"));

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("salaryExpectation", nullValue())
                    .body("workAuthorization", equalTo("US Citizen"));
        }

        @Test
        @DisplayName("BE-C6: clearing every field -> all null, updatedAt non-null, distinct from never-saved (AC6)")
        void clearingEveryFieldKeepsUpdatedAt() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            Map<String, Object> allBlank = new HashMap<>();
            allBlank.put("workAuthorization", null);
            allBlank.put("requiresSponsorship", null);
            allBlank.put("noticePeriod", null);
            allBlank.put("salaryExpectation", null);
            allBlank.put("currentLocation", null);
            allBlank.put("willingToRelocate", null);
            allBlank.put("linkedinUrl", null);
            allBlank.put("githubUrl", null);
            allBlank.put("portfolioUrl", null);
            allBlank.put("languages", List.of());
            allBlank.put("roomToGrow", null);

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(allBlank)
                    .when().put(BASE)
                    .then()
                    .statusCode(200)
                    .body("workAuthorization", nullValue())
                    .body("languages", nullValue())
                    .body("roomToGrow", nullValue())
                    .body("updatedAt", notNullValue());
        }

        @Test
        @DisplayName("BE-C7: 400 on over-length roomToGrow, atomic (nothing persisted) (AC7)")
        void overLengthRoomToGrowRejectedAtomically() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            Map<String, Object> invalid = new HashMap<>(fullProfileBody());
            invalid.put("roomToGrow", "x".repeat(2001));

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(invalid)
                    .when().put(BASE)
                    .then().statusCode(400);

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("roomToGrow", equalTo("Grow into a staff engineer role"));
        }

        @Test
        @DisplayName("BE-C8: 400 on over-length workAuthorization (201 chars), atomic (AC7)")
        void overLengthWorkAuthorizationRejected() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            Map<String, Object> invalid = new HashMap<>(fullProfileBody());
            invalid.put("workAuthorization", "x".repeat(201));

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(invalid)
                    .when().put(BASE)
                    .then().statusCode(400);

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("workAuthorization", equalTo("US Citizen"));
        }

        @Test
        @DisplayName("BE-C8: 400 on over-length noticePeriod (101 chars), atomic (AC7)")
        void overLengthNoticePeriodRejected() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            Map<String, Object> invalid = new HashMap<>(fullProfileBody());
            invalid.put("noticePeriod", "x".repeat(101));

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(invalid)
                    .when().put(BASE)
                    .then().statusCode(400);

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("noticePeriod", equalTo("2 weeks"));
        }

        @Test
        @DisplayName("BE-C9: 400 on 21 languages, previous list preserved (AC8)")
        void tooManyLanguagesRejectedAtomically() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            Map<String, Object> initial = new HashMap<>(fullProfileBody());
            initial.put("languages", List.of("English", "Spanish", "French"));

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(initial)
                    .when().put(BASE).then().statusCode(200);

            List<String> tooMany = java.util.stream.IntStream.range(0, 21)
                    .mapToObj(i -> "Lang" + i)
                    .toList();
            Map<String, Object> invalid = new HashMap<>(fullProfileBody());
            invalid.put("languages", tooMany);

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(invalid)
                    .when().put(BASE)
                    .then().statusCode(400);

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("languages", equalTo(List.of("English", "Spanish", "French")));
        }

        @Test
        @DisplayName("BE-C10: 400 on malformed linkedinUrl, valid currentLocation change in same "
                + "request does not silently persist (AC9)")
        void malformedLinkedinUrlRejectedWithoutPartialSave() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            Map<String, Object> invalid = new HashMap<>(fullProfileBody());
            invalid.put("linkedinUrl", "not a link");
            invalid.put("currentLocation", "Lisbon, Portugal");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(invalid)
                    .when().put(BASE)
                    .then().statusCode(400);

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("currentLocation", equalTo("Madrid, Spain"))
                    .body("linkedinUrl", equalTo("https://linkedin.com/in/alice"));
        }

        @Test
        @DisplayName("BE-C10: 400 on malformed githubUrl (AC9)")
        void malformedGithubUrlRejected() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            Map<String, Object> invalid = new HashMap<>(fullProfileBody());
            invalid.put("githubUrl", "not a link");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(invalid)
                    .when().put(BASE)
                    .then().statusCode(400);

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("githubUrl", equalTo("https://github.com/alice"));
        }

        @Test
        @DisplayName("BE-C10: 400 on malformed portfolioUrl (AC9)")
        void malformedPortfolioUrlRejected() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            Map<String, Object> invalid = new HashMap<>(fullProfileBody());
            invalid.put("portfolioUrl", "not a link");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(invalid)
                    .when().put(BASE)
                    .then().statusCode(400);

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("portfolioUrl", equalTo("https://alice.dev"));
        }

        @Test
        @DisplayName("BE-C12: no Authorization header -> 401, no persistence side-effect (AC12)")
        void unauthenticatedPutHasNoSideEffect() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            given().contentType("application/json")
                    .body(fullProfileBody())
                    .when().put(BASE)
                    .then().statusCode(401);

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("workAuthorization", equalTo("US Citizen"));
        }

        @Test
        @DisplayName("BE-C13: isolation from #296 — PATCH /account leaves the apply profile unaffected (AC13)")
        void patchAccountDoesNotAffectApplyProfile() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            String updatedAtBefore = given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then().statusCode(200)
                    .extract().jsonPath().getString("updatedAt");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(Map.of("firstName", "Alicia"))
                    .when().patch(ACCOUNT)
                    .then().statusCode(200);

            given().header("Authorization", "Bearer " + token)
                    .when().get(BASE)
                    .then()
                    .statusCode(200)
                    .body("workAuthorization", equalTo("US Citizen"))
                    .body("updatedAt", equalTo(updatedAtBefore));
        }

        @Test
        @DisplayName("BE-C13: isolation from #296 — saving the apply profile leaves AccountResponse unaffected (AC13)")
        void saveApplyProfileDoesNotAffectAccount() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            String firstNameBefore = given().header("Authorization", "Bearer " + token)
                    .when().get(ACCOUNT)
                    .then().statusCode(200)
                    .extract().jsonPath().getString("firstName");

            given().header("Authorization", "Bearer " + token)
                    .contentType("application/json").body(fullProfileBody())
                    .when().put(BASE).then().statusCode(200);

            given().header("Authorization", "Bearer " + token)
                    .when().get(ACCOUNT)
                    .then()
                    .statusCode(200)
                    .body("firstName", equalTo(firstNameBefore))
                    .body("email", equalTo(uniqueEmail))
                    .body("emailVerified", equalTo(true));
        }

        @Test
        @DisplayName("no Authorization header -> 401")
        void noTokenUnauthorized() {
            given().contentType("application/json")
                    .body(fullProfileBody())
                    .when().put(BASE)
                    .then().statusCode(401);
        }
    }

    // --- helpers (mirrors AuthResourceComponentTest) ---

    private void registerAndVerify(String email, String password) {
        given().contentType("application/json")
                .body(Map.of("firstName", "Test", "lastName", "User",
                        "email", email, "password", password))
                .when().post(REGISTER)
                .then().statusCode(201);

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        Mockito.verify(notifier).sendActionCode(anyString(), eq(VerificationAction.VERIFY_EMAIL), codeCap.capture());
        String code = codeCap.getValue();

        given().contentType("application/json")
                .body(Map.of("email", email, "code", code))
                .when().post(VERIFY_EMAIL_PATH)
                .then().statusCode(200);

        Mockito.reset(notifier);
    }

    private String login(String email, String password) {
        return given().contentType("application/json")
                .body(Map.of("email", email, "password", password))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("token");
    }
}
