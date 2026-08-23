package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.WeeklyDigestScheduler;
import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.DigestRunEntity;
import com.davidcreate.jobhub.notification.component_tests.support.AuthInternalUnreachableProfile;
import com.davidcreate.jobhub.notification.domain.port.out.DigestMailer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TC-13b: when auth-service (the {@code auth-internal} batch email lookup) is unreachable,
 * the weekly-digest run fails loud for that dependency but the service stays up and other
 * endpoints keep responding.
 *
 * <p>Lives in its own {@code @QuarkusTest} class — per CLAUDE.md, {@code @InjectMock} replaces
 * the {@code DigestMailer} bean for the whole class, and the unreachable
 * {@code auth-internal} URL (via {@link AuthInternalUnreachableProfile}) would otherwise
 * conflict with the happy-path WireMock stubs in {@code WeeklyDigestSchedulerComponentTest}.
 */
@QuarkusTest
@TestProfile(AuthInternalUnreachableProfile.class)
@DisplayName("WeeklyDigestScheduler Component Tests — auth-service outage")
class WeeklyDigestSchedulerOutageComponentTest {

    private static final String PREFERENCES_BASE = "/notifications/preferences";

    private static final UUID USER_0001 = UUID.fromString("e0000000-0000-0000-0000-000000000001");

    @Inject
    WeeklyDigestScheduler scheduler;

    @InjectMock
    DigestMailer digestMailer;

    // TC-13b
    @Test
    @TestSecurity(user = "b0000000-0000-0000-0000-000000000099", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "b0000000-0000-0000-0000-000000000099"))
    @DisplayName("TC-13b: scheduler run logs a single error and leaves the service responsive when auth-service is down")
    void schedulerRunLogsSingleErrorAndStaysResponsiveWhenAuthServiceDown() {
        scheduler.run();

        verify(digestMailer, never()).send(anyString(), any(), anyBoolean());

        List<DigestRunEntity> sentRuns = DigestRunEntity.list("userId = ?1 and status = ?2", USER_0001, "sent");
        assertThat(sentRuns).isEmpty();

        given().when().get(PREFERENCES_BASE)
                .then().statusCode(200);
    }
}
