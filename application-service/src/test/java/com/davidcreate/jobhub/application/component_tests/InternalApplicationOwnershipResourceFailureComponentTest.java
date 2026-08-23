package com.davidcreate.jobhub.application.component_tests;

import com.davidcreate.jobhub.application.application.port.in.ApplicationUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OWN-C-010: Repository crash on ownership check returns 500.
 *
 * Separate @QuarkusTest class because mixing @InjectMock with the real DevServices
 * DB in the same class prevents the real DB bean from being used by other tests
 * (per CLAUDE.md rule).
 */
@QuarkusTest
@DisplayName("Internal Application Ownership Resource Failure Tests")
class InternalApplicationOwnershipResourceFailureComponentTest {

    private static final String BASE = "/internal/applications";
    private static final String SERVICE_KEY = "test-internal-key";

    @InjectMock
    ApplicationUseCase applicationUseCase;

    // ── OWN-C-010 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OWN-C-010: Use-case crash on ownership check returns 500")
    void head_useCaseCrash_returns500() {
        when(applicationUseCase.isOwnedByUser(any(), any()))
                .thenThrow(new RuntimeException("simulated DB crash"));

        given()
            .header("X-Service-Key", SERVICE_KEY)
        .when()
            .head(BASE + "/f0000000-0000-0000-0000-000000000099/owner/fa000000-0000-0000-0000-000000000001")
        .then()
            .statusCode(500);
    }
}
