package com.davidcreate.jobhub.application.unit_tests.logging;

import com.davidcreate.jobhub.application.logging.RequestLoggingFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RLF-U1 elapsed >= threshold -> exactly one WARNING with method/path/status/elapsed.
 * RLF-U2 elapsed < threshold -> zero WARNINGs.
 * RLF-U3 boundary elapsed == threshold -> one WARNING (proves >=).
 * RLF-U4 slow-request.enabled=false + slow -> zero WARNINGs.
 * RLF-U5 request-log.enabled=false + slow -> still exactly one WARNING.
 * RLF-U6 request-side filter(req) with request-log.enabled=false -> setProperty(START_TIME, ...) still fires.
 * RLF-U7 START_TIME null/non-Long (elapsed==-1) -> zero WARNINGs even at threshold-ms=0.
 * RLF-U8 threshold-ms=0 + valid near-zero elapsed -> exactly one WARNING.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RequestLoggingFilter Unit Tests - slow-request WARN")
class RequestLoggingFilterTest {

    private static final String START_TIME = "jobhub.req.start-nanos";
    private static final Logger HTTP_IN = Logger.getLogger("http.in");

    @Mock
    ContainerRequestContext req;

    @Mock
    ContainerResponseContext res;

    @Mock
    UriInfo uriInfo;

    private RequestLoggingFilter filter;
    private CapturingHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        filter = new RequestLoggingFilter();
        setField("enabled", true);
        setField("bodyLimit", 2000);
        setField("slowRequestEnabled", true);
        setField("slowRequestThresholdMs", 1000L);

        handler = new CapturingHandler();
        HTTP_IN.addHandler(handler);

        lenient().when(req.getMethod()).thenReturn("GET");
        lenient().when(req.getUriInfo()).thenReturn(uriInfo);
        lenient().when(uriInfo.getPath()).thenReturn("jobs");
        lenient().when(res.getStatus()).thenReturn(200);
        lenient().when(res.hasEntity()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        HTTP_IN.removeHandler(handler);
    }

    @Test
    @DisplayName("RLF-U1: elapsed >= threshold logs exactly one WARNING with method/path/status/elapsed")
    void warnsWhenElapsedAtOrAboveThreshold() {
        // Stubbed 1500ms comfortably clears the 1000ms threshold, but the real elapsed read by
        // the filter is (System.nanoTime() - start), so a cold-JVM fork can add a few ms of
        // jitter between stubbing and invocation. Assert the stable message fields plus the
        // *numeric* elapsed relationship, not an exact millisecond literal.
        stubElapsed(1500);

        filter.filter(req, res);

        assertThat(handler.warnings()).hasSize(1);
        String message = handler.warnings().get(0).getMessage();
        assertThat(message).contains("SLOW").contains("GET").contains("jobs").contains("200")
                .contains("threshold 1000ms");
        assertThat(extractElapsedMs(message)).isGreaterThanOrEqualTo(1000L);
    }

    @Test
    @DisplayName("RLF-U2: elapsed < threshold logs zero WARNINGs")
    void noWarningWhenElapsedBelowThreshold() {
        stubElapsed(500);

        filter.filter(req, res);

        assertThat(handler.warnings()).isEmpty();
    }

    @Test
    @DisplayName("RLF-U3: boundary elapsed == threshold logs one WARNING (proves >=)")
    void warnsAtExactThreshold() throws Exception {
        long thresholdMs = 1000L;
        setField("slowRequestThresholdMs", thresholdMs);
        long startNanos = System.nanoTime() - thresholdMs * 1_000_000L;
        when(req.getProperty(START_TIME)).thenReturn(startNanos);

        filter.filter(req, res);

        assertThat(handler.warnings()).hasSize(1);
    }

    @Test
    @DisplayName("RLF-U4: slow-request.enabled=false + slow request logs zero WARNINGs")
    void noWarningWhenSlowRequestDisabled() throws Exception {
        setField("slowRequestEnabled", false);
        stubElapsed(5000);

        filter.filter(req, res);

        assertThat(handler.warnings()).isEmpty();
    }

    @Test
    @DisplayName("RLF-U5: request-log.enabled=false + slow request still logs exactly one WARNING")
    void warnsEvenWhenVerboseLoggingDisabled() throws Exception {
        setField("enabled", false);
        stubElapsed(5000);

        filter.filter(req, res);

        assertThat(handler.warnings()).hasSize(1);
    }

    @Test
    @DisplayName("RLF-U6: request-side filter always records the start time, even when request-log.enabled=false")
    void alwaysRecordsStartTimeOnRequestSide() throws Exception {
        setField("enabled", false);

        filter.filter(req);

        verify(req).setProperty(eq(START_TIME), any());
    }

    @Test
    @DisplayName("RLF-U7: missing/non-Long START_TIME (elapsed==-1) never warns, even at threshold-ms=0")
    void noWarningWhenStartTimeUncomputable() throws Exception {
        setField("slowRequestThresholdMs", 0L);
        when(req.getProperty(START_TIME)).thenReturn(null);

        filter.filter(req, res);

        assertThat(handler.warnings()).isEmpty();
    }

    @Test
    @DisplayName("RLF-U8: threshold-ms=0 + valid near-zero elapsed logs exactly one WARNING")
    void warnsAtZeroThreshold() throws Exception {
        setField("slowRequestThresholdMs", 0L);
        stubElapsed(0);

        filter.filter(req, res);

        assertThat(handler.warnings()).hasSize(1);
    }

    private void stubElapsed(long elapsedMs) {
        long startNanos = System.nanoTime() - elapsedMs * 1_000_000L;
        when(req.getProperty(START_TIME)).thenReturn(startNanos);
    }

    private static final Pattern TOOK_MS = Pattern.compile("took (\\d+)ms");

    private static long extractElapsedMs(String message) {
        Matcher m = TOOK_MS.matcher(message);
        assertThat(m.find()).as("expected a 'took <n>ms' token in: " + message).isTrue();
        return Long.parseLong(m.group(1));
    }

    private void setField(String name, Object value) throws Exception {
        Field f = RequestLoggingFilter.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(filter, value);
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
            // org.jboss.logging maps WARN to its own JDKLevel("WARN", 900) instance rather than
            // java.util.logging.Level.WARNING, so compare by severity, not by identity/name.
            return records.stream()
                    .filter(r -> r.getLevel().intValue() == Level.WARNING.intValue())
                    .toList();
        }
    }
}
