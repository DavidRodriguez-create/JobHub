package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.source;

import com.davidcreate.jobhub.crawler.adapter.out.client.source.AmazonJobSourceClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.source.BaseJobSourceClient;
import com.davidcreate.jobhub.crawler.domain.model.PullResult;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ADR 0029, story #513: TC-513-B18, the "found N jobs" DEBUG-demotion case for
 * AmazonJobSourceClient (the per-city line the ADR calls "the worst offender": one line per
 * city per run). No prior unit test existed for this client, so this file is new and minimal,
 * mirroring {@code GreenhouseJobSourceClientTest}'s mocked-{@link HttpClient} pattern.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AmazonJobSourceClient Unit Tests")
class AmazonJobSourceClientTest {

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<String> response;

    private AmazonJobSourceClient client;

    @BeforeEach
    void setUp() {
        client = new AmazonJobSourceClient() {
            @Override
            protected HttpClient httpClient() {
                return httpClient;
            }

            @Override
            protected Duration requestTimeout() {
                return Duration.ofSeconds(30);
            }
        };
        inject(client, "objectMapper", new ObjectMapper());
    }

    private static void inject(Object target, String fieldName, Object value) {
        try {
            Field field = BaseJobSourceClient.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static PullTarget target() {
        return PullTarget.builder()
                .id(UUID.randomUUID())
                .sourceType("amazon")
                .companyName("Amazon")
                .scraperConfig("{\"locations\":[{\"city\":\"Seattle\",\"country\":\"US\"}]}")
                .build();
    }

    private static HttpResponse.BodyHandler<String> anyBodyHandler() {
        return any();
    }

    private PullResult crawlEmptyPage() throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"jobs\":[]}");
        return client.crawl(target());
    }

    @Test
    @DisplayName("empty jobs array for a single configured location yields a successful, empty result")
    void emptyJobsArrayYieldsSuccessfulEmptyResult() throws Exception {
        PullResult result = crawlEmptyPage();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getJobs()).isEmpty();
    }

    // ─── TC-513-B18 (ADR 0029, story #513): "found N jobs" demoted to DEBUG ────

    @Test
    @DisplayName("TC-513-B18: 'Amazon <city>/<country>: found N jobs' is suppressed at the "
            + "default level, and appears at DEBUG when the category is opened up")
    void foundJobsLineIsSuppressedThenDebugAtOverride() throws Exception {
        Logger clientLogger = Logger.getLogger(AmazonJobSourceClient.class.getName());
        CapturingHandler handler = new CapturingHandler();
        clientLogger.addHandler(handler);
        try {
            crawlEmptyPage();
            assertThat(matching(handler.records, "Amazon Seattle/US")).isEmpty();

            clientLogger.setLevel(Level.ALL);
            crawlEmptyPage();

            List<LogRecord> debugLines = matching(handler.records, "Amazon Seattle/US");
            assertThat(debugLines).hasSize(1);
            assertThat(debugLines.get(0).getMessage()).isEqualTo("Amazon Seattle/US: found 0 jobs");
            assertThat(debugLines.get(0).getLevel().intValue()).isLessThan(Level.INFO.intValue());
        } finally {
            clientLogger.removeHandler(handler);
            clientLogger.setLevel(null);
        }
    }

    private static List<LogRecord> matching(List<LogRecord> records, String prefix) {
        return records.stream()
                .filter(r -> r.getMessage() != null && r.getMessage().startsWith(prefix))
                .toList();
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
    }
}
