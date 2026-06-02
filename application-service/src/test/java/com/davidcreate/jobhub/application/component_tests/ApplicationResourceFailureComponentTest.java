package com.davidcreate.jobhub.application.component_tests;

import com.davidcreate.jobhub.application.application.port.out.ApplicationRepository;
import com.davidcreate.jobhub.application.application.port.out.JobPostGateway;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@QuarkusTest
@DisplayName("Application Resource Failure Component Tests")
class ApplicationResourceFailureComponentTest {

    private static final String BASE = "/applications";
    private static final String USER = "55555555-5555-5555-5555-555555555555";

    @InjectMock
    ApplicationRepository applicationRepository;

    @InjectMock
    JobPostGateway jobPostGateway;

    @Test
    @TestSecurity(user = USER, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER))
    @DisplayName("GET list → repository crash → 500 via GlobalExceptionMapper")
    void listServerError() {
        when(applicationRepository.listByUser(any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("simulated DB crash"));

        given().when().get(BASE).then().statusCode(500);
    }

    @Test
    @TestSecurity(user = USER, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER))
    @DisplayName("POST crawled apply → job post not found → 404")
    void jobPostNotFound() {
        when(jobPostGateway.findById(any())).thenReturn(Optional.empty());

        given().contentType("application/json")
                .body(Map.of("jobPostId", UUID.randomUUID().toString()))
                .when().post(BASE)
                .then().statusCode(404);
    }
}
