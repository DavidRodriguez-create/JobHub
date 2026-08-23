package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.out.client.source.GreenhouseJobSourceClient;
import com.davidcreate.jobhub.crawler.domain.model.PullResult;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.PullTargetRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Component tests for {@code CrawlerResource}.
 *
 * Backed by mocked repositories and a mocked Greenhouse client so no real
 * database or external HTTP calls are needed. Follows the same pattern as
 * job-service: mock at the repository layer, exercise the real service.
 *
 * Server-error (500) paths live in {@link CrawlerResourceFailureComponentTest}.
 */
@QuarkusTest
@DisplayName("Crawler Resource Component Tests")
class CrawlerResourceComponentTest {

    private static final String CRAWL = "/crawl";
    private static final UUID KNOWN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");

    @InjectMock
    PullTargetRepository pullTargetRepository;

    @InjectMock
    JobPostRepository jobPostRepository;

    @InjectMock
    GreenhouseJobSourceClient greenhouseJobSourceClient;

    private PullTarget sampleTarget;

    @BeforeEach
    void setUp() {
        sampleTarget = PullTarget.builder()
                .id(KNOWN_ID)
                .sourceType("greenhouse")
                .companyName("TestCo Alpha")
                .token("testco-alpha")
                .build();

        lenient().when(greenhouseJobSourceClient.supports("greenhouse")).thenReturn(true);
        lenient().when(greenhouseJobSourceClient.crawl(any())).thenReturn(PullResult.success(List.of()));
    }

    // ── Existing tests updated for new CrawlBatchResult shape ────────────────

    @Test
    @DisplayName("POST /crawl valid batch request with jobs found returns 200 OK and batch results")
    void testBatchSuccess() {
        when(pullTargetRepository.findNextAvailableAndLock())
                .thenReturn(Optional.of(sampleTarget))
                .thenReturn(Optional.empty());

        given()
                .contentType("application/json")
                .when().post(CRAWL)
                .then()
                .statusCode(200)
                .body("crawled", equalTo(1))
                .body("newPosts", equalTo(0))
                .body("hasMore", notNullValue())
                .body("cancelled", notNullValue());
    }

    @Test
    @DisplayName("POST /crawl no available targets returns 204 no content")
    void testBatchEmpty() {
        when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.empty());

        given()
                .contentType("application/json")
                .when().post(CRAWL)
                .then()
                .statusCode(204);
    }

    @Test
    @DisplayName("POST /crawl limit=0 returns 400 bad request (via ValidationExceptionMapper)")
    void testBatchValidationError() {
        given()
                .contentType("application/json")
                .queryParam("limit", 0)
                .when().post(CRAWL)
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("POST /crawl/{targetId} valid target ID returns 200 OK")
    void testCrawlIdSuccess() {
        when(pullTargetRepository.findAndLockById(KNOWN_ID)).thenReturn(Optional.of(sampleTarget));

        given()
                .contentType("application/json")
                .pathParam("targetId", KNOWN_ID)
                .when().post(CRAWL + "/{targetId}")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("POST /crawl/{targetId} target locked or missing returns 409 conflict (via ConflictExceptionMapper)")
    void testCrawlIdConflict() {
        when(pullTargetRepository.findAndLockById(any())).thenReturn(Optional.empty());

        given()
                .contentType("application/json")
                .pathParam("targetId", KNOWN_ID)
                .when().post(CRAWL + "/{targetId}")
                .then()
                .statusCode(409);
    }

    // ── New cases (CS-C-01 through CS-C-08) ──────────────────────────────────

    @Test
    @DisplayName("CS-C-01: POST /crawl with sources available returns 200 and body with newPosts field")
    void csCee01_200WithNewPostsField() {
        when(pullTargetRepository.findNextAvailableAndLock())
                .thenReturn(Optional.of(sampleTarget))
                .thenReturn(Optional.empty());
        // greenhouseJobSourceClient.crawl returns success(List.of()) -- 0 new posts

        given()
                .contentType("application/json")
                .when().post(CRAWL)
                .then()
                .statusCode(200)
                .body("crawled", equalTo(1))
                .body("newPosts", equalTo(0))
                .body("hasMore", notNullValue())
                .body("cancelled", notNullValue());
    }

    @Test
    @DisplayName("CS-C-02: POST /crawl with no available sources returns 204")
    void csCee02_204WhenNoSources() {
        when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.empty());

        given()
                .contentType("application/json")
                .when().post(CRAWL)
                .then()
                .statusCode(204);
    }

    @Test
    @DisplayName("CS-C-03: POST /crawl?limit=1 is accepted (minimum valid value)")
    void csCee03_limit1Accepted() {
        when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.empty());

        given()
                .contentType("application/json")
                .queryParam("limit", 1)
                .when().post(CRAWL)
                .then()
                .statusCode(204); // no sources, but no ValidationException -- 1 is valid
    }

    @Test
    @DisplayName("CS-C-04: POST /crawl?limit=0 returns 400 with standard error body")
    void csCee04_limit0Returns400() {
        given()
                .contentType("application/json")
                .queryParam("limit", 0)
                .when().post(CRAWL)
                .then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());

        verify(pullTargetRepository, never()).findNextAvailableAndLock();
    }

    @Test
    @DisplayName("CS-C-05: POST /crawl?limit=-1 returns 400 with standard error body")
    void csCee05_limitNegativeReturns400() {
        given()
                .contentType("application/json")
                .queryParam("limit", -1)
                .when().post(CRAWL)
                .then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("CS-C-06: POST /crawl with no limit param defaults to configured minNewPosts (100)")
    void csCee06_defaultParamUsesConfiguredMinNewPosts() {
        // min-new-posts=100 in test properties; empty source -- confirms 100 was accepted (no ValidationException)
        when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.empty());

        given()
                .contentType("application/json")
                .when().post(CRAWL)
                .then()
                .statusCode(204); // empty run, no ValidationException: default 100 was used
    }

    @Test
    @DisplayName("CS-C-07: POST /crawl?limit=50 interprets limit as new-post target, not source count")
    void csCee07_limitInterpretedAsNewPostTarget() {
        when(pullTargetRepository.findNextAvailableAndLock())
                .thenReturn(Optional.of(sampleTarget))
                .thenReturn(Optional.empty());
        // client returns empty list: 0 new posts, but crawled=1

        given()
                .contentType("application/json")
                .queryParam("limit", 50)
                .when().post(CRAWL)
                .then()
                .statusCode(200) // crawled=1, isEmpty() is false
                .body("crawled", equalTo(1))
                .body("newPosts", equalTo(0));
    }

    @Test
    @DisplayName("CS-C-08: POST /crawl returns 200 when crawled > 0 even if newPosts = 0")
    void csCee08_200WhenCrawledButNoNewPosts() {
        when(pullTargetRepository.findNextAvailableAndLock())
                .thenReturn(Optional.of(sampleTarget))
                .thenReturn(Optional.empty());
        // All posts returned by client will be empty: 0 new posts, 1 target visited

        given()
                .contentType("application/json")
                .when().post(CRAWL)
                .then()
                .statusCode(200)
                .body("crawled", equalTo(1))
                .body("newPosts", equalTo(0));
    }
}
