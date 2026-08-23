package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.source;

import com.davidcreate.jobhub.crawler.adapter.out.client.source.SmartRecruitersJobSourceClient;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.PullResult;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmartRecruitersJobSourceClient Unit Tests")
class SmartRecruitersJobSourceClientTest {

    private static final String FIXTURES_DIR = "src/test/resources/fixtures/";

    @Mock
    HttpClient httpClient;

    @Mock
    HttpResponse<String> response;

    private SmartRecruitersJobSourceClient client(int maxPages) {
        return new SmartRecruitersJobSourceClient(new ObjectMapper(), maxPages) {
            @Override
            protected HttpClient httpClient() {
                return httpClient;
            }

            @Override
            protected java.time.Duration requestTimeout() {
                return java.time.Duration.ofSeconds(30);
            }
        };
    }

    private SmartRecruitersJobSourceClient client() {
        return client(5);
    }

    private static PullTarget targetWithConfig(String scraperConfig) {
        return PullTarget.builder()
                .id(UUID.randomUUID())
                .sourceType("smartrecruiters")
                .companyName("Bosch Group")
                .scraperConfig(scraperConfig)
                .build();
    }

    private static String fixture(String name) {
        try {
            return Files.readString(Path.of(FIXTURES_DIR + name));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void stubStatusAndBody(int status, String body) {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
    }

    private static HttpResponse.BodyHandler<String> anyBodyHandler() {
        return any();
    }

    // ─── supports() (AC-270-14) ─────────────────────────────────────────────

    @Test
    @DisplayName("TC-SR-01: supports(\"smartrecruiters\") returns true")
    void supportsSmartRecruiters() {
        assertThat(client().supports("smartrecruiters")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "greenhouse", "lever", "workday", "amazon", "unknown", "" })
    @DisplayName("TC-SR-02: supports(sourceType) returns false for every other source type")
    void doesNotSupportOtherSourceTypes(String sourceType) {
        assertThat(client().supports(sourceType)).isFalse();
    }

    @Test
    @DisplayName("TC-SR-02b: supports(null) returns false")
    void doesNotSupportNull() {
        assertThat(client().supports(null)).isFalse();
    }

    // ─── URL / query construction (AC-270-1, AC-270-2) ──────────────────────

    @Test
    @DisplayName("TC-SR-03: multi-country config issues one request per country, each with a single country param and q applied")
    void buildsOneRequestPerCountry() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(captor.capture(), anyBodyHandler())).thenReturn(response);
        stubStatusAndBody(200, fixture("smartrecruiters-postings-empty-first-page.json"));

        PullTarget target = targetWithConfig(
                "{\"company\":\"BoschGroup\",\"countries\":[\"DE\",\"PL\"],\"query\":\"engineer\"}");

        client().crawl(target);

        verify(httpClient, times(2)).send(any(), anyBodyHandler());
        List<URI> requestedUris = captor.getAllValues().stream().map(HttpRequest::uri).toList();
        assertThat(requestedUris).containsExactlyInAnyOrder(
                URI.create("https://api.smartrecruiters.com/v1/companies/BoschGroup/postings?limit=100&offset=0&country=DE&q=engineer"),
                URI.create("https://api.smartrecruiters.com/v1/companies/BoschGroup/postings?limit=100&offset=0&country=PL&q=engineer"));
    }

