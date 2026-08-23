package com.davidcreate.jobhub.application.unit_tests.application.usecase;

import com.davidcreate.jobhub.application.application.port.out.ApplicationRepository;
import com.davidcreate.jobhub.application.application.port.out.ApplicationTimelineRepository;
import com.davidcreate.jobhub.application.application.port.out.JobPostSnapshotRepository;
import com.davidcreate.jobhub.application.application.port.out.UserJobPostRepository;
import com.davidcreate.jobhub.application.application.usecase.ApplicationService;
import com.davidcreate.jobhub.application.domain.entity.Application;
import com.davidcreate.jobhub.application.domain.exception.AlreadyTerminalException;
import com.davidcreate.jobhub.application.domain.exception.ApplicationNotFoundException;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import com.davidcreate.jobhub.application.domain.valueobject.JobInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GA-APP-09, GA-APP-10, GA-APP-19, GA-APP-20, GA-APP-21
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationService — Ghosted Alert unit tests")
class GhostedAlertServiceTest {

    @Mock
    ApplicationRepository applicationRepository;
    @Mock
    ApplicationTimelineRepository timelineRepository;
    @Mock
    UserJobPostRepository userJobPostRepository;
    @Mock
    JobPostSnapshotRepository snapshotRepository;
    @Mock
    com.davidcreate.jobhub.application.application.port.out.JobPostGateway jobPostGateway;
    @Mock
    com.davidcreate.jobhub.application.application.port.out.VerificationGateway verificationGateway;

    @InjectMocks
    ApplicationService service;

    // ── GA-APP-09 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-09: listStaleApplications passes cutoff = now minus days to repository")
    void listStale_passesCutoffToRepository() {
        int days = 14;
        when(applicationRepository.findNonTerminalStaleApplications(any())).thenReturn(List.of());

        service.listStaleApplications(days);

        ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(applicationRepository).findNonTerminalStaleApplications(captor.capture());

        OffsetDateTime captured = captor.getValue();
        OffsetDateTime expectedMin = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days).minusSeconds(5);
        OffsetDateTime expectedMax = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days).plusSeconds(5);

        assertThat(captured).isAfter(expectedMin).isBefore(expectedMax);
    }

    // ── GA-APP-10 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-10: findNonTerminalStaleApplications is called (non-terminal scope is enforced by the repo contract)")
    void listStale_repositoryCalledForNonTerminalOnly() {
        // The service must only call findNonTerminalStaleApplications, NOT the general find.
        // We verify it never calls listByUser (which would include terminal statuses).
        when(applicationRepository.findNonTerminalStaleApplications(any())).thenReturn(List.of());

        service.listStaleApplications(7);

        verify(applicationRepository).findNonTerminalStaleApplications(any());
        verify(applicationRepository, never()).listByUser(any(), any(), any(int.class), any(int.class));
    }

    // ── GA-APP-19 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-19: updateApplicationStatusInternal stamps endedAt and appends timeline for GHOSTED")
    void updateStatusInternal_ghostedStampsEndedAt() {
        UUID appId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        Application existing = Application.builder()
                .id(appId)
                .userId(UUID.randomUUID())
                .status(ApplicationStatus.APPLIED)
                .appliedAt(now.minusDays(20))
                .build();

        when(applicationRepository.findOneById(appId)).thenReturn(Optional.of(existing));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateApplicationStatusInternal(appId, ApplicationStatus.GHOSTED);

        ArgumentCaptor<Application> savedCaptor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(savedCaptor.capture());
        Application saved = savedCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.GHOSTED);
        assertThat(saved.getEndedAt()).isNotNull();

        verify(timelineRepository).append(eq(appId), eq(ApplicationStatus.GHOSTED), any());
    }

    // ── GA-APP-20 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-20: updateApplicationStatusInternal throws AlreadyTerminalException when app is already terminal")
    void updateStatusInternal_alreadyTerminalThrows() {
        UUID appId = UUID.randomUUID();
        Application terminal = Application.builder()
                .id(appId)
                .userId(UUID.randomUUID())
                .status(ApplicationStatus.REJECTED)
                .appliedAt(OffsetDateTime.now().minusDays(30))
                .build();

        when(applicationRepository.findOneById(appId)).thenReturn(Optional.of(terminal));

        assertThatThrownBy(() -> service.updateApplicationStatusInternal(appId, ApplicationStatus.GHOSTED))
                .isInstanceOf(AlreadyTerminalException.class);

        verify(applicationRepository, never()).save(any());
        verify(timelineRepository, never()).append(any(), any(), any());
    }

    // ── GA-APP-21 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-21: updateApplicationStatusInternal throws ApplicationNotFoundException for unknown id")
    void updateStatusInternal_unknownIdThrows() {
        UUID appId = UUID.randomUUID();
        when(applicationRepository.findOneById(appId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateApplicationStatusInternal(appId, ApplicationStatus.GHOSTED))
                .isInstanceOf(ApplicationNotFoundException.class);

        verify(applicationRepository, never()).save(any());
    }
}
