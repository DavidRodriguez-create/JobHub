package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.out.client.GreenhouseJobSourceClient;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Component tests for {@code CrawlerResource}.
 *
 * <p>Backed by mocked repositories and a mocked Greenhouse client so no real
 * database or external HTTP calls are needed. Follows the same pattern as
 * job-service: mock at the repository layer, exercise the real service.
 *
 * <p>Server-error (500) paths live in {@link CrawlerResourceFailureComponentTest}.
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

    @Test
    @DisplayName("POST /crawl ✓ valid batch request with jobs found → 200 OK & batch results")
    void testBatchSuccess() {
        when(pullTargetRepository.findNextAvailableAndLock())
                .thenReturn(Optional.of(sampleTarget))
                .thenReturn(Optional.empty());

        given()
                .contentType("application/json")
                .when().post(CRAWL)
                .then()
                .statusCode(200)
                .body("crawled", equalTo(1));
    }

    @Test
    @DisplayName("POST /crawl ✓ no available targets → 204 no content")
    void testBatchEmpty() {
        when(pullTargetRepository.findNextAvailableAndLock()).thenReturn(Optional.empty());

        given()
                .contentType("application/json")
                .when().post(CRAWL)
                .then()
                .statusCode(204);
    }

    @Test
    @DisplayName("POST /crawl ✗ limit=0 → 400 bad request (via ValidationExceptionMapper)")
    void testBatchValidationError() {
        given()
                .contentType("application/json")
                .queryParam("limit", 0)
                .when().post(CRAWL)
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("POST /crawl/{targetId} ✓ valid target ID → 200 OK")
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
    @DisplayName("POST /crawl/{targetId} ✗ target locked or missing → 409 conflict (via ConflictExceptionMapper)")
    void testCrawlIdConflict() {
        when(pullTargetRepository.findAndLockById(any())).thenReturn(Optional.empty());

        given()
                .contentType("application/json")
                .pathParam("targetId", KNOWN_ID)
                .when().post(CRAWL + "/{targetId}")
                .then()
                .statusCode(409);
    }
}