    @Test
    @DisplayName("TC-SR-04: config without countries/query omits country and q params entirely")
    void buildsUriWithoutCountriesOrQuery() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(captor.capture(), anyBodyHandler())).thenReturn(response);
        stubStatusAndBody(200, fixture("smartrecruiters-postings-empty-first-page.json"));

        PullTarget target = targetWithConfig("{\"company\":\"SGS\"}");

        client().crawl(target);

        assertThat(captor.getValue().uri())
                .isEqualTo(URI.create("https://api.smartrecruiters.com/v1/companies/SGS/postings?limit=100&offset=0"));
    }

    @Test
    @DisplayName("TC-SR-05: empty countries array + blank query are treated as absent")
    void treatsEmptyCountriesAndBlankQueryAsAbsent() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(captor.capture(), anyBodyHandler())).thenReturn(response);
        stubStatusAndBody(200, fixture("smartrecruiters-postings-empty-first-page.json"));

        PullTarget target = targetWithConfig("{\"company\":\"SGS\",\"countries\":[],\"query\":\"   \"}");

        client().crawl(target);

        assertThat(captor.getValue().uri())
                .isEqualTo(URI.create("https://api.smartrecruiters.com/v1/companies/SGS/postings?limit=100&offset=0"));
    }

    // ─── Pagination stop conditions (AC-270-3..7) ───────────────────────────

    @Test
    @DisplayName("TC-SR-06: single page under totalFound stops after page 1 with 3 jobs")
    void stopsAfterSinglePageUnderTotalFound() throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        stubStatusAndBody(200, fixture("smartrecruiters-postings-page1.json"));

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        verify(httpClient, times(1)).send(any(), anyBodyHandler());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getJobCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-SR-07: multi-page crawl (250 total, 100+100+50) stops at offset>=totalFound after 3 pages")
    void stopsAfterOffsetReachesTotalFound() throws Exception {
        String fullPage = fixture("smartrecruiters-postings-full-page-100.json");
        String page2 = withTitlePrefix(fullPage, "P2 ");
        String page3 = withTitlePrefix(truncatedFullPage(fullPage, 50), "P3 ");

        when(httpClient.send(any(), anyBodyHandler()))
                .thenReturn(response, response, response);
        when(response.statusCode()).thenReturn(200);
        when(response.body())
                .thenReturn(fullPage)
                .thenReturn(page2)
                .thenReturn(page3);

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        verify(httpClient, times(3)).send(any(), anyBodyHandler());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getJobCount()).isEqualTo(250);
    }

    @Test
    @DisplayName("TC-SR-08: page cap (default 5) reached mid-crawl still returns success with 500 jobs")
    void stopsAtDefaultPageCap() throws Exception {
        String fullPage = withTotalFound(fixture("smartrecruiters-postings-full-page-100.json"), 10000);

        when(httpClient.send(any(), anyBodyHandler())).thenAnswer(distinctPagePerCallAnswer(fullPage));

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        verify(httpClient, times(5)).send(any(), anyBodyHandler());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getJobCount()).isEqualTo(500);
    }

    @Test
    @DisplayName("TC-SR-09: empty content on first page returns success with zero jobs, one request")
    void emptyFirstPageIsSuccessWithNoJobs() throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        stubStatusAndBody(200, fixture("smartrecruiters-postings-empty-first-page.json"));

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        verify(httpClient, times(1)).send(any(), anyBodyHandler());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getJobCount()).isZero();
    }

    @Test
    @DisplayName("TC-SR-10: empty content on a later page stops pagination, keeps page-1 jobs")
    void stopsOnLaterEmptyPage() throws Exception {
        String page1WithLargeTotal = fixture("smartrecruiters-postings-page1.json")
                .replaceFirst("\"totalFound\"\\s*:\\s*3", "\"totalFound\": 150");

        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response, response);
        when(response.statusCode()).thenReturn(200);
        when(response.body())
                .thenReturn(page1WithLargeTotal)
                .thenReturn(fixture("smartrecruiters-postings-page2-empty.json"));

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        verify(httpClient, times(2)).send(any(), anyBodyHandler());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getJobCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-SR-11: missing content key (valid JSON, wrong shape) treated as empty page, not a failure")
    void missingContentKeyTreatedAsEmptyPage() throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        stubStatusAndBody(200, fixture("smartrecruiters-postings-missing-content-key.json"));

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        verify(httpClient, times(1)).send(any(), anyBodyHandler());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getJobCount()).isZero();
    }

    // ─── JobPost mapping (AC-270-8, AC-270-9, AC-270-10) ────────────────────

    @Test
    @DisplayName("TC-SR-12: full-location posting maps title/url/city/country/employmentType/languages/hash")
    void mapsFullLocationPosting() throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        stubStatusAndBody(200, fixture("smartrecruiters-postings-page1.json"));

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        JobPost post = result.getJobs().get(0);
        assertThat(post.getTitle()).isEqualTo("Backend Engineer");
        assertThat(post.getUrl()).isEqualTo("https://jobs.smartrecruiters.com/BoschGroup/8a7c9e1f-0001");
        assertThat(post.getCity()).isEqualTo("Stuttgart");
        assertThat(post.getCountry()).isEqualTo("Germany");
        assertThat(post.getEmploymentType()).isEqualTo("full-time");
        assertThat(post.getLanguages()).containsExactly("English");
        assertThat(post.getDescription()).isNull();
        assertThat(post.getContentHash()).isNotBlank();
        assertThat(post.getContentHash())
                .isEqualTo(JobPost.computeHash("Backend Engineer", "Bosch Group", "Stuttgart", null));
        // TC-319-CRAWL-10: non-Lever sources keep producing exactly one opening (AC-319-CRAWL-5).
        assertThat(post.getAdditionalLocations()).isEmpty();
    }

    @Test
    @DisplayName("TC-SR-13: posting with no location object is still mapped, city/country null, not dropped")
    void mapsPostingWithMissingLocation() throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        stubStatusAndBody(200, fixture("smartrecruiters-postings-page1.json"));

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        JobPost post = result.getJobs().get(1);
        assertThat(post.getTitle()).isEqualTo("Field Service Technician");
        assertThat(post.getUrl()).isEqualTo("https://jobs.smartrecruiters.com/BoschGroup/8a7c9e1f-0002");
        assertThat(post.getCity()).isNull();
        assertThat(post.getCountry()).isNull();
        assertThat(result.getJobCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-SR-14: unmapped employment-type value (Apprenticeship) maps to null, posting kept")
    void mapsUnknownEmploymentTypeToNull() throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        stubStatusAndBody(200, fixture("smartrecruiters-postings-page1.json"));

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        JobPost post = result.getJobs().get(1);
        assertThat(post.getEmploymentType()).isNull();
        assertThat(result.getJobs()).contains(post);
    }

    @Test
    @DisplayName("TC-SR-15: second full-location posting normalises city/country/employmentType")
    void mapsSecondFullLocationPosting() throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        stubStatusAndBody(200, fixture("smartrecruiters-postings-page1.json"));

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        JobPost post = result.getJobs().get(2);
        assertThat(post.getCity()).isEqualTo("Madrid");
        assertThat(post.getCountry()).isEqualTo("Spain");
        assertThat(post.getEmploymentType()).isEqualTo("full-time");
    }

    // ─── Failure paths (AC-270-11, AC-270-12, AC-270-13) ────────────────────

    @Test
    @DisplayName("TC-SR-16: non-200 on first page fails with reason, status 503, zero jobs")
    void firstPageNon200Fails() throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        when(response.statusCode()).thenReturn(503);

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorReason()).isNotBlank();
        assertThat(result.getHttpStatus()).isEqualTo(503);
        assertThat(result.getJobCount()).isZero();
    }

    @Test
    @DisplayName("TC-SR-17: page 1 succeeds, page 2 returns 500, whole crawl fails (no partial success)")
    void laterPageNon200FailsWholeCrawl() throws Exception {
        String page1WithLargeTotal = fixture("smartrecruiters-postings-page1.json")
                .replaceFirst("\"totalFound\"\\s*:\\s*3", "\"totalFound\": 150");

        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response, response);
        when(response.statusCode()).thenReturn(200, 500);
        when(response.body()).thenReturn(page1WithLargeTotal, "");

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getHttpStatus()).isEqualTo(500);
        assertThat(result.getJobCount()).isZero();
    }

    @Test
    @DisplayName("TC-SR-18: malformed JSON body fails with reason, no forced http status")
    void malformedBodyFails() throws Exception {
        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
        stubStatusAndBody(200, fixture("smartrecruiters-postings-malformed.json"));

        PullResult result = client().crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorReason()).isNotBlank();
        assertThat(result.getHttpStatus()).isNull();
    }

    // ─── Config-driven page cap (AC-270-15) ─────────────────────────────────

    @Test
    @DisplayName("TC-SR-19: overridden max-pages=2 caps the crawl at 2 requests / 200 jobs")
    void honoursOverriddenPageCap() throws Exception {
        String fullPage = withTotalFound(fixture("smartrecruiters-postings-full-page-100.json"), 1000);

        when(httpClient.send(any(), anyBodyHandler())).thenAnswer(distinctPagePerCallAnswer(fullPage));

        PullResult result = client(2).crawl(targetWithConfig("{\"company\":\"BoschGroup\"}"));

        verify(httpClient, times(2)).send(any(), anyBodyHandler());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getJobCount()).isEqualTo(200);
    }

    // ─── Multi-country sweep, merge and dedup (per-country fix) ─────────────

    @Test
    @DisplayName("TC-SR-20: multi-country config merges results across countries and dedups the same posting by contentHash")
    void mergesAndDedupsAcrossCountries() throws Exception {
        String page1 = fixture("smartrecruiters-postings-page1.json");

        when(httpClient.send(any(), anyBodyHandler())).thenReturn(response, response);
        when(response.statusCode()).thenReturn(200, 200);
        when(response.body()).thenReturn(page1, page1);

        PullResult result = client().crawl(targetWithConfig(
                "{\"company\":\"BoschGroup\",\"countries\":[\"DE\",\"PL\"]}"));

        verify(httpClient, times(2)).send(any(), anyBodyHandler());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getJobCount()).isEqualTo(3);
        assertThat(result.getJobs()).extracting(JobPost::getContentHash).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("TC-SR-21: page cap applies per country (2 countries x max-pages=2 -> 4 requests, 400 genuinely distinct jobs, no cross-page/cross-country dedup collapse)")
    void pageCapAppliesPerCountry() throws Exception {
        String basePage = withTotalFound(fixture("smartrecruiters-postings-full-page-100.json"), 1000);

        // Every one of the 4 requests (DE offset=0, DE offset=100, PL offset=0, PL offset=100)
        // must return content distinct from every other request's content, otherwise contentHash
        // dedup would silently collapse a broken per-country/per-offset pagination into a passing
        // assertion (the defect this case exists to catch).
        when(httpClient.send(any(), anyBodyHandler())).thenAnswer(invocation -> {
            HttpRequest req = invocation.getArgument(0);
            String uri = req.uri().toString();
            String country = uri.contains("country=PL") ? "PL" : "DE";
            String offset = uri.replaceAll(".*offset=(\\d+).*", "$1");
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(withTitlePrefix(basePage, country + "-P" + offset + " "));
            return response;
        });

        PullResult result = client(2).crawl(targetWithConfig(
                "{\"company\":\"BoschGroup\",\"countries\":[\"DE\",\"PL\"]}"));

        verify(httpClient, times(4)).send(any(), anyBodyHandler());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getJobCount()).isEqualTo(400);
        assertThat(result.getJobs()).extracting(JobPost::getContentHash).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("TC-SR-22: DE sweep succeeds fully but PL's first page returns 500, whole crawl fails (no partial DE jobs)")
    void laterCountrySweepFailureFailsWholeCrawl() throws Exception {
        String dePage = fixture("smartrecruiters-postings-page1.json");

        when(httpClient.send(any(), anyBodyHandler())).thenAnswer(invocation -> {
            HttpRequest req = invocation.getArgument(0);
            if (req.uri().toString().contains("country=PL")) {
                when(response.statusCode()).thenReturn(500);
            } else {
                when(response.statusCode()).thenReturn(200);
                when(response.body()).thenReturn(dePage);
            }
            return response;
        });

        PullResult result = client().crawl(targetWithConfig(
                "{\"company\":\"BoschGroup\",\"countries\":[\"DE\",\"PL\"]}"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getHttpStatus()).isEqualTo(500);
        assertThat(result.getJobCount()).isZero();
    }

    // ─── TC-513-B18 (ADR 0029, story #513): "found N jobs" demoted to DEBUG ────

    @Test
    @DisplayName("TC-513-B18: 'SmartRecruiters ...: found N jobs' is suppressed at the default level, "
            + "and appears at DEBUG when the category is opened up")
    void foundJobsLineIsSuppressedThenDebugAtOverride() throws Exception {
        java.util.logging.Logger clientLogger = java.util.logging.Logger.getLogger(SmartRecruitersJobSourceClient.class.getName());
        CapturingHandler handler = new CapturingHandler();
        clientLogger.addHandler(handler);
        try {
            when(httpClient.send(any(), anyBodyHandler())).thenReturn(response);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(fixture("smartrecruiters-postings-page1.json"));

            client().crawl(targetWithConfig("{\"company\":\"BoschGroup\",\"countries\":[\"DE\"]}"));
            assertThat(matching(handler.records, "SmartRecruiters ")).isEmpty();

            clientLogger.setLevel(java.util.logging.Level.ALL);
            client().crawl(targetWithConfig("{\"company\":\"BoschGroup\",\"countries\":[\"DE\"]}"));

            List<java.util.logging.LogRecord> debugLines = matching(handler.records, "SmartRecruiters ");
            assertThat(debugLines).hasSize(1);
            assertThat(debugLines.get(0).getMessage()).isEqualTo("SmartRecruiters BoschGroup: found 3 jobs");
            assertThat(debugLines.get(0).getLevel().intValue()).isLessThan(java.util.logging.Level.INFO.intValue());
        } finally {
            clientLogger.removeHandler(handler);
            clientLogger.setLevel(null);
        }
    }

    private static List<java.util.logging.LogRecord> matching(List<java.util.logging.LogRecord> records, String prefix) {
        return records.stream()
                .filter(r -> r.getMessage() != null && r.getMessage().startsWith(prefix))
                .toList();
    }

    private static class CapturingHandler extends java.util.logging.Handler {
        private final List<java.util.logging.LogRecord> records = new java.util.ArrayList<>();

        @Override
        public void publish(java.util.logging.LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    // ─── Test helpers ────────────────────────────────────────────────────────

    private static String withTotalFound(String body, int totalFound) {
        return body.replaceFirst("\"totalFound\"\\s*:\\s*\\d+", "\"totalFound\": " + totalFound);
    }

    private static String withTitlePrefix(String body, String prefix) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(body);
        var content = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("content");
        for (var item : content) {
            String name = item.path("name").asText("");
            ((com.fasterxml.jackson.databind.node.ObjectNode) item).put("name", prefix + name);
        }
        return mapper.writeValueAsString(root);
    }

    private static String truncatedFullPage(String fullPageBody, int keepCount) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(fullPageBody);
        var content = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("content");
        while (content.size() > keepCount) {
            content.remove(content.size() - 1);
        }
        return mapper.writeValueAsString(root);
    }

    // Each successive send() on the same mock gets a distinct copy of the page (title-prefixed
    // by call index) so contentHash dedup does not collapse what should be N distinct pages'
    // worth of postings into one, while every page still carries the same totalFound so the
    // page-cap stop condition (not offset>=totalFound) is what ends the sweep.
    private org.mockito.stubbing.Answer<HttpResponse<String>> distinctPagePerCallAnswer(String basePage) {
        java.util.concurrent.atomic.AtomicInteger callIndex = new java.util.concurrent.atomic.AtomicInteger();
        return invocation -> {
            int index = callIndex.getAndIncrement();
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(withTitlePrefix(basePage, "P" + index + " "));
            return response;
        };
    }
}
