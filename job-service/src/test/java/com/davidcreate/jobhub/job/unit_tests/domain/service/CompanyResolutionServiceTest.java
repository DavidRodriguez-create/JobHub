package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.UnresolvedTarget;
import com.davidcreate.jobhub.job.domain.port.out.CompanyRepository;
import com.davidcreate.jobhub.job.domain.port.out.PullTargetRepository;
import com.davidcreate.jobhub.job.domain.service.CompanyResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyResolutionService Unit Tests")
class CompanyResolutionServiceTest {

    private static final int BATCH_SIZE = 200;

    @Mock
    CompanyRepository companyRepo;

    @Mock
    PullTargetRepository targetRepo;

    CompanyResolutionService service;

    @BeforeEach
    void setUp() {
        service = new CompanyResolutionService(companyRepo, targetRepo, BATCH_SIZE);
    }

    private static UnresolvedTarget target(UUID id, String companyName) {
        return UnresolvedTarget.builder().id(id).companyName(companyName).build();
    }

    private static Company company(UUID id, String slug, String name) {
        return Company.builder().id(id).slug(slug).name(name).build();
    }

    // ── QAE-428-SVC-01 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-SVC-01: one pending resolvable target -> findBySlug (miss), then "
            + "upsertBySlug, then assignCompany, in that exact order")
    void resolvesOnePendingTargetInStrictOrder() {
        UUID targetId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(targetRepo.findWithoutCompany(BATCH_SIZE)).thenReturn(List.of(target(targetId, "Stripe")));
        when(companyRepo.findBySlug("stripe")).thenReturn(Optional.empty());
        when(companyRepo.upsertBySlug(any())).thenReturn(company(companyId, "stripe", "Stripe"));

        int resolved = service.resolvePending();

        assertThat(resolved).isEqualTo(1);
        InOrder order = inOrder(companyRepo, targetRepo);
        order.verify(companyRepo).findBySlug("stripe");
        order.verify(companyRepo).upsertBySlug(any());
        order.verify(targetRepo).assignCompany(targetId, companyId);
    }

    // ── QAE-428-SVC-02 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-SVC-02: two pending targets slugging to the SAME value -> both "
            + "assigned the SAME company id, no duplicate company created")
    void twoTargetsSameSlugAssignedSameCompanyId() {
        UUID target1 = UUID.randomUUID();
        UUID target2 = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Company nestle = company(companyId, "nestle", "Nestle");

        when(targetRepo.findWithoutCompany(BATCH_SIZE)).thenReturn(List.of(
                target(target1, "Nestlé S.A."),
                target(target2, "NESTLE SA")));
        when(companyRepo.findBySlug("nestle"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(nestle));
        when(companyRepo.upsertBySlug(any())).thenReturn(nestle);

        int resolved = service.resolvePending();

        assertThat(resolved).isEqualTo(2);
        verify(targetRepo).assignCompany(target1, companyId);
        verify(targetRepo).assignCompany(target2, companyId);
        verify(companyRepo, times(1)).upsertBySlug(any());
    }

    // ── QAE-428-SVC-03 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-SVC-03: a no-slug target is skipped entirely (no companyRepo/assignCompany "
            + "calls, no exception), and another pending target in the same batch still resolves normally")
    void noSlugTargetSkippedOthersStillProcessed() {
        UUID noSlugTargetId = UUID.randomUUID();
        UUID resolvableTargetId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(targetRepo.findWithoutCompany(BATCH_SIZE)).thenReturn(List.of(
                target(noSlugTargetId, "楽天"),
                target(resolvableTargetId, "Stripe")));
        when(companyRepo.findBySlug("stripe")).thenReturn(Optional.empty());
        when(companyRepo.upsertBySlug(any())).thenReturn(company(companyId, "stripe", "Stripe"));

        int resolved = service.resolvePending();

        assertThat(resolved).isEqualTo(1);
        verify(targetRepo, never()).assignCompany(eq(noSlugTargetId), any());
        verify(targetRepo).assignCompany(resolvableTargetId, companyId);
        verify(companyRepo, never()).findBySlug("");
    }

    // ── QAE-428-SVC-04 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-SVC-04: empty pending list -> zero companyRepo invocations, returns 0")
    void emptyPendingListMakesNoWork() {
        when(targetRepo.findWithoutCompany(BATCH_SIZE)).thenReturn(List.of());

        int resolved = service.resolvePending();

        assertThat(resolved).isZero();
        verifyNoInteractions(companyRepo);
    }

    // ── QAE-429-SVC-01 (revised ADR 0024) ─────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-SVC-01 (AC-429-01): a genuine insert is born with a null logo_url - "
            + "logos are curated (db/init/052) / admin-filled (#430), never derived here")
    void insertBranchThreadsNullLogoUrl() {
        UUID targetId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(targetRepo.findWithoutCompany(BATCH_SIZE)).thenReturn(List.of(target(targetId, "Globex")));
        when(companyRepo.findBySlug("globex")).thenReturn(Optional.empty());
        when(companyRepo.upsertBySlug(any())).thenReturn(company(companyId, "globex", "Globex"));

        service.resolvePending();

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepo).upsertBySlug(captor.capture());
        assertThat(captor.getValue().getLogoUrl()).isNull();
    }

    // ── QAE-429-SVC-02 (AC-429-05/10): the insert-only no-overwrite guarantee ──────────

    @Test
    @DisplayName("QAE-429-SVC-02 (AC-429-05/10): lookup-hit on a manually-edited, "
            + "intentionally-cleared company never re-inserts or overwrites - upsertBySlug is "
            + "never called, assignCompany uses the existing company's id")
    void lookupHitManuallyEditedClearedLogoNeverOverwritten() {
        UUID targetId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Company existing = Company.builder()
                .id(companyId).slug("manual-cleared").name("Manual Cleared")
                .manuallyEdited(true).logoUrl(null).build();

        when(targetRepo.findWithoutCompany(BATCH_SIZE))
                .thenReturn(List.of(target(targetId, "Manual Cleared")));
        when(companyRepo.findBySlug("manual-cleared")).thenReturn(Optional.of(existing));

        service.resolvePending();

        verify(companyRepo, never()).upsertBySlug(any());
        verify(targetRepo).assignCompany(targetId, companyId);
    }

    @Test
    @DisplayName("QAE-429-SVC-02 (AC-429-05/10): lookup-hit on an already-resolved, "
            + "non-manual company (existing logoUrl set) never re-inserts or overwrites")
    void lookupHitAlreadyResolvedCompanyNeverOverwritten() {
        UUID targetId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Company existing = Company.builder()
                .id(companyId).slug("acme").name("Acme")
                .manuallyEdited(false).logoUrl("https://acme.com/favicon.ico").build();

        when(targetRepo.findWithoutCompany(BATCH_SIZE)).thenReturn(List.of(target(targetId, "Acme")));
        when(companyRepo.findBySlug("acme")).thenReturn(Optional.of(existing));

        service.resolvePending();

        verify(companyRepo, never()).upsertBySlug(any());
        verify(targetRepo).assignCompany(targetId, companyId);
    }

    // ── QAE-429-SVC-03 (AC-429-07, regression) ────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-SVC-03 (AC-429-07, regression): a no-slug target is still skipped "
            + "before any company work - zero companyRepo interactions")
    void noSlugTargetSkipsBeforeAnyCompanyWork() {
        UUID noSlugTargetId = UUID.randomUUID();
        when(targetRepo.findWithoutCompany(BATCH_SIZE))
                .thenReturn(List.of(target(noSlugTargetId, "楽天")));

        int resolved = service.resolvePending();

        assertThat(resolved).isZero();
        verifyNoInteractions(companyRepo);
    }
}
