package com.davidcreate.jobhub.job.adapter.in.rest;

import com.davidcreate.jobhub.job.adapter.in.rest.dto.CompanyAdminResponseMapper;
import com.davidcreate.jobhub.job.adapter.in.rest.dto.JobFacetsResponseMapper;
import com.davidcreate.jobhub.job.adapter.in.rest.dto.JobPostResponseMapper;
import com.davidcreate.jobhub.job.adapter.in.rest.dto.SavedFilterResponseMapper;
import com.davidcreate.jobhub.job.adapter.in.rest.dto.SavedJobResponseMapper;
import com.davidcreate.jobhub.job.adapter.in.rest.dto.TriggerResponseMapper;
import com.davidcreate.jobhub.job.adapter.in.rest.dto.TriggerStatusMapper;
import com.davidcreate.jobhub.job.contract.api.JobsApi;
import com.davidcreate.jobhub.job.contract.model.CompanyInfo;
import com.davidcreate.jobhub.job.contract.model.CompanyUpdateRequest;
import com.davidcreate.jobhub.job.contract.model.SavedFilterPatchRequest;
import com.davidcreate.jobhub.job.contract.model.SavedFilterRequest;
import com.davidcreate.jobhub.job.contract.model.SavedFilterResponse;
import com.davidcreate.jobhub.job.contract.model.TriggerRequestBody;
import com.davidcreate.jobhub.job.domain.exception.JobNotFoundException;
import com.davidcreate.jobhub.job.domain.model.CareerLevel;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.CompanyPage;
import com.davidcreate.jobhub.job.domain.model.CompanySearchQuery;
import com.davidcreate.jobhub.job.domain.model.CompanySortOrder;
import com.davidcreate.jobhub.job.domain.model.CompanyUpdateCommand;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobCount;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.model.SavedFilter;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.job.domain.model.JobSortOrder;
import com.davidcreate.jobhub.job.domain.model.PostedWithin;
import com.davidcreate.jobhub.job.domain.model.TriggerCommand;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;
import com.davidcreate.jobhub.job.domain.port.in.AdminCompanyQueryUseCase;
import com.davidcreate.jobhub.job.domain.port.in.AdminTriggerUseCase;
import com.davidcreate.jobhub.job.domain.port.in.GetJobFacetsUseCase;
import com.davidcreate.jobhub.job.domain.port.in.GetJobUseCase;
import com.davidcreate.jobhub.job.domain.port.in.SavedFilterUseCase;
import com.davidcreate.jobhub.job.domain.port.in.SavedJobUseCase;
import com.davidcreate.jobhub.job.domain.port.in.SearchJobsUseCase;
import com.davidcreate.jobhub.job.domain.port.in.UpdateCompanyUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

@Path("/jobs")
@ApplicationScoped
public class JobResource implements JobsApi {

