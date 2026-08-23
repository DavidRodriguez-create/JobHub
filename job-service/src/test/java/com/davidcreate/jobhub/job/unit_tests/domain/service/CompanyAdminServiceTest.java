package com.davidcreate.jobhub.job.unit_tests.domain.service;

import com.davidcreate.jobhub.job.domain.exception.CompanyNotFoundException;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.CompanyPage;
import com.davidcreate.jobhub.job.domain.model.CompanySearchQuery;
import com.davidcreate.jobhub.job.domain.model.CompanySortOrder;
import com.davidcreate.jobhub.job.domain.model.CompanyUpdateCommand;
import com.davidcreate.jobhub.job.domain.port.out.CompanyRepository;
import com.davidcreate.jobhub.job.domain.service.CompanyAdminService;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyAdminService Unit Tests (Story #430)")
class CompanyAdminServiceTest {

    @Mock
    CompanyRepository repository;

    CompanyAdminService service;

    @BeforeEach
    void setUp() {
        service = new CompanyAdminService(repository);
    }

    private static Company existing(UUID id, String slug, String name) {
        return Company.builder().id(id).slug(slug).name(name).manuallyEdited(false).build();
    }

    private static CompanyUpdateCommand fullCommand() {
        return CompanyUpdateCommand.builder()
                .website("https://acme.com")
                .industry("Fintech")
                .size("51-200")
                .headquarters("Barcelona, Spain")
                .description("A fintech company.")
                .tags(List.of("remote-first", "b2b"))
                .logoUrl("https://acme.com/logo.png")
                .build();
    }

    // ── QAE-430-SVC-01 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-SVC-01: list() passes q/manuallyEdited/sort/page/size through to the "
            + "repository unchanged, and returns the repo's page + total without re-filtering")
    void listPassesQueryThroughUnchanged() {
        CompanySearchQuery query = CompanySearchQuery.builder()
                .q("strip")
                .manuallyEdited(false)
                .sort(CompanySortOrder.UPDATED_DESC)
                .page(2)
                .size(10)
                .build();
        CompanyPage page = new CompanyPage(List.of(existing(UUID.randomUUID(), "stripe", "Stripe")), 45L);
        when(repository.search(any())).thenReturn(page);

        CompanyPage result = service.list(query);

        ArgumentCaptor<CompanySearchQuery> captor = ArgumentCaptor.forClass(CompanySearchQuery.class);
        verify(repository).search(captor.capture());
        CompanySearchQuery captured = captor.getValue();
        assertThat(captured.getQ()).isEqualTo("strip");
        assertThat(captured.getManuallyEdited()).isFalse();
        assertThat(captured.getSort()).isEqualTo(CompanySortOrder.UPDATED_DESC);
        assertThat(captured.getPage()).isEqualTo(2);
        assertThat(captured.getSize()).isEqualTo(10);
        assertThat(result).isSameAs(page);
    }

    // ── QAE-430-SVC-02 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-SVC-02: getById() on a repository hit returns the mapped Company")
    void getByIdReturnsMappedCompanyOnHit() {
        UUID id = UUID.randomUUID();
        Company company = existing(id, "stripe", "Stripe");
        when(repository.findCompanyById(id)).thenReturn(Optional.of(company));

        assertThat(service.getById(id)).isSameAs(company);
    }

    @Test
    @DisplayName("QAE-430-SVC-02: getById() on a repository miss throws CompanyNotFoundException, "
            + "never returns null or an empty optional silently")
    void getByIdThrowsOnMiss() {
        UUID id = UUID.randomUUID();
        when(repository.findCompanyById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(CompanyNotFoundException.class);
    }

    // ── QAE-430-SVC-03 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-SVC-03: update() full-replace mapping - the Company argument captured "
            + "on the repository's write call has every field set exactly from the request, "
            + "nulls mapped to null, never left at the existing row's value")
    void updateMapsEveryFieldExactlyFromRequestNoPartialMerge() {
        UUID id = UUID.randomUUID();
        Company existing = Company.builder()
                .id(id).slug("acme").name("Acme")
                .website("https://old.example.com").industry("Old Industry")
                .manuallyEdited(false).build();
        when(repository.findCompanyById(id)).thenReturn(Optional.of(existing));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanyUpdateCommand command = CompanyUpdateCommand.builder()
                .website("https://new.example.com")
                .industry(null)
                .size(null)
                .headquarters("Berlin, Germany")
                .description(null)
                .tags(null)
                .logoUrl(null)
                .build();

        service.update(id, command);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(repository).update(captor.capture());
        Company captured = captor.getValue();
        assertThat(captured.getWebsite()).isEqualTo("https://new.example.com");
        assertThat(captured.getIndustry()).isNull();
        assertThat(captured.getSize()).isNull();
        assertThat(captured.getHeadquarters()).isEqualTo("Berlin, Germany");
        assertThat(captured.getDescription()).isNull();
        assertThat(captured.getTags()).isNull();
        assertThat(captured.getLogoUrl()).isNull();
    }

    // ── QAE-430-SVC-04 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-SVC-04: update() always persists the path {id} and the EXISTING row's "
            + "slug/name on the captured argument")
    void updatePersistsPathIdAndExistingSlugName() {
        UUID id = UUID.randomUUID();
        Company existing = Company.builder().id(id).slug("acme").name("Acme Corp").manuallyEdited(false).build();
        when(repository.findCompanyById(id)).thenReturn(Optional.of(existing));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, fullCommand());

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(repository).update(captor.capture());
        Company captured = captor.getValue();
        assertThat(captured.getId()).isEqualTo(id);
        assertThat(captured.getSlug()).isEqualTo("acme");
        assertThat(captured.getName()).isEqualTo("Acme Corp");
    }

    // ── QAE-430-SVC-05 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-SVC-05: update() always sets manuallyEdited=true and a fresh updatedAt "
            + "on the captured argument, including a no-op resubmission")
    void updateAlwaysPinsRecordAndStampsFreshUpdatedAt() {
        UUID id = UUID.randomUUID();
        Company existing = Company.builder()
                .id(id).slug("acme").name("Acme")
                .website(fullCommand().getWebsite())
                .industry(fullCommand().getIndustry())
                .size(fullCommand().getSize())
                .headquarters(fullCommand().getHeadquarters())
                .description(fullCommand().getDescription())
                .tags(fullCommand().getTags())
                .logoUrl(fullCommand().getLogoUrl())
                .manuallyEdited(false)
                .build();
        when(repository.findCompanyById(id)).thenReturn(Optional.of(existing));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, fullCommand());

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(repository).update(captor.capture());
        Company captured = captor.getValue();
        assertThat(captured.getManuallyEdited()).isTrue();
        assertThat(captured.getUpdatedAt()).isNotNull();
    }

    // ── QAE-430-SVC-06 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-SVC-06: update() rejects a request carrying duplicate tags BEFORE "
            + "calling the repository's write method")
    void updateRejectsDuplicateTagsBeforeAnyWrite() {
        UUID id = UUID.randomUUID();
        when(repository.findCompanyById(id)).thenReturn(Optional.of(existing(id, "acme", "Acme")));

        CompanyUpdateCommand command = CompanyUpdateCommand.builder()
                .tags(List.of("remote-first", "remote-first"))
                .build();

        assertThatThrownBy(() -> service.update(id, command)).isInstanceOf(BadRequestException.class);
        verify(repository, never()).update(any());
    }

    // ── QAE-430-SVC-07 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-SVC-07: update() maps an empty tags array to a stored null (never [])")
    void updateMapsEmptyTagsToNull() {
        UUID id = UUID.randomUUID();
        when(repository.findCompanyById(id)).thenReturn(Optional.of(existing(id, "acme", "Acme")));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanyUpdateCommand command = CompanyUpdateCommand.builder().tags(List.of()).build();

        service.update(id, command);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(repository).update(captor.capture());
        assertThat(captor.getValue().getTags()).isNull();
    }

    // ── QAE-430-SVC-08 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-SVC-08: update()'s repository call is the ONLY write path invoked - "
            + "no other repository method is called besides the read that checks existence")
    void updateInvokesNoOtherRepositoryMethod() {
        UUID id = UUID.randomUUID();
        when(repository.findCompanyById(id)).thenReturn(Optional.of(existing(id, "acme", "Acme")));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, fullCommand());

        verify(repository).findCompanyById(id);
        verify(repository).update(any());
        verifyNoMoreInteractions(repository);
    }

    // ── Validation (AC-430-31..36) ────────────────────────────────────────────

    @Test
    @DisplayName("A tag violating the lowercase-kebab-case grammar is rejected before any write")
    void rejectsBadTagPattern() {
        UUID id = UUID.randomUUID();
        when(repository.findCompanyById(id)).thenReturn(Optional.of(existing(id, "acme", "Acme")));

        CompanyUpdateCommand command = CompanyUpdateCommand.builder().tags(List.of("Remote First")).build();

        assertThatThrownBy(() -> service.update(id, command)).isInstanceOf(BadRequestException.class);
        verify(repository, never()).update(any());
    }

    @Test
    @DisplayName("More than 20 tags is rejected before any write")
    void rejectsTooManyTags() {
        UUID id = UUID.randomUUID();
        when(repository.findCompanyById(id)).thenReturn(Optional.of(existing(id, "acme", "Acme")));

        List<String> tooMany = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "tag-" + i).toList();
        CompanyUpdateCommand command = CompanyUpdateCommand.builder().tags(tooMany).build();

        assertThatThrownBy(() -> service.update(id, command)).isInstanceOf(BadRequestException.class);
        verify(repository, never()).update(any());
    }

    @Test
    @DisplayName("An over-length industry value is rejected before any write")
    void rejectsOverLengthIndustry() {
        UUID id = UUID.randomUUID();
        when(repository.findCompanyById(id)).thenReturn(Optional.of(existing(id, "acme", "Acme")));

        CompanyUpdateCommand command = CompanyUpdateCommand.builder()
                .industry("x".repeat(81))
                .build();

        assertThatThrownBy(() -> service.update(id, command)).isInstanceOf(BadRequestException.class);
        verify(repository, never()).update(any());
    }

    @Test
    @DisplayName("update() on an unknown id throws CompanyNotFoundException before validation runs")
    void updateOnUnknownIdThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findCompanyById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, fullCommand()))
                .isInstanceOf(CompanyNotFoundException.class);
        verify(repository, never()).update(any());
    }
}
