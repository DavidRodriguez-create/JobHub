package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.domain.port.out.PullTargetRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Failure-path component tests for {@code CrawlerResource}.
 *
 * <p>Lives in its own class because {@code @InjectMock} replaces the bean for
 * the whole {@code @QuarkusTest} class — kept separate from
 * {@link CrawlerResourceComponentTest} for clarity.
 */
@QuarkusTest
@DisplayName("Crawler Resource Failure Component Tests")
class CrawlerResourceFailureComponentTest {

    private static final String CRAWL = "/crawl";

    @InjectMock
    PullTargetRepository pullTargetRepository;

    @Test
    @DisplayName("POST /crawl ✗ DB crash during batch → 500 internal server error (via GlobalExceptionMapper)")
    void testBatchServerError() {
        when(pullTargetRepository.findNextAvailableAndLock())
                .thenThrow(new RuntimeException("Simulated DB connection crash"));

        given()
                .contentType("application/json")
                .when().post(CRAWL)
                .then()
                .statusCode(500);
    }

    @Test
    @DisplayName("POST /crawl/{targetId} ✗ DB crash during single crawl → 500 internal server error (via GlobalExceptionMapper)")
    void testCrawlIdServerError() {
        when(pullTargetRepository.findAndLockById(any()))
                .thenThrow(new RuntimeException("Simulated DB connection crash"));

        given()
                .contentType("application/json")
                .pathParam("targetId", UUID.randomUUID())
                .when().post(CRAWL + "/{targetId}")
                .then()
                .statusCode(500);
    }
}
