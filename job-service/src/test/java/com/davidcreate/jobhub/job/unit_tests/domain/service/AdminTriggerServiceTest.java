package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.exception.CrawlerUnavailableException;
import com.davidcreate.jobhub.job.domain.exception.NoActiveTriggerException;
import com.davidcreate.jobhub.job.domain.exception.TriggerInProgressException;
import com.davidcreate.jobhub.job.domain.exception.TriggeringDisabledException;
import com.davidcreate.jobhub.job.domain.exception.VerificationRequiredException;
import com.davidcreate.jobhub.job.domain.exception.VerificationThrottledException;
import com.davidcreate.jobhub.job.domain.model.TriggerCommand;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;
import com.davidcreate.jobhub.job.domain.model.TriggerStatus;
import com.davidcreate.jobhub.job.domain.model.TriggerStatusOverview;
import com.davidcreate.jobhub.job.domain.port.out.AdminTwoFactorGateway;
import com.davidcreate.jobhub.job.domain.port.out.CrawlerTriggerGateway;
import com.davidcreate.jobhub.job.domain.port.out.TriggerRequestRepository;
import com.davidcreate.jobhub.job.domain.service.AdminTriggerService;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTriggerService Unit Tests")
class AdminTriggerServiceTest {

    @Mock
    TriggerRequestRepository repository;

    @Mock
    AdminTwoFactorGateway twoFactorGateway;

    @Mock
    CrawlerTriggerGateway crawlerTriggerGateway;

    private AdminTriggerService service;

    @BeforeEach
    void setUp() {
        // enabled=true by default
        service = new AdminTriggerService(repository, twoFactorGateway, crawlerTriggerGateway, true);
    }

    // ── TR-17/TR-18: happy path forwards to the gateway, no local write ─────────

    @Test
    @DisplayName("TR-17: queue() with a mocked client returning 202 returns the mapped TriggerRequest, no local insert")
    void queueReturnsMappedTriggerRequestFromGateway() {
        UUID admin = UUID.randomUUID();
        TriggerRequest queued = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.CRAWL)
                .status(TriggerStatus.QUEUED)
                .requestedBy(admin)
                .requestedAt(OffsetDateTime.now())
                .build();
        when(crawlerTriggerGateway.queue(TriggerKind.CRAWL, admin)).thenReturn(queued);

        TriggerCommand cmd = TriggerCommand.builder()
                .kind("crawl")
                .requestedBy(admin)
                .build();

        TriggerRequest result = service.queue(cmd);

        assertThat(result).isEqualTo(queued);
        verify(crawlerTriggerGateway).queue(TriggerKind.CRAWL, admin);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("TR-18: cancel() with a mocked client returning 200 returns the updated TriggerRequest")
    void cancelReturnsUpdatedTriggerRequestFromGateway() {
        TriggerRequest cancelled = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.CRAWL)
                .status(TriggerStatus.CANCELLED)
                .finishedAt(OffsetDateTime.now())
                .resultSummary("Cancelled before execution")
                .build();
        when(crawlerTriggerGateway.cancel(TriggerKind.CRAWL)).thenReturn(cancelled);

        TriggerRequest result = service.cancel(TriggerKind.CRAWL);

