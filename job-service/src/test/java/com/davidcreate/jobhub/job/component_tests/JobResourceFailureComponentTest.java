package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.domain.port.out.JobPostRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Failure-path component tests for {@code JobResource}.
 *
 * <p>Lives in its own class because {@code @InjectMock} replaces the bean for
 * the whole {@code @QuarkusTest} class; we don't want to lose the real
 * DevServices-backed repository in {@link JobResourceComponentTest}.
 */
@QuarkusTest
@DisplayName("Job Resource Failure Component Tests")
class JobResourceFailureComponentTest {

    private static final String JOBS = "/jobs";

    @InjectMock
    JobPostRepository jobPostRepository;

    @Test
    @DisplayName("✗ search query timeout or DB connection crash → 500 internal server error")
    void testSearchServerError() {
        when(jobPostRepository.search(any()))
                .thenThrow(new RuntimeException("Simulated DB connection crash"));

        given()
                .when().get(JOBS)
                .then()
                .statusCode(500);
    }

    @Test
    @DisplayName("✗ data corruption or fetch exception → 500 internal server error")
    void testGetJobServerError() {
        when(jobPostRepository.findJobById(any()))
                .thenThrow(new RuntimeException("Simulated data corruption"));

        given()
                .pathParam("id", UUID.randomUUID())
                .when().get(JOBS + "/{id}")
                .then()
                .statusCode(500);
    }
}
