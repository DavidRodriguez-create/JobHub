package com.davidcreate.jobhub.application.component_tests;

import com.davidcreate.jobhub.application.application.port.out.ApplicationRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * GA-APP-18: Repository crash returns 500.
 *
 * Separate @QuarkusTest class because mixing @InjectMock with the real DevServices
 * DB in the same class prevents the real DB bean from being used by other tests.
 */
@QuarkusTest
@DisplayName("Internal Application Resource Failure Tests")
class InternalApplicationResourceFailureComponentTest {

    private static final String BASE_STALE = "/internal/applications/stale";
    private static final String SERVICE_KEY = "test-internal-key";

    @InjectMock
    ApplicationRepository applicationRepository;

    // ── GA-APP-18 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-18: Repository crash on stale query returns 500")
    void getStale_repositoryCrash() {
        when(applicationRepository.findNonTerminalStaleApplications(any()))
                .thenThrow(new RuntimeException("simulated DB crash"));

        given()
            .header("X-Service-Key", SERVICE_KEY)
            .queryParam("days", 14)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(500);
    }
}
