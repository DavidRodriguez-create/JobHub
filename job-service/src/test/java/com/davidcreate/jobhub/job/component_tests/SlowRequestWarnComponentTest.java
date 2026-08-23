package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.SlowRequestWarnProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * RLF-C1 (Story #328 / sub-issue #362): with {@code jobhub.http.slow-request.threshold-ms=0},
 * every request crosses the {@code >=} guard, so a plain {@code GET /jobs} both responds
 * 200 unchanged AND emits exactly one WARNING on the {@code http.in} logger naming the
 * method, path and status.
 */
@QuarkusTest
@TestProfile(SlowRequestWarnProfile.class)
@DisplayName("RequestLoggingFilter Component Tests - slow-request WARN")
class SlowRequestWarnComponentTest {

    private static final String JOBS = "/jobs";
    private static final Logger HTTP_IN = Logger.getLogger("http.in");

    private CapturingHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CapturingHandler();
        HTTP_IN.addHandler(handler);
    }

    @AfterEach
    void tearDown() {
        HTTP_IN.removeHandler(handler);
    }

    @Test
    @DisplayName("RLF-C1: GET /jobs -> 200 unchanged + exactly one WARNING naming GET /jobs 200")
    void slowRequestWarnDoesNotAffectResponseButLogsOnce() {
        given()
                .when().get(JOBS)
                .then()
                .statusCode(200);

        List<LogRecord> warnings = handler.warnings();
        assertThat(warnings).hasSize(1);
        String message = warnings.get(0).getMessage();
        assertThat(message).contains("GET").contains("jobs").contains("200");
    }

    private static class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<LogRecord> warnings() {
            // org.jboss.logging maps WARN to its own Level("WARN", 900) instance rather than
            // java.util.logging.Level.WARNING, so compare by severity, not by identity/name.
            return records.stream()
                    .filter(r -> r.getLevel().intValue() == Level.WARNING.intValue())
                    .toList();
        }
    }
}
