package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.AdminCompanyUpdateTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Story #430 (ADR 0025 D4/D5, PDA sections C/H/I): {@code PUT /jobs/admin/companies/{id}}
 * persistence, full-replace semantics, immutability, side effects, edge/empty states and the
 * cross-cutting {@code CompanyInfo} regression. Every mutating case uses its OWN dedicated
 * seed row (mirrors {@code AdminTriggerResourceComponentTest}'s precedent) so no two write
 * cases can ever touch the same row - EXCEPT U-02/U-09/U-10, which deliberately reuse the
 * real "Stripe"/"Acme Only" fixtures per the QAE doc; this whole class therefore runs in its
 * own isolated {@code @TestProfile} container so those two mutations never leak into
 * {@code JobResourceComponentTest} / {@code CompanyResolutionComponentTest}'s own assertions
 * against the SAME rows in the shared, no-{@code @TestProfile} container.
 */
@QuarkusTest
@TestProfile(AdminCompanyUpdateTestProfile.class)
@TestSecurity(user = "43000000-9995-0000-0000-000000000001", roles = "admin")
@DisplayName("Admin Company Update Component Tests (Story #430)")
class AdminCompanyUpdateComponentTest {

    private static final String COMPANIES = "/jobs/admin/companies";

    private static final String EDIT_TARGET_ID = "43000000-0000-0000-0000-000000000013";
    private static final String CLEAR_TARGET_ID = "43000000-0000-0000-0000-000000000014";
    private static final String NOOP_TARGET_ID = "43000000-0000-0000-0000-000000000015";
    private static final String IMMUTABLE_TARGET_ID = "43000000-0000-0000-0000-000000000016";
    private static final String TAGS_CLEAR_ID = "43000000-0000-0000-0000-000000000017";
    private static final String ACME_ONLY_ID = "c4444444-c444-c444-c444-c44444444444";
    private static final String STRIPE_JOB_A_ID = "11111111-1111-1111-1111-111111111111";
    private static final String STRIPE_JOB_B_ID = "22222222-2222-2222-2222-222222222222";

    private static Map<String, Object> body(String website, String industry, String size,
            String headquarters, String description, List<String> tags, String logoUrl) {
        Map<String, Object> b = new HashMap<>();
        b.put("website", website);
        b.put("industry", industry);
        b.put("size", size);
        b.put("headquarters", headquarters);
        b.put("description", description);
        b.put("tags", tags);
        b.put("logoUrl", logoUrl);
        return b;
    }

    // ── QAE-430-U-01 (AC-430-13) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-01: a single-field-intent edit persists")
    void singleFieldIntentEditPersists() {
        Map<String, Object> update = body(
                "https://edit-target.example.com", "Fintech", "11-50", "Barcelona, Spain",
                "A fixture company awaiting its first industry/headquarters edit.",
                null, "https://example.com/logos/edit-target.png");

        given().contentType(ContentType.JSON).body(update)
                .when().put(COMPANIES + "/" + EDIT_TARGET_ID)
                .then()
                .statusCode(200)
                .body("industry", equalTo("Fintech"))
                .body("headquarters", equalTo("Barcelona, Spain"));

        given().when().get(COMPANIES + "/" + EDIT_TARGET_ID)
                .then()
                .statusCode(200)
                .body("industry", equalTo("Fintech"))
                .body("headquarters", equalTo("Barcelona, Spain"));
    }

    // ── QAE-430-U-02 (AC-430-14) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-02: the edit appears on EVERY posting for that company")
    void editAppearsOnEveryPosting() {
        String stripeId = "c1111111-c111-c111-c111-c11111111111";
        List<String> newTags = List.of("payments", "infrastructure");
        Map<String, Object> update = body(
                "https://stripe.com", "Fintech", "5001-10000", "San Francisco, United States",
                "Updated description for the every-posting proof.", newTags,
                "https://example.com/logos/stripe.png");

        given().contentType(ContentType.JSON).body(update)
                .when().put(COMPANIES + "/" + stripeId)
                .then().statusCode(200);

        given().when().get("/jobs/" + STRIPE_JOB_A_ID)
                .then().statusCode(200)
                .body("company.description", equalTo("Updated description for the every-posting proof."))
                .body("company.tags", equalTo(newTags));

        given().when().get("/jobs/" + STRIPE_JOB_B_ID)
                .then().statusCode(200)
                .body("company.description", equalTo("Updated description for the every-posting proof."))
                .body("company.tags", equalTo(newTags));
    }

    // ── QAE-430-U-03 (AC-430-15) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-03: full-replace - a field omitted from the body is cleared, not left alone")
    void omittedFieldIsClearedNotLeftAlone() {
        Map<String, Object> update = body(
                null, "Logistics", "51-200", "Rotterdam, Netherlands",
                "A fixture company whose website will be cleared by omission.",
                null, null);

        given().contentType(ContentType.JSON).body(update)
                .when().put(COMPANIES + "/" + CLEAR_TARGET_ID)
                .then()
                .statusCode(200)
                .body("website", nullValue());

        given().when().get(COMPANIES + "/" + CLEAR_TARGET_ID)
                .then().statusCode(200).body("website", nullValue());
    }

    // ── QAE-430-U-04 (AC-430-16) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-04: a genuine no-op resubmission still succeeds and still pins the record")
    void noOpResubmissionStillSucceedsAndPins() {
        var snapshot = given().when().get(COMPANIES + "/" + NOOP_TARGET_ID)
                .then().statusCode(200).extract().jsonPath();

        Map<String, Object> echoed = body(
                snapshot.getString("website"), snapshot.getString("industry"), snapshot.getString("size"),
                snapshot.getString("headquarters"), snapshot.getString("description"),
                snapshot.getList("tags", String.class), snapshot.getString("logoUrl"));

        given().contentType(ContentType.JSON).body(echoed)
                .when().put(COMPANIES + "/" + NOOP_TARGET_ID)
                .then()
                .statusCode(200)
                .body("website", equalTo(snapshot.getString("website")))
                .body("industry", equalTo(snapshot.getString("industry")))
                .body("headquarters", equalTo(snapshot.getString("headquarters")))
                .body("manuallyEdited", equalTo(true));
    }

    // ── QAE-430-U-05 (AC-430-17/40) ─────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-05: id/slug/name are immutable through this endpoint")
    void idSlugNameAreImmutable() {
        var before = given().when().get(COMPANIES + "/" + IMMUTABLE_TARGET_ID)
                .then().statusCode(200).extract().jsonPath();

        Map<String, Object> update = new HashMap<>(body(
                "https://immutable-target.example.com", "Healthcare", "501-1000",
                "Zurich, Switzerland", "Edited description", null, null));
        update.put("id", UUID.randomUUID().toString());
        update.put("slug", "made-up-slug");
        update.put("name", "Made Up Name");

        given().contentType(ContentType.JSON).body(update)
                .when().put(COMPANIES + "/" + IMMUTABLE_TARGET_ID)
                .then()
                .statusCode(200)
                .body("id", equalTo(IMMUTABLE_TARGET_ID))
                .body("slug", equalTo(before.getString("slug")))
                .body("name", equalTo(before.getString("name")));

        given().when().get(COMPANIES + "/" + IMMUTABLE_TARGET_ID)
                .then()
                .statusCode(200)
                .body("id", equalTo(IMMUTABLE_TARGET_ID))
                .body("slug", equalTo(before.getString("slug")))
                .body("name", equalTo(before.getString("name")));
    }

    // ── QAE-430-U-06 (AC-430-18) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-06: success side effects - manuallyEdited true, updatedAt advances")
    void successSideEffectsPinAndAdvanceUpdatedAt() {
        String before = given().when().get(COMPANIES + "/" + EDIT_TARGET_ID)
                .then().statusCode(200).extract().jsonPath().getString("updatedAt");

        given().contentType(ContentType.JSON)
                .body(body("https://edit-target.example.com", "Fintech", "11-50",
                        "Barcelona, Spain", null, null, null))
                .when().put(COMPANIES + "/" + EDIT_TARGET_ID)
                .then()
                .statusCode(200)
                .body("manuallyEdited", equalTo(true))
                .body("updatedAt", notNullValue());

        String after = given().when().get(COMPANIES + "/" + EDIT_TARGET_ID)
                .then().statusCode(200).extract().jsonPath().getString("updatedAt");

        org.assertj.core.api.Assertions.assertThat(Instant.parse(after))
                .isAfter(Instant.parse(before));
    }

    // ── QAE-430-U-07 (AC-430-21) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-07: editing an unknown id gets 404, no row is created")
    void editingUnknownIdIsNotFoundAndCreatesNothing() {
        String totalBefore = given().queryParam("size", 1).when().get(COMPANIES)
                .then().statusCode(200).extract().header("X-Total-Count");

        given().contentType(ContentType.JSON)
                .body(body("https://example.com", "Tech", "1-10", "Remote", null, null, null))
                .when().put(COMPANIES + "/" + UUID.randomUUID())
                .then().statusCode(404)
                .body("error", notNullValue())
                .body("message", notNullValue());

        String totalAfter = given().queryParam("size", 1).when().get(COMPANIES)
                .then().statusCode(200).extract().header("X-Total-Count");

        org.assertj.core.api.Assertions.assertThat(totalAfter).isEqualTo(totalBefore);
    }

    // ── QAE-430-U-08 (AC-430-37) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-08: an empty tags array clears all tags to null, not []")
    void emptyTagsArrayClearsToNull() {
        Map<String, Object> update = body(null, null, null, null, null, List.of(), null);

        given().contentType(ContentType.JSON).body(update)
                .when().put(COMPANIES + "/" + TAGS_CLEAR_ID)
                .then().statusCode(200).body("tags", nullValue());

        given().when().get(COMPANIES + "/" + TAGS_CLEAR_ID)
                .then().statusCode(200).body("tags", nullValue());
    }

    // ── QAE-430-U-09/U-10 (AC-430-38/39) ────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-09/U-10: a sparse company (name only) can be opened/edited normally, "
            + "and manuallyEdited flips false -> true on first curation")
    void sparseCompanyOpenedAndEditedNormally() {
        given().when().get(COMPANIES + "/" + ACME_ONLY_ID)
                .then()
                .statusCode(200)
                .body("name", equalTo("Acme Only"))
                .body("website", nullValue())
                .body("industry", nullValue())
                .body("manuallyEdited", equalTo(false));

        given().contentType(ContentType.JSON)
                .body(body("https://acme-only.example.com", "Manufacturing", "11-50",
                        "Chicago, United States", "Newly curated.", List.of("manufacturing"), null))
                .when().put(COMPANIES + "/" + ACME_ONLY_ID)
                .then()
                .statusCode(200)
                .body("website", equalTo("https://acme-only.example.com"))
                .body("industry", equalTo("Manufacturing"))
                .body("manuallyEdited", equalTo(true));
    }

    // ── QAE-430-U-11 (AC-430-40, regression) ────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-11: id/slug/name stay byte-identical across REPEATED edits")
    void idSlugNameStableAcrossRepeatedEdits() {
        var before = given().when().get(COMPANIES + "/" + NOOP_TARGET_ID)
                .then().statusCode(200).extract().jsonPath();

        given().contentType(ContentType.JSON)
                .body(body("https://noop-1.example.com", "Retail", "1001-5000",
                        "Dublin, Ireland", "First edit.", List.of("retail"), null))
                .when().put(COMPANIES + "/" + NOOP_TARGET_ID)
                .then()
                .statusCode(200)
                .body("id", equalTo(before.getString("id")))
                .body("slug", equalTo(before.getString("slug")))
                .body("name", equalTo(before.getString("name")));

        given().contentType(ContentType.JSON)
                .body(body("https://noop-2.example.com", "Retail", "1001-5000",
                        "Dublin, Ireland", "Second, different edit.", List.of("ecommerce"), null))
                .when().put(COMPANIES + "/" + NOOP_TARGET_ID)
                .then()
                .statusCode(200)
                .body("id", equalTo(before.getString("id")))
                .body("slug", equalTo(before.getString("slug")))
                .body("name", equalTo(before.getString("name")));
    }

    // ── QAE-430-U-12 (AC-430-41, regression) ────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-12: an unset field stays JSON null, never \"\" or a placeholder")
    void unsetFieldStaysJsonNullNeverEmptyStringOrPlaceholder() {
        Map<String, Object> update = body(null, "Logistics", "51-200",
                "Rotterdam, Netherlands", "desc", null, null);

        given().contentType(ContentType.JSON).body(update)
                .when().put(COMPANIES + "/" + CLEAR_TARGET_ID)
                .then()
                .statusCode(200)
                .body("website", nullValue())
                .body("$", org.hamcrest.Matchers.hasKey("website"));

        given().when().get(COMPANIES + "/" + CLEAR_TARGET_ID)
                .then()
                .statusCode(200)
                .body("website", nullValue());
    }

    // ── QAE-430-U-13 (AC-430-42) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-U-13: the company object exposes exactly the frozen CompanyInfo key set")
    void companyObjectExposesExactFrozenKeySet() {
        java.util.Set<String> expected = java.util.Set.of(
                "id", "slug", "name", "website", "industry", "size", "headquarters",
                "description", "tags", "logoUrl", "manuallyEdited", "updatedAt");

        given().when().get("/jobs/" + STRIPE_JOB_A_ID)
                .then().statusCode(200)
                .body("company.keySet()", equalTo(expected));

        given().when().get("/jobs/" + STRIPE_JOB_B_ID)
                .then().statusCode(200)
                .body("company.keySet()", equalTo(expected));
    }
}
