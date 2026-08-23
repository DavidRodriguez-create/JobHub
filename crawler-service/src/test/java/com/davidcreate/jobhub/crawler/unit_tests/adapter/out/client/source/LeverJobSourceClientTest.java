package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.source;

import com.davidcreate.jobhub.crawler.adapter.out.client.source.BaseJobSourceClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.source.LeverJobSourceClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.SalaryParser;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.JobPostLocation;
import com.davidcreate.jobhub.crawler.domain.model.PullResult;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Story #319 (ticket #323): mirrors {@code SmartRecruitersJobSourceClientTest}'s pattern
 * (mocked {@link HttpClient}, no WireMock, fixtures loaded from {@code src/test/resources}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LeverJobSourceClient Unit Tests")
class LeverJobSourceClientTest {

    private static final String FIXTURES_DIR = "src/test/resources/fixtures/";

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<String> response;

    @Mock
    SalaryParser salaryParser;

    private LeverJobSourceClient client;

    @BeforeEach
    void setUp() {
        lenient().when(salaryParser.parseToEur(any())).thenReturn(Optional.empty());

        client = new LeverJobSourceClient() {
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
                .sourceType("lever")
                .companyName("Acme")
                .token("acme")
                .build();
    }

    private static String fixture(String name) {
        try {
            return Files.readString(Path.of(FIXTURES_DIR + name));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static HttpResponse.BodyHandler<String> anyBodyHandler() {
        return any();
    }

    private PullResult crawlFixture() throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(fixture("lever-postings-multi-location.json"));
        return client.crawl(target());
    }

    // ─── TC-319-CRAWL-01: multiple distinct listed locations, deduped, one primary ──

    @Test
    @DisplayName("TC-319-CRAWL-01: canonical + 2 distinct listed locations yield primary + 2 deduped additional openings")
    void multipleDistinctListedLocationsYieldDedupedAdditionalOpenings() throws Exception {
        PullResult result = crawlFixture();

        JobPost post = result.getJobs().get(0);
        assertThat(post.getCity()).isEqualTo("Barcelona");
        assertThat(post.getCountry()).isEqualTo("Spain");
        assertThat(post.getAdditionalLocations()).hasSize(2);
        assertThat(post.getAdditionalLocations()).extracting(JobPostLocation::getCountry, JobPostLocation::getCity)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Netherlands", "Amsterdam"),
                        org.assertj.core.groups.Tuple.tuple("France", "Paris"));
        assertThat(post.getAdditionalLocations()).allMatch(loc -> !loc.isPrimary());
    }

    // ─── TC-319-CRAWL-02: single-location posting still yields exactly one opening (regression) ──

    @Test
    @DisplayName("TC-319-CRAWL-02: single-location Lever posting still yields exactly one opening")
    void singleLocationPostingYieldsExactlyOneOpening() throws Exception {
        PullResult result = crawlFixture();

        JobPost post = result.getJobs().get(1);
        assertThat(post.getCity()).isEqualTo("Madrid");
        assertThat(post.getCountry()).isEqualTo("Spain");
        assertThat(post.getAdditionalLocations()).isEmpty();
    }

    // ─── TC-319-CRAWL-03: canonical repeated verbatim in the list is not double-stored ──

    @Test
    @DisplayName("TC-319-CRAWL-03: canonical location repeated verbatim in the list is not stored a second time")
    void canonicalRepeatedVerbatimIsNotDoubleStored() throws Exception {
        PullResult result = crawlFixture();

        JobPost post = result.getJobs().get(2);
        assertThat(post.getAdditionalLocations()).hasSize(1);
        JobPostLocation only = post.getAdditionalLocations().get(0);
        assertThat(only.getCountry()).isEqualTo("Netherlands");
        assertThat(only.getCity()).isEqualTo("Amsterdam");
    }

    // ─── TC-319-CRAWL-04: case-insensitive dedup of the canonical location ──

    @Test
    @DisplayName("TC-319-CRAWL-04: canonical location repeated with different casing is not stored a second time")
    void canonicalRepeatedWithDifferentCasingIsNotDoubleStored() throws Exception {
        PullResult result = crawlFixture();

        JobPost post = result.getJobs().get(3);
        assertThat(post.getAdditionalLocations()).hasSize(1);
        JobPostLocation only = post.getAdditionalLocations().get(0);
        assertThat(only.getCountry()).isEqualTo("Netherlands");
        assertThat(only.getCity()).isEqualTo("Amsterdam");
    }

    // ─── TC-319-CRAWL-05: two distinct listed locations in the same country remain two openings ──

    @Test
    @DisplayName("TC-319-CRAWL-05: two distinct same-country listed locations remain two openings")
    void sameCountryDistinctCitiesRemainTwoOpenings() throws Exception {
        PullResult result = crawlFixture();

        JobPost post = result.getJobs().get(4);
        assertThat(post.getCity()).isEqualTo("Barcelona");
        assertThat(post.getCountry()).isEqualTo("Spain");
        assertThat(post.getAdditionalLocations()).hasSize(1);
        JobPostLocation additional = post.getAdditionalLocations().get(0);
        assertThat(additional.getCity()).isEqualTo("Madrid");
        assertThat(additional.getCountry()).isEqualTo("Spain");
        assertThat(additional.isPrimary()).isFalse();
    }

    // ─── TC-319-CRAWL-06: Remote listed alongside a real primary country ──

    @Test
    @DisplayName("TC-319-CRAWL-06: Remote listed alongside a real primary country becomes a Remote additional opening")
    void remoteListedAlongsideRealPrimaryCountry() throws Exception {
        PullResult result = crawlFixture();

        JobPost post = result.getJobs().get(5);
        assertThat(post.getCity()).isEqualTo("Berlin");
        assertThat(post.getCountry()).isEqualTo("Germany");
        assertThat(post.getAdditionalLocations()).hasSize(1);
        JobPostLocation remote = post.getAdditionalLocations().get(0);
        assertThat(remote.getCountry()).isEqualTo("Remote");
        assertThat(remote.getCity()).isNull();
    }

    // ─── TC-319-CRAWL-07: allLocations key absent entirely (older/partial payload shape) ──

    @Test
    @DisplayName("TC-319-CRAWL-07: allLocations key absent entirely does not throw, additionalLocations empty")
    void missingAllLocationsKeyDoesNotThrow() throws Exception {
        String body = "[{"
                + "\"text\":\"Legacy Shape Role\","
                + "\"hostedUrl\":\"https://jobs.lever.co/Acme/319-legacy\","
                + "\"categories\":{\"location\":\"Madrid, Spain\",\"commitment\":\"Full-time\"},"
                + "\"descriptionPlain\":\"Older payload shape, no allLocations key at all.\""
                + "}]";
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);

        PullResult result = client.crawl(target());

        assertThat(result.isSuccess()).isTrue();
        JobPost post = result.getJobs().get(0);
        assertThat(post.getCity()).isEqualTo("Madrid");
        assertThat(post.getCountry()).isEqualTo("Spain");
        assertThat(post.getAdditionalLocations()).isEmpty();
    }

    // ─── TC-319-CRAWL-08: allLocations present but empty array ──

    @Test
    @DisplayName("TC-319-CRAWL-08: allLocations present but empty array yields no additional openings")
    void emptyAllLocationsArrayYieldsNoAdditionalOpenings() throws Exception {
        PullResult result = crawlFixture();

        JobPost post = result.getJobs().get(6);
        assertThat(post.getCity()).isEqualTo("Madrid");
        assertThat(post.getCountry()).isEqualTo("Spain");
        assertThat(post.getAdditionalLocations()).isEmpty();
    }

    // ─── TC-319-CRAWL-09: zero-location posting stays zero-location ──

    @Test
    @DisplayName("TC-319-CRAWL-09: zero-location Lever posting stays zero-location")
    void zeroLocationPostingStaysZeroLocation() throws Exception {
        PullResult result = crawlFixture();

        JobPost post = result.getJobs().get(7);
        assertThat(post.getCity()).isNull();
        assertThat(post.getCountry()).isNull();
        assertThat(post.getAdditionalLocations()).isEmpty();
        assertThat(post.locations()).isEmpty();
    }

    // ─── QAE-408-F-02: facade regression, story #408 / ADR 0021 ──

    @Test
    @DisplayName("QAE-408-F-02: a messy alias raw location (usa) comes out canonicalized with zero call-site change (AC-408-49)")
    void messyAliasRawLocationComesOutCanonicalized() throws Exception {
        String body = "[{"
                + "\"text\":\"Story 408 Alias Role\","
                + "\"hostedUrl\":\"https://jobs.lever.co/Acme/408-alias\","
                + "\"categories\":{\"location\":\"usa\",\"commitment\":\"Full-time\"},"
                + "\"descriptionPlain\":\"Raw source location is the messy alias 'usa'.\""
                + "}]";
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);

        PullResult result = client.crawl(target());

        assertThat(result.isSuccess()).isTrue();
        JobPost post = result.getJobs().get(0);
        assertThat(post.getCity()).isNull();
        assertThat(post.getCountry()).isEqualTo("United States");
    }

    // ─── TC-513-B18 (ADR 0029, story #513): "found N jobs" demoted to DEBUG ────

    @Test
    @DisplayName("TC-513-B18: 'Lever ...: found N jobs' is suppressed at the default level, "
            + "and appears at DEBUG when the category is opened up")
    void foundJobsLineIsSuppressedThenDebugAtOverride() throws Exception {
        Logger clientLogger = Logger.getLogger(LeverJobSourceClient.class.getName());
        CapturingHandler handler = new CapturingHandler();
        clientLogger.addHandler(handler);
        try {
            crawlFixture();
            assertThat(matching(handler.records, "Lever ")).isEmpty();

            clientLogger.setLevel(Level.ALL);
            crawlFixture();

            List<LogRecord> debugLines = matching(handler.records, "Lever ");
            assertThat(debugLines).hasSize(1);
            assertThat(debugLines.get(0).getMessage()).isEqualTo("Lever acme: found 8 jobs");
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