        assertThat(result).isEqualTo(cancelled);
        verify(crawlerTriggerGateway).cancel(TriggerKind.CRAWL);
        verifyNoInteractions(repository);
    }

    // ── TR-19/TR-20: the gateway maps crawler-service's errors, service propagates ──

    @Test
    @DisplayName("TR-19: mocked client throws TriggerInProgressException on queue -> propagated unchanged")
    void queuePropagatesTriggerInProgressFromGateway() {
        UUID admin = UUID.randomUUID();
        when(crawlerTriggerGateway.queue(TriggerKind.CRAWL, admin))
                .thenThrow(new TriggerInProgressException(TriggerKind.CRAWL));

        TriggerCommand cmd = TriggerCommand.builder()
                .kind("crawl")
                .requestedBy(admin)
                .build();

        assertThatThrownBy(() -> service.queue(cmd))
                .isInstanceOf(TriggerInProgressException.class);
    }

    @Test
    @DisplayName("TR-20: mocked client throws NoActiveTriggerException on cancel -> propagated unchanged (public 409)")
    void cancelPropagatesNoActiveTriggerFromGateway() {
        when(crawlerTriggerGateway.cancel(TriggerKind.ENRICHMENT))
                .thenThrow(new NoActiveTriggerException(TriggerKind.ENRICHMENT));

        assertThatThrownBy(() -> service.cancel(TriggerKind.ENRICHMENT))
                .isInstanceOf(NoActiveTriggerException.class);
    }

    @Test
    @DisplayName("mocked client throws CrawlerUnavailableException on queue -> propagated unchanged (503)")
    void queuePropagatesCrawlerUnavailableFromGateway() {
        UUID admin = UUID.randomUUID();
        when(crawlerTriggerGateway.queue(TriggerKind.CRAWL, admin))
                .thenThrow(new CrawlerUnavailableException("Crawler service is unreachable; nothing was started"));

        TriggerCommand cmd = TriggerCommand.builder()
                .kind("crawl")
                .requestedBy(admin)
                .build();

        assertThatThrownBy(() -> service.queue(cmd))
                .isInstanceOf(CrawlerUnavailableException.class);
    }

    @Test
    @DisplayName("mocked client throws CrawlerUnavailableException on cancel -> propagated unchanged (503)")
    void cancelPropagatesCrawlerUnavailableFromGateway() {
        when(crawlerTriggerGateway.cancel(TriggerKind.CRAWL))
                .thenThrow(new CrawlerUnavailableException("Crawler service is unreachable; nothing was changed"));

        assertThatThrownBy(() -> service.cancel(TriggerKind.CRAWL))
                .isInstanceOf(CrawlerUnavailableException.class);
    }

    // ── Kind / validation regression (unchanged by ADR 0033) ────────────────────

    @Test
    @DisplayName("J-U-01: rejects unknown kind with BadRequestException")
    void rejectsUnknownKind() {
        TriggerCommand cmd = TriggerCommand.builder()
                .kind("unknown-value")
                .requestedBy(UUID.randomUUID())
                .build();

        assertThatThrownBy(() -> service.queue(cmd))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(crawlerTriggerGateway);
    }

    // ── TC-384-J1..J8: the 2FA gate (ADR 0019) ──────────────────────────────────

    @Test
    @DisplayName("TC-384-J1: valid TOTP code authorizes -> gateway.queue called, queued request returned")
    void validTotpCodeQueuesTheTrigger() {
        UUID admin = UUID.randomUUID();
        doNothing().when(twoFactorGateway).verify(admin, "123456");
        when(crawlerTriggerGateway.queue(TriggerKind.CRAWL, admin)).thenReturn(
                TriggerRequest.builder()
                        .id(UUID.randomUUID())
                        .kind(TriggerKind.CRAWL)
                        .status(TriggerStatus.QUEUED)
                        .requestedBy(admin)
                        .requestedAt(OffsetDateTime.now())
                        .build());

        TriggerCommand cmd = TriggerCommand.builder()
                .kind("crawl")
                .code("123456")
                .requestedBy(admin)
                .build();

        TriggerRequest result = service.queue(cmd);

        assertThat(result.getStatus()).isEqualTo(TriggerStatus.QUEUED);
        verify(crawlerTriggerGateway).queue(TriggerKind.CRAWL, admin);
    }

    @Test
    @DisplayName("TC-384-J2: gateway denies (2FA enabled, code missing) -> VerificationRequiredException, no queue call")
    void missingCodeOnTwoFactorAdminIsDenied() {
        UUID admin = UUID.randomUUID();
        doThrow(new VerificationRequiredException("code required"))
                .when(twoFactorGateway).verify(eq(admin), isNull());

        TriggerCommand cmd = TriggerCommand.builder()
                .kind("crawl")
                .requestedBy(admin)
                .build();

        assertThatThrownBy(() -> service.queue(cmd))
                .isInstanceOf(VerificationRequiredException.class);

        verifyNoInteractions(crawlerTriggerGateway);
    }

    @Test
    @DisplayName("TC-384-J3: gateway denies (wrong/expired code) -> VerificationRequiredException")
    void wrongCodeOnTwoFactorAdminIsDenied() {
        UUID admin = UUID.randomUUID();
        doThrow(new VerificationRequiredException("code invalid"))
                .when(twoFactorGateway).verify(admin, "999999");

        TriggerCommand cmd = TriggerCommand.builder()
                .kind("crawl")
                .code("999999")
                .requestedBy(admin)
                .build();

        assertThatThrownBy(() -> service.queue(cmd))
                .isInstanceOf(VerificationRequiredException.class);
    }

    @Test
    @DisplayName("TC-384-J4: gateway throttles -> VerificationThrottledException, no queue call")
    void throttledGatewayThrowsThrottledException() {
        UUID admin = UUID.randomUUID();
        doThrow(new VerificationThrottledException("too many attempts"))
                .when(twoFactorGateway).verify(admin, "123456");

        TriggerCommand cmd = TriggerCommand.builder()
                .kind("crawl")
                .code("123456")
                .requestedBy(admin)
                .build();

        assertThatThrownBy(() -> service.queue(cmd))
                .isInstanceOf(VerificationThrottledException.class);

        verifyNoInteractions(crawlerTriggerGateway);
    }

    @Test
    @DisplayName("TC-384-J5: not-enrolled admin, no code -> authorized, gateway.queue called")
    void notEnrolledAdminWithNoCodeIsAuthorized() {
        UUID admin = UUID.randomUUID();
        doNothing().when(twoFactorGateway).verify(eq(admin), isNull());
        when(crawlerTriggerGateway.queue(TriggerKind.CRAWL, admin)).thenReturn(
                TriggerRequest.builder()
                        .id(UUID.randomUUID())
                        .kind(TriggerKind.CRAWL)
                        .status(TriggerStatus.QUEUED)
                        .requestedBy(admin)
                        .requestedAt(OffsetDateTime.now())
                        .build());

        TriggerCommand cmd = TriggerCommand.builder()
                .kind("crawl")
                .requestedBy(admin)
                .build();

        TriggerRequest result = service.queue(cmd);

        assertThat(result.getStatus()).isEqualTo(TriggerStatus.QUEUED);
        verify(crawlerTriggerGateway).queue(TriggerKind.CRAWL, admin);
    }

    @Test
    @DisplayName("TC-384-J6/BR-384-5: not-enrolled admin supplies a code anyway -> still authorized, code ignored")
    void notEnrolledAdminWithCodeSuppliedIsStillAuthorized() {
        UUID admin = UUID.randomUUID();
        doNothing().when(twoFactorGateway).verify(admin, "123456");
        when(crawlerTriggerGateway.queue(TriggerKind.CRAWL, admin)).thenReturn(
                TriggerRequest.builder()
                        .id(UUID.randomUUID())
                        .kind(TriggerKind.CRAWL)
                        .status(TriggerStatus.QUEUED)
                        .requestedBy(admin)
                        .requestedAt(OffsetDateTime.now())
                        .build());

        TriggerCommand cmd = TriggerCommand.builder()
                .kind("crawl")
                .code("123456")
                .requestedBy(admin)
                .build();

        TriggerRequest result = service.queue(cmd);

        assertThat(result.getStatus()).isEqualTo(TriggerStatus.QUEUED);
        verify(crawlerTriggerGateway).queue(TriggerKind.CRAWL, admin);
    }

    @Test
    @DisplayName("TC-384-J7/BR-384-4: triggering disabled short-circuits before the 2FA gate is ever reached")
    void disabledGateShortCircuitsBeforeTwoFactorGate() {
        AdminTriggerService disabled = new AdminTriggerService(repository, twoFactorGateway, crawlerTriggerGateway, false);

        TriggerCommand cmd = TriggerCommand.builder()
                .kind("crawl")
                .requestedBy(UUID.randomUUID())
                .build();

        assertThatThrownBy(() -> disabled.queue(cmd))
                .isInstanceOf(TriggeringDisabledException.class);

        verifyNoInteractions(twoFactorGateway);
        verifyNoInteractions(crawlerTriggerGateway);
    }

    // ── TC-384-J13: format validation before any gateway call ──────────────────

    @ParameterizedTest(name = "TC-384-J13: malformed code ''{0}'' -> 400, gateway.verify never invoked")
    @ValueSource(strings = {"abc", "1234567", "12345", "abcdefgh1", "!!!!!!!!"})
    void malformedCodeIsRejectedBeforeGatewayCall(String malformedCode) {
        TriggerCommand cmd = TriggerCommand.builder()
                .kind("crawl")
                .code(malformedCode)
                .requestedBy(UUID.randomUUID())
                .build();

        assertThatThrownBy(() -> service.queue(cmd))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(twoFactorGateway);
        verifyNoInteractions(crawlerTriggerGateway);
    }

    // ── TC-384-J9: cancel is never gated by 2FA (BR-384-7) ──────────────────────

    @Test
    @DisplayName("TC-384-J9/BR-384-7: cancel never touches the 2FA gateway")
    void cancelNeverTouchesTheTwoFactorGateway() {
        UUID id = UUID.randomUUID();
        TriggerRequest queued = TriggerRequest.builder()
                .id(id)
                .kind(TriggerKind.CRAWL)
                .status(TriggerStatus.CANCELLED)
                .requestedAt(OffsetDateTime.now().minusMinutes(5))
                .build();

        when(crawlerTriggerGateway.cancel(TriggerKind.CRAWL)).thenReturn(queued);

        service.cancel(TriggerKind.CRAWL);

        verifyNoInteractions(twoFactorGateway);
    }

    // ── TC-384-J10..J12: getStatus per-caller 2FA signal ────────────────────────

    @Test
    @DisplayName("TC-384-J10: gateway.isEnabled=true -> twoFactorRequired=true")
    void statusReportsTwoFactorRequiredTrue() {
        UUID admin = UUID.randomUUID();
        when(twoFactorGateway.isEnabled(admin)).thenReturn(true);

        TriggerStatusOverview overview = service.getStatus(admin);

        assertThat(overview.isTwoFactorRequired()).isTrue();
    }

    @Test
    @DisplayName("TC-384-J11: gateway.isEnabled=false -> twoFactorRequired=false")
    void statusReportsTwoFactorRequiredFalse() {
        UUID admin = UUID.randomUUID();
        when(twoFactorGateway.isEnabled(admin)).thenReturn(false);

        TriggerStatusOverview overview = service.getStatus(admin);

        assertThat(overview.isTwoFactorRequired()).isFalse();
    }

    @ParameterizedTest(name = "TC-384-J12: triggerEnabled=false regardless of isEnabled={0}")
    @ValueSource(booleans = {true, false})
    void triggerEnabledIsIndependentOfTwoFactorState(boolean adminHasTwoFactor) {
        UUID admin = UUID.randomUUID();
        AdminTriggerService disabled = new AdminTriggerService(repository, twoFactorGateway, crawlerTriggerGateway, false);
        when(twoFactorGateway.isEnabled(admin)).thenReturn(adminHasTwoFactor);

        TriggerStatusOverview overview = disabled.getStatus(admin);

        assertThat(overview.isTriggerEnabled()).isFalse();
        assertThat(overview.isTwoFactorRequired()).isEqualTo(adminHasTwoFactor);
    }

    // ── getStatus reads (unaffected by ADR 0033: still direct repository reads) ──

    @Test
    @DisplayName("getStatus reads via the repository, never the crawler gateway")
    void getStatusNeverTouchesTheCrawlerGateway() {
        UUID admin = UUID.randomUUID();
        when(twoFactorGateway.isEnabled(admin)).thenReturn(false);

        service.getStatus(admin);

        verify(repository).findMostRecent(TriggerKind.CRAWL);
        verify(repository).findMostRecent(TriggerKind.ENRICHMENT);
        verify(repository).findLastFinished(TriggerKind.CRAWL);
        verify(repository).findLastFinished(TriggerKind.ENRICHMENT);
        verifyNoInteractions(crawlerTriggerGateway);
    }
}