    private final SearchJobsUseCase searchJobsUseCase;
    private final GetJobUseCase getJobUseCase;
    private final GetJobFacetsUseCase getJobFacetsUseCase;
    private final SavedJobUseCase savedJobUseCase;
    private final SavedFilterUseCase savedFilterUseCase;
    private final AdminTriggerUseCase adminTriggerUseCase;
    private final AdminCompanyQueryUseCase adminCompanyQueryUseCase;
    private final UpdateCompanyUseCase updateCompanyUseCase;
    private final JsonWebToken jwt;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "job.search.max-size", defaultValue = "100")
    int maxSize;

    public JobResource(SearchJobsUseCase searchJobsUseCase,
                       GetJobUseCase getJobUseCase,
                       GetJobFacetsUseCase getJobFacetsUseCase,
                       SavedJobUseCase savedJobUseCase,
                       SavedFilterUseCase savedFilterUseCase,
                       AdminTriggerUseCase adminTriggerUseCase,
                       AdminCompanyQueryUseCase adminCompanyQueryUseCase,
                       UpdateCompanyUseCase updateCompanyUseCase,
                       JsonWebToken jwt,
                       ObjectMapper objectMapper) {
        this.searchJobsUseCase = searchJobsUseCase;
        this.getJobUseCase = getJobUseCase;
        this.getJobFacetsUseCase = getJobFacetsUseCase;
        this.savedJobUseCase = savedJobUseCase;
        this.savedFilterUseCase = savedFilterUseCase;
        this.adminTriggerUseCase = adminTriggerUseCase;
        this.adminCompanyQueryUseCase = adminCompanyQueryUseCase;
        this.updateCompanyUseCase = updateCompanyUseCase;
        this.jwt = jwt;
        this.objectMapper = objectMapper;
    }

    // ── Job search (public) ───────────────────────────────────────────────────

    @Override
    public Response searchJobs(String keyword,
                               List<String> location,
                               List<String> language,
                               List<String> company,
                               List<String> employmentType,
                               List<String> careerLevel,
                               Integer compensationMin,
                               Integer compensationMax,
                               String postedWithin,
                               String sort,
                               Integer page,
                               Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        validatePagination(p, s);

        JobSearchQuery query = JobSearchQuery.builder()
                .keyword(keyword)
                .locations(location)
                .languages(language)
                .companies(company)
                .employmentTypes(parseEmploymentTypes(employmentType))
                .careerLevels(parseCareerLevels(careerLevel))
                .compensationMin(compensationMin)
                .compensationMax(compensationMax)
                .postedWithin(parsePostedWithin(postedWithin))
                .sort(parseSort(sort))
                .page(p)
                .size(s)
                .build();

        List<JobPost> jobs = searchJobsUseCase.search(query);
        JobCount total = searchJobsUseCase.count(query);

        return Response.ok(JobPostResponseMapper.toPage(jobs, p, s, total)).build();
    }

    @Override
    public Response getJob(UUID id) {
        return getJobUseCase.getById(id)
                .map(JobPostResponseMapper::toResponse)
                .map(r -> Response.ok(r).build())
                .orElseThrow(() -> new JobNotFoundException("Job with id " + id + " not found"));
    }

    @Override
    public Response getJobFacets(String keyword,
                                 List<String> location,
                                 List<String> language,
                                 List<String> company,
                                 List<String> employmentType,
                                 List<String> careerLevel,
                                 Integer compensationMin,
                                 Integer compensationMax,
                                 String postedWithin) {
        JobSearchQuery query = JobSearchQuery.builder()
                .keyword(keyword)
                .locations(location)
                .languages(language)
                .companies(company)
                .employmentTypes(parseEmploymentTypes(employmentType))
                .careerLevels(parseCareerLevels(careerLevel))
                .compensationMin(compensationMin)
                .compensationMax(compensationMax)
                .postedWithin(parsePostedWithin(postedWithin))
                .build();
        return Response.ok(JobFacetsResponseMapper.toResponse(getJobFacetsUseCase.getFacets(query))).build();
    }

    // ── Saved Jobs (authenticated) ────────────────────────────────────────────

    @Override
    @RolesAllowed("user")
    public Response listSavedJobs(Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        validatePagination(p, s);
        return Response.ok(SavedJobResponseMapper.toPage(savedJobUseCase.list(userId(), p, s), p, s)).build();
    }

    @Override
    @RolesAllowed("user")
    public Response saveJob(UUID jobId) {
        savedJobUseCase.save(userId(), jobId);
        return Response.noContent().build();
    }

    @Override
    @RolesAllowed("user")
    public Response unsaveJob(UUID jobId) {
        savedJobUseCase.unsave(userId(), jobId);
        return Response.noContent().build();
    }

    // ── Saved Filters (authenticated) ─────────────────────────────────────────

    @Override
    @RolesAllowed("user")
    public Response listSavedFilters() {
        List<SavedFilterResponse> body = savedFilterUseCase.list(userId()).stream()
                .map(f -> SavedFilterResponseMapper.toResponse(f, objectMapper))
                .toList();
        return Response.ok(body).build();
    }

    @Override
    @RolesAllowed("user")
    public Response createSavedFilter(SavedFilterRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BadRequestException("name is required");
        }
        if (req.getFilters() == null) {
            throw new BadRequestException("filters is required");
        }
        validateNameLength(req.getName());
        String filtersJson = SavedFilterResponseMapper.toJson(req.getFilters(), objectMapper);
        SavedFilter created = savedFilterUseCase.create(userId(), req.getName(), filtersJson);
        return Response.status(Response.Status.CREATED)
                .entity(SavedFilterResponseMapper.toResponse(created, objectMapper))
                .build();
    }

    @Override
    @RolesAllowed("user")
    public Response updateSavedFilter(UUID id, SavedFilterPatchRequest req) {
        if (req.getName() == null && req.getFilters() == null) {
            throw new BadRequestException("patch body must contain at least one of name or filters");
        }
        validateNameLength(req.getName());
        String filtersJson = req.getFilters() == null ? null
                : SavedFilterResponseMapper.toJson(req.getFilters(), objectMapper);
        SavedFilter updated = savedFilterUseCase.update(userId(), id, req.getName(), filtersJson);
        return Response.ok(SavedFilterResponseMapper.toResponse(updated, objectMapper)).build();
    }

    @Override
    @RolesAllowed("user")
    public Response deleteSavedFilter(UUID id) {
        savedFilterUseCase.delete(userId(), id);
        return Response.noContent().build();
    }

    // ── Admin: crawl/enrichment trigger (Story #7 / ADR 0003) ─────────────────

    @Override
    @RolesAllowed("admin")
    public Response triggerPass(TriggerRequestBody body) {
        if (body == null) {
            throw new BadRequestException("request body is required");
        }
        TriggerCommand command = TriggerCommand.builder()
                .kind(body.getKind() == null ? null : body.getKind().toString())
                .code(body.getCode())
                .requestedBy(userId())
                .build();

        TriggerRequest queued = adminTriggerUseCase.queue(command);
        return Response.status(Response.Status.ACCEPTED)
                .entity(TriggerResponseMapper.toResponse(queued))
                .build();
    }

    @Override
    @RolesAllowed("admin")
    public Response getTriggerStatus() {
        return Response.ok(TriggerStatusMapper.toStatusResponse(adminTriggerUseCase.getStatus(userId()))).build();
    }

    @Override
    @RolesAllowed("admin")
    public Response cancelTrigger(com.davidcreate.jobhub.job.contract.model.TriggerKind kind) {
        TriggerKind domainKind = TriggerKind.fromValue(kind.toString());
        TriggerRequest cancelled = adminTriggerUseCase.cancel(domainKind);
        return Response.ok(TriggerResponseMapper.toResponse(cancelled)).build();
    }

    // ── Admin: company enrichment (Story #430 / ADR 0025) ──────────────────────

    @Override
    @RolesAllowed("admin")
    public Response listAdminCompanies(String q, Boolean manuallyEdited, String sort, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        validatePagination(p, s);

        CompanySearchQuery query = CompanySearchQuery.builder()
                .q(q)
                .manuallyEdited(manuallyEdited)
                .sort(parseCompanySort(sort))
                .page(p)
                .size(s)
                .build();

        CompanyPage result = adminCompanyQueryUseCase.list(query);
        List<CompanyInfo> body = result.content().stream()
                .map(CompanyAdminResponseMapper::toResponse)
                .toList();
        return Response.ok(body)
                .header("X-Total-Count", result.totalCount())
                .build();
    }

    @Override
    @RolesAllowed("admin")
    public Response getAdminCompany(UUID id) {
        Company company = adminCompanyQueryUseCase.getById(id);
        return Response.ok(CompanyAdminResponseMapper.toResponse(company)).build();
    }

    @Override
    @RolesAllowed("admin")
    public Response updateAdminCompany(UUID id, CompanyUpdateRequest companyUpdateRequest) {
        if (companyUpdateRequest == null) {
            throw new BadRequestException("request body is required");
        }
        CompanyUpdateCommand command = CompanyAdminResponseMapper.toCommand(companyUpdateRequest);
        Company updated = updateCompanyUseCase.update(id, command);
        return Response.ok(CompanyAdminResponseMapper.toResponse(updated)).build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be >= 0");
        }
        if (size < 1) {
            throw new BadRequestException("size must be >= 1");
        }
        if (size > maxSize) {
            throw new BadRequestException("size must be <= " + maxSize);
        }
    }

    private static void validateNameLength(String name) {
        if (name != null && name.length() > 80) {
            throw new BadRequestException("name must be at most 80 characters");
        }
    }

    private UUID userId() {
        return UUID.fromString(jwt.getSubject());
    }

    private static List<EmploymentType> parseEmploymentTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return raw.stream().map(EmploymentType::fromValue).toList();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private static List<CareerLevel> parseCareerLevels(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return raw.stream().map(CareerLevel::fromValue).toList();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private static PostedWithin parsePostedWithin(String raw) {
        try {
            return PostedWithin.fromValue(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private static JobSortOrder parseSort(String raw) {
        try {
            return JobSortOrder.fromValue(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private static CompanySortOrder parseCompanySort(String raw) {
        try {
            return CompanySortOrder.fromValue(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
