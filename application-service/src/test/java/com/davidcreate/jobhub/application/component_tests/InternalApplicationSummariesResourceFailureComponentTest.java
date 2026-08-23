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
 * AS-CF-01: Repository crash while resolving the batch returns 500.
 *
 * Separate @QuarkusTest class because mixing @InjectMock with the real DevServices DB in the
 * same class prevents the real DB bean from being used by other tests (CLAUDE.md rule).
 */
@QuarkusTest
@DisplayName("Internal Application Summaries Resource Failure Tests")
class InternalApplicationSummariesResourceFailureComponentTest {

    private static final String BASE = "/internal/applications/summaries";
    private static final String SERVICE_KEY = "test-internal-key";

    @InjectMock
    ApplicationRepository applicationRepository;

    @Test
    @DisplayName("AS-CF-01 / AS244-CF-01: repository crash while resolving the batch returns 500 with ErrorResponse "
            + "shape, no stack trace leaked (regression: the new companyLogoUrl column introduces no new failure mode)")
    void repositoryCrashReturns500() {
        when(applicationRepository.findAllByIds(any()))
                .thenThrow(new RuntimeException("simulated DB crash"));

        given()
                .header("X-Service-Key", SERVICE_KEY)
                .queryParam("ids", UUID.randomUUID().toString())
                .when().get(BASE)
                .then().statusCode(500)
                .body("error", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty()))
                .body("message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty()))
                .body("message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("simulated DB crash")));
    }
}
