package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.TwoFactorStatusProfile;
import com.davidcreate.jobhub.job.component_tests.support.WireMockAuthServerResource;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Component tests for {@code GET /jobs/admin/triggers/status}'s per-caller 2FA
 * signal (ADR 0019), stubbing auth-service's
 * {@code GET /auth/internal/users/{userId}/two-factor} via WireMock.
 * TC-384-J26..J29 (section D.2 of the QAE test-case doc).
 */
@QuarkusTest
@TestProfile(TwoFactorStatusProfile.class)
@DisplayName("Admin Trigger Status Component Tests — per-caller 2FA signal")
class AdminTriggerStatusTwoFactorComponentTest {

    private static final String STATUS = "/jobs/admin/triggers/status";

    private WireMockServer wireMock() {
        return WireMockAuthServerResource.server();
    }

    @BeforeEach
    void resetStubs() {
        wireMock().resetAll();
    }

    private void stubTwoFactorEnabled(String userId, boolean enabled) {
        wireMock().stubFor(get(urlEqualTo("/auth/internal/users/" + userId + "/two-factor"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"twoFactorEnabled\":" + enabled + "}")));
    }

    @Test
    @TestSecurity(user = "40000000-0000-0000-0000-000000000001", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "40000000-0000-0000-0000-000000000001"))
    @DisplayName("TC-384-J26/AC-01: 2FA-enrolled admin -> twoFactorRequired=true")
    void statusReportsTwoFactorRequiredTrue() {
        stubTwoFactorEnabled("40000000-0000-0000-0000-000000000001", true);

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("twoFactorRequired", equalTo(true));
    }

    @Test
    @TestSecurity(user = "40000000-0000-0000-0000-000000000002", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "40000000-0000-0000-0000-000000000002"))
    @DisplayName("TC-384-J27/AC-02: non-2FA admin -> twoFactorRequired=false")
    void statusReportsTwoFactorRequiredFalse() {
        stubTwoFactorEnabled("40000000-0000-0000-0000-000000000002", false);

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("twoFactorRequired", equalTo(false));
    }

    @Test
    @TestSecurity(user = "40000000-0000-0000-0000-000000000003", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "40000000-0000-0000-0000-000000000003"))
    @DisplayName("TC-384-J28/AC-03: per-caller, not shared — admin A sees their own state independent of admin B")
    void statusIsPerCallerNotShared() {
        String adminA = "40000000-0000-0000-0000-000000000003";
        String adminB = "40000000-0000-0000-0000-000000000004";
        stubTwoFactorEnabled(adminA, true);
        stubTwoFactorEnabled(adminB, false);

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("twoFactorRequired", equalTo(true));
    }

    @Test
    @TestSecurity(user = "40000000-0000-0000-0000-000000000004", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "40000000-0000-0000-0000-000000000004"))
    @DisplayName("TC-384-J28/AC-03: the other admin (B) sees false, independent of admin A")
    void statusIsPerCallerNotSharedOtherAdmin() {
        String adminA = "40000000-0000-0000-0000-000000000003";
        String adminB = "40000000-0000-0000-0000-000000000004";
        stubTwoFactorEnabled(adminA, true);
        stubTwoFactorEnabled(adminB, false);

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("twoFactorRequired", equalTo(false));
    }

    @Test
    @TestSecurity(user = "40000000-0000-0000-0000-000000000005", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "40000000-0000-0000-0000-000000000005"))
    @DisplayName("TC-384-J29/AC-04: triggerEnabled reflects only deployment config, independent of the caller's 2FA state")
    void triggerEnabledIsIndependentOfCallerTwoFactorState() {
        stubTwoFactorEnabled("40000000-0000-0000-0000-000000000005", true);

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("triggerEnabled", equalTo(true))
                .body("twoFactorRequired", equalTo(true));
    }

    @Test
    @TestSecurity(user = "40000000-0000-0000-0000-000000000006", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "40000000-0000-0000-0000-000000000006"))
    @DisplayName("TC-384-J29/AC-04: triggerEnabled=true holds for a non-2FA admin too")
    void triggerEnabledIsTrueRegardlessOfTwoFactorState() {
        stubTwoFactorEnabled("40000000-0000-0000-0000-000000000006", false);

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("triggerEnabled", equalTo(true))
                .body("twoFactorRequired", equalTo(false));
    }
}
