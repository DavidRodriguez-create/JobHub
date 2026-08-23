package com.davidcreate.jobhub.job.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Story #430 (ADR 0025 D5, PDA section G): the update endpoint's 400s. Every case runs
 * against the SHARED {@code valid-target-co} baseline, which no passing case ever mutates -
 * every assertion re-reads it afterward to confirm it is untouched.
 */
@QuarkusTest
@TestSecurity(user = "43000000-9994-0000-0000-000000000001", roles = "admin")
@DisplayName("Admin Company Validation Component Tests (Story #430)")
class AdminCompanyValidationComponentTest {

    private static final String COMPANIES = "/jobs/admin/companies";
    private static final String VALID_TARGET_ID = "43000000-0000-0000-0000-000000000018";

    private static Map<String, Object> validBaselineBody() {
        Map<String, Object> b = new HashMap<>();
        b.put("website", "https://valid-target.example.com");
        b.put("industry", "Education");
        b.put("size", "11-50");
        b.put("headquarters", "Porto, Portugal");
        b.put("description", "A fixture company used only as the shared validation baseline.");
        b.put("tags", List.of("edtech", "b2c"));
        b.put("logoUrl", "https://example.com/logos/valid-target.png");
        return b;
    }

    private void assertBaselineUnchanged() {
        given().when().get(COMPANIES + "/" + VALID_TARGET_ID)
                .then()
                .statusCode(200)
                .body("website", equalTo("https://valid-target.example.com"))
                .body("industry", equalTo("Education"))
                .body("headquarters", equalTo("Porto, Portugal"))
                .body("tags", equalTo(List.of("edtech", "b2c")));
    }

    private void assertRejected(Map<String, Object> body) {
        given().contentType(ContentType.JSON).body(body)
                .when().put(COMPANIES + "/" + VALID_TARGET_ID)
                .then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
        assertBaselineUnchanged();
    }

    // ── QAE-430-V-01 (AC-430-31) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-V-01: a tag violating lowercase-kebab-case grammar is rejected")
    void badTagPatternRejected() {
        Map<String, Object> body = validBaselineBody();
        body.put("tags", List.of("Remote First"));
        assertRejected(body);
    }

    // ── QAE-430-V-02 (AC-430-32) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-V-02: a duplicate tag is rejected, not silently deduplicated")
    void duplicateTagRejected() {
        Map<String, Object> body = validBaselineBody();
        body.put("tags", List.of("remote-first", "remote-first"));
        assertRejected(body);
    }

    // ── QAE-430-V-03 (AC-430-33) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-V-03: more than 20 tags is rejected")
    void tooManyTagsRejected() {
        Map<String, Object> body = validBaselineBody();
        body.put("tags", IntStream.range(0, 21).mapToObj(i -> "tag-" + i).toList());
        assertRejected(body);
    }

    // ── QAE-430-V-04 (AC-430-34) ────────────────────────────────────────────────

    static Stream<Arguments> overLengthFields() {
        return Stream.of(
                Arguments.of("website", "https://example.com/" + "a".repeat(2040)),
                Arguments.of("logoUrl", "https://example.com/" + "a".repeat(2040)),
                Arguments.of("industry", "x".repeat(81)),
                Arguments.of("size", "x".repeat(41)),
                Arguments.of("headquarters", "x".repeat(121)),
                Arguments.of("description", "x".repeat(2001)));
    }

    @ParameterizedTest(name = "{0} over its length limit is rejected")
    @MethodSource("overLengthFields")
    @DisplayName("QAE-430-V-04: an over-length field is rejected, other valid fields not partially applied")
    void overLengthFieldRejected(String field, String overLengthValue) {
        Map<String, Object> body = validBaselineBody();
        body.put(field, overLengthValue);
        assertRejected(body);
    }

    @Test
    @DisplayName("QAE-430-V-04(g): a single over-length tag is rejected")
    void overLengthTagRejected() {
        Map<String, Object> body = validBaselineBody();
        body.put("tags", List.of("x".repeat(41)));
        assertRejected(body);
    }

    // ── QAE-430-V-05 (AC-430-35) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-V-05: a non-URI website is rejected")
    void nonUriWebsiteRejected() {
        Map<String, Object> body = validBaselineBody();
        body.put("website", "not a url");
        assertRejected(body);
    }

    @Test
    @DisplayName("QAE-430-V-05: a non-URI logoUrl is rejected")
    void nonUriLogoUrlRejected() {
        Map<String, Object> body = validBaselineBody();
        body.put("logoUrl", "not a url");
        assertRejected(body);
    }

    // ── QAE-430-V-06 (AC-430-36) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-V-06: a validation failure is all-or-nothing - six valid fields NOT partially applied")
    void allOrNothingAcrossWholeRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("website", "https://changed-target.example.com");
        body.put("industry", "Changed Industry");
        body.put("size", "201-500");
        body.put("headquarters", "Changed City, Country");
        body.put("description", "Changed description.");
        body.put("tags", List.of("Bad Tag")); // the one invalid field
        body.put("logoUrl", "https://changed-target.example.com/logo.png");

        given().contentType(ContentType.JSON).body(body)
                .when().put(COMPANIES + "/" + VALID_TARGET_ID)
                .then().statusCode(400);

        assertBaselineUnchanged();
        given().when().get(COMPANIES + "/" + VALID_TARGET_ID)
                .then()
                .statusCode(200)
                .body("size", equalTo("11-50"))
                .body("description", equalTo("A fixture company used only as the shared validation baseline."))
                .body("logoUrl", equalTo("https://example.com/logos/valid-target.png"));
    }
}
