package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Failure-path component tests for {@code /internal/trigger-requests} (story #582,
 * ADR 0033). Separate {@code @QuarkusTest} class: {@code @InjectMock} replaces the bean
 * for the whole class, so it cannot share a class with the real-DB happy/4xx tests in
 * {@link InternalTriggerRequestResourceComponentTest}.
 */
@QuarkusTest
@DisplayName("Internal Trigger Request Resource Failure Component Tests (story #582)")
class InternalTriggerRequestResourceFailureComponentTest {

    private static final String BASE = "/internal/trigger-requests";
    private static final String SERVICE_KEY = "test-internal-key";

    @InjectMock
    TriggerRequestQueue triggerRequestQueue;

    // ── TR-10 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TR-10: full stack, missing X-Service-Key header - POST queue - 401, rejected "
            + "pre-insert, no downstream call")
    void queueingWithoutServiceKeyIsRejectedBeforeAnyWrite() {
        given()
                .contentType("application/json")
                .body(Map.of("kind", "crawl"))
                .when().post(BASE)
                .then()
                .statusCode(401);

        org.mockito.Mockito.verifyNoInteractions(triggerRequestQueue);
    }

    // ── TR-13 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TR-13: repository throws on insert -> 500")
    void repositoryCrashOnEnqueueReturns500() {
        when(triggerRequestQueue.enqueue(any(), any(), any()))
                .thenThrow(new RuntimeException("simulated DB crash"));

        given()
                .header("X-Service-Key", SERVICE_KEY)
                .contentType("application/json")
                .body(Map.of("kind", "crawl"))
                .when().post(BASE)
                .then()
                .statusCode(500);
    }
}
