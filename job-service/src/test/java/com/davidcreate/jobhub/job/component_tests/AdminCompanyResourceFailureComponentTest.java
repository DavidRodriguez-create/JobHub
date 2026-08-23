package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.domain.port.out.CompanyRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Story #430 (QAE-430-F-01..03): 5xx paths for the three admin company endpoints,
 * {@code @InjectMock} on {@link CompanyRepository}. Its own top-level {@code @QuarkusTest}
 * class per CLAUDE.md's component-test rules (never mixed with a real-DB class).
 */
@QuarkusTest
@DisplayName("Admin Company Resource Failure Component Tests (Story #430)")
class AdminCompanyResourceFailureComponentTest {

    private static final String COMPANIES = "/jobs/admin/companies";

    @InjectMock
    CompanyRepository companyRepository;

    // ── QAE-430-F-01 ─────────────────────────────────────────────────────────────

    @Test
    @TestSecurity(user = "43000000-9992-0000-0000-000000000001", roles = "admin")
    @DisplayName("QAE-430-F-01: browse - a repository failure surfaces as 500 {error, message}")
    void browseRepositoryFailureSurfacesAs500() {
        when(companyRepository.search(any())).thenThrow(new RuntimeException("Simulated DB connection crash"));

        given().when().get(COMPANIES)
                .then()
                .statusCode(500)
                .body("error", equalTo("Internal Server Error"))
                .body("message", notNullValue());
    }

    // ── QAE-430-F-02 ─────────────────────────────────────────────────────────────

    @Test
    @TestSecurity(user = "43000000-9992-0000-0000-000000000002", roles = "admin")
    @DisplayName("QAE-430-F-02: read-one - a repository failure surfaces as 500 {error, message}")
    void readOneRepositoryFailureSurfacesAs500() {
        when(companyRepository.findCompanyById(any()))
                .thenThrow(new RuntimeException("Simulated DB connection crash"));

        given().when().get(COMPANIES + "/" + UUID.randomUUID())
                .then()
                .statusCode(500)
                .body("error", equalTo("Internal Server Error"))
                .body("message", notNullValue());
    }

    // ── QAE-430-F-03 ─────────────────────────────────────────────────────────────

    @Test
    @TestSecurity(user = "43000000-9992-0000-0000-000000000003", roles = "admin")
    @DisplayName("QAE-430-F-03: update - a repository failure surfaces as 500 {error, message}")
    void updateRepositoryFailureSurfacesAs500() {
        UUID id = UUID.randomUUID();
        when(companyRepository.findCompanyById(id)).thenReturn(Optional.of(
                com.davidcreate.jobhub.job.domain.model.Company.builder()
                        .id(id).slug("acme").name("Acme").manuallyEdited(false).build()));
        when(companyRepository.update(any())).thenThrow(new RuntimeException("Simulated DB connection crash"));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("industry", "Fintech"))
                .when().put(COMPANIES + "/" + id)
                .then()
                .statusCode(500)
                .body("error", equalTo("Internal Server Error"))
                .body("message", notNullValue());
    }
}
