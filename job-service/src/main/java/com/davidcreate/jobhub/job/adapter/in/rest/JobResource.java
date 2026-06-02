package com.davidcreate.jobhub.job.adapter.in.rest;

import com.davidcreate.jobhub.job.adapter.in.rest.dto.JobFacetsResponseMapper;
import com.davidcreate.jobhub.job.adapter.in.rest.dto.JobPostResponseMapper;
import com.davidcreate.jobhub.job.adapter.in.rest.dto.SavedFilterResponseMapper;
import com.davidcreate.jobhub.job.adapter.in.rest.dto.SavedJobResponseMapper;
import com.davidcreate.jobhub.job.contract.api.JobsApi;
import com.davidcreate.jobhub.job.contract.model.SavedFilterPatchRequest;
import com.davidcreate.jobhub.job.contract.model.SavedFilterRequest;
import com.davidcreate.jobhub.job.contract.model.SavedFilterResponse;
import com.davidcreate.jobhub.job.domain.exception.JobNotFoundException;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.model.SavedFilter;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.job.domain.model.JobSortOrder;
import com.davidcreate.jobhub.job.domain.model.PostedWithin;
import com.davidcreate.jobhub.job.domain.port.in.GetJobFacetsUseCase;
import com.davidcreate.jobhub.job.domain.port.in.GetJobUseCase;
import com.davidcreate.jobhub.job.domain.port.in.SavedFilterUseCase;
import com.davidcreate.jobhub.job.domain.port.in.SavedJobUseCase;
import com.davidcreate.jobhub.job.domain.port.in.SearchJobsUseCase;
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
    private final JsonWebToken jwt;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "job.search.max-size", defaultValue = "100")
    int maxSize;

    public JobResource(SearchJobsUseCase searchJobsUseCase,
                       GetJobUseCase getJobUseCase,
                       GetJobFacetsUseCase getJobFacetsUseCase,
                       SavedJobUseCase savedJobUseCase,
                       SavedFilterUseCase savedFilterUseCase,
                       JsonWebToken jwt,
                       ObjectMapper objectMapper) {
        this.searchJobsUseCase = searchJobsUseCase;
        this.getJobUseCase = getJobUseCase;
        this.getJobFacetsUseCase = getJobFacetsUseCase;
        this.savedJobUseCase = savedJobUseCase;
        this.savedFilterUseCase = savedFilterUseCase;
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
                .compensationMin(compensationMin)
                .compensationMax(compensationMax)
                .postedWithin(parsePostedWithin(postedWithin))
                .sort(parseSort(sort))
                .page(p)
                .size(s)
                .build();

        List<JobPost> jobs = searchJobsUseCase.search(query);
        long total = searchJobsUseCase.count(query);

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
    public Response getJobFacets() {
        return Response.ok(JobFacetsResponseMapper.toResponse(getJobFacetsUseCase.getFacets())).build();
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
}
