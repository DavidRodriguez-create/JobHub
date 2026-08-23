package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.source;

import com.davidcreate.jobhub.crawler.adapter.out.client.source.BaseJobSourceClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.source.GreenhouseJobSourceClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.SalaryParser;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
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
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Story #408 (ADR 0021), QAE-408-C-09: Greenhouse call-site facade regression, the component
 * half of QAE-408-F-02 (unit half proven in {@code LocationParserTest}). Mirrors {@code
 * LeverJobSourceClientTest}'s pattern (mocked {@link HttpClient}, no WireMock, inline fixture
 * JSON, since no Greenhouse fixture file existed prior to this story).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GreenhouseJobSourceClient Unit Tests")
class GreenhouseJobSourceClientTest {

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<String> response;

    @Mock
    SalaryParser salaryParser;

    private GreenhouseJobSourceClient client;

    @BeforeEach
    void setUp() {
        lenient().when(salaryParser.parseToEur(any())).thenReturn(Optional.empty());

        client = new GreenhouseJobSourceClient() {
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
        inject(client, "salaryParser", salaryParser);
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
                .sourceType("greenhouse")
                .companyName("Acme")
                .token("acme")
                .build();
    }

    private static HttpResponse.BodyHandler<String> anyBodyHandler() {
        return any();
    }

    private PullResult crawlBody(String body) throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return client.crawl(target());
    }

    // ─── QAE-408-C-09: facade regression, story #408 / ADR 0021 ──

    @Test
    @DisplayName("QAE-408-C-09: a messy alias raw location (usa) comes out canonicalized with zero call-site change (AC-408-49)")
    void messyAliasRawLocationComesOutCanonicalized() throws Exception {
        String body = "[{"
                + "\"title\":\"Story 408 Alias Role\","
                + "\"absolute_url\":\"https://boards.greenhouse.io/acme/jobs/408-alias\","
                + "\"location\":{\"name\":\"usa\"},"
                + "\"content\":\"Raw source location is the messy alias 'usa'.\","
                + "\"metadata\":[]"
                + "}]";
        String root = "{\"jobs\":" + body + "}";

        PullResult result = crawlBody(root);

        assertThat(result.isSuccess()).isTrue();
        JobPost post = result.getJobs().get(0);
        assertThat(post.getCity()).isNull();
        assertThat(post.getCountry()).isEqualTo("United States");
    }

    @Test
    @DisplayName("QAE-408-C-09: a City, us style raw location canonicalizes both slots (AC-408-49)")
    void cityCommaLowerCaseIsoCodeCanonicalizes() throws Exception {
        String body = "[{"
                + "\"title\":\"Story 408 City Comma Role\","
                + "\"absolute_url\":\"https://boards.greenhouse.io/acme/jobs/408-city-comma\","
                + "\"location\":{\"name\":\"Austin, us\"},"
                + "\"content\":\"Raw source location is 'Austin, us'.\","
                + "\"metadata\":[]"
                + "}]";
        String root = "{\"jobs\":" + body + "}";

        PullResult result = crawlBody(root);

        assertThat(result.isSuccess()).isTrue();
        JobPost post = result.getJobs().get(0);
        assertThat(post.getCity()).isEqualTo("Austin");
        assertThat(post.getCountry()).isEqualTo("United States");
    }

    @Test
    @DisplayName("QAE-408-C-09: an unmappable raw location is preserved, not nulled (AC-408-29, core preserve-raw rule)")
    void unmappableRawLocationIsPreservedNotNulled() throws Exception {
        String body = "[{"
                + "\"title\":\"Story 408 Unmappable Role\","
                + "\"absolute_url\":\"https://boards.greenhouse.io/acme/jobs/408-unmappable\","
                + "\"location\":{\"name\":\"Emea\"},"
                + "\"content\":\"Raw source location is the unmappable region 'Emea'.\","
                + "\"metadata\":[]"
                + "}]";
        String root = "{\"jobs\":" + body + "}";

        PullResult result = crawlBody(root);

        assertThat(result.isSuccess()).isTrue();
        JobPost post = result.getJobs().get(0);
        assertThat(post.getCity()).isEqualTo("Emea");
        assertThat(post.getCountry()).isNull();
    }

    // ─── TC-513-B18 (ADR 0029, story #513): "found N jobs" demoted to DEBUG ────

    @Test
    @DisplayName("TC-513-B18: 'Greenhouse ...: found N jobs' is suppressed at the default level, "
            + "and appears at DEBUG when the category is opened up")
    void foundJobsLineIsSuppressedThenDebugAtOverride() throws Exception {
        Logger clientLogger = Logger.getLogger(GreenhouseJobSourceClient.class.getName());
        CapturingHandler handler = new CapturingHandler();
        clientLogger.addHandler(handler);
        try {
            crawlBody("{\"jobs\":[]}");
            assertThat(matching(handler.records, "Greenhouse ")).isEmpty();

            clientLogger.setLevel(Level.ALL);
            crawlBody("{\"jobs\":[]}");

            List<LogRecord> debugLines = matching(handler.records, "Greenhouse ");
            assertThat(debugLines).hasSize(1);
            assertThat(debugLines.get(0).getMessage()).isEqualTo("Greenhouse acme: found 0 jobs");
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
