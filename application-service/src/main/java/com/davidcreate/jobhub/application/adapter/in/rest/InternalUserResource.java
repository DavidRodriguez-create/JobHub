package com.davidcreate.jobhub.application.adapter.in.rest;

import com.davidcreate.jobhub.application.adapter.in.rest.dto.ApplicationStatusMapper;
import com.davidcreate.jobhub.application.adapter.out.persistence.mapper.UpcomingNextStepMapper;
import com.davidcreate.jobhub.application.application.port.in.ApplicationUseCase;
import com.davidcreate.jobhub.application.application.port.in.GetInterestProfileUseCase;
import com.davidcreate.jobhub.application.application.port.in.GetUpcomingNextStepsUseCase;
import com.davidcreate.jobhub.application.contract.api.InternalApi;
import com.davidcreate.jobhub.application.contract.model.ApplicationSummaryListResponse;
import com.davidcreate.jobhub.application.contract.model.ApplicationSummaryResponse;
import com.davidcreate.jobhub.application.contract.model.InternalStatusUpdateResponse;
import com.davidcreate.jobhub.application.contract.model.InterestProfileResponse;
import com.davidcreate.jobhub.application.contract.model.StaleApplicationListResponse;
import com.davidcreate.jobhub.application.contract.model.StaleApplicationResponse;
import com.davidcreate.jobhub.application.contract.model.UpdateApplicationStatusRequest;
import com.davidcreate.jobhub.application.domain.entity.Application;
import com.davidcreate.jobhub.application.domain.entity.ApplicationSummaryView;
import com.davidcreate.jobhub.application.domain.entity.InterestProfile;
import com.davidcreate.jobhub.application.domain.entity.StaleApplicationView;
import com.davidcreate.jobhub.application.domain.entity.UpcomingNextStep;
import com.davidcreate.jobhub.application.domain.exception.ValidationException;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Service-to-service endpoints (ADR 0008/0009). Authenticated by {@code ServiceKeyFilter} via
 * the pre-shared {@code X-Service-Key} header, not a user JWT -- hence {@code @PermitAll} here.
 * The class-level {@code @Path} mirrors {@link InternalApi}'s class-level {@code @Path};
 * RESTEasy Reactive requires it on the implementing class for resource discovery (interface-only
 * class-level {@code @Path} is not picked up), but method-level JAX-RS annotations
 * ({@code @GET}, {@code @Path} suffix, {@code @Produces}) declared on {@link InternalApi}'s
 * methods ARE inherited as long as the override here adds none of its own.
 */
@ApplicationScoped
@Path("/internal")
@PermitAll
@RequiredArgsConstructor
public class InternalUserResource implements InternalApi {

    private final GetInterestProfileUseCase interestProfileUseCase;
    private final GetUpcomingNextStepsUseCase getUpcomingNextStepsUseCase;
    private final ApplicationUseCase applicationUseCase;

    @Override
    public Response getUserInterestProfile(UUID userId) {
        InterestProfile profile = interestProfileUseCase.getInterestProfile(userId);
        return Response.ok(toInterestProfileResponse(profile)).build();
    }

    @Override
    public Response getUpcomingNextSteps(Integer withinHours) {
        int window = withinHours == null ? 26 : withinHours;
        if (window < 1 || window > 168) {
            throw new ValidationException("withinHours must be between 1 and 168");
        }
        List<UpcomingNextStep> steps = getUpcomingNextStepsUseCase.handle(window);
        return Response.ok(UpcomingNextStepMapper.toResponse(steps)).build();
    }

    @Override
    public Response listStaleApplications(Integer days) {
        int d = days == null ? 14 : days;
        if (d < 1) {
            throw new ValidationException("days must be at least 1");
        }
        List<StaleApplicationView> stale = applicationUseCase.listStaleApplications(d);
        List<StaleApplicationResponse> items = stale.stream()
                .map(this::toStaleResponse)
                .toList();
        return Response.ok(new StaleApplicationListResponse().items(items)).build();
    }

    @Override
    public Response getApplicationSummaries(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("ids must contain between 1 and 100 application ids");
        }
        if (ids.size() > 100) {
            throw new ValidationException("ids must not contain more than 100 application ids");
        }
        List<ApplicationSummaryView> summaries = applicationUseCase.resolveApplicationSummaries(ids);
        List<ApplicationSummaryResponse> items = summaries.stream()
                .map(this::toSummaryResponse)
                .toList();
        return Response.ok(new ApplicationSummaryListResponse().items(items)).build();
    }

    @Override
    public Response checkApplicationOwner(UUID id, UUID userId) {
        if (applicationUseCase.isOwnedByUser(id, userId)) {
            return Response.noContent().build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @Override
    public Response updateApplicationStatusInternal(UUID id, UpdateApplicationStatusRequest req) {
        if (req == null || req.getStatus() == null) {
            throw new ValidationException("status is required");
        }
        ApplicationStatus domainStatus = ApplicationStatusMapper.toDomain(req.getStatus());
        Application updated = applicationUseCase.updateApplicationStatusInternal(id, domainStatus);
        return Response.ok(new InternalStatusUpdateResponse()
                .id(updated.getId())
                .userId(updated.getUserId())
                .newStatus(ApplicationStatusMapper.toContract(updated.getStatus())))
                .build();
    }

    private ApplicationSummaryResponse toSummaryResponse(ApplicationSummaryView view) {
        return new ApplicationSummaryResponse()
                .applicationId(view.applicationId())
                .company(view.company() != null ? view.company() : "")
                .jobTitle(view.jobTitle() != null ? view.jobTitle() : "")
                .companyLogoUrl(toUri(view.companyLogoUrl()));
    }

    private static java.net.URI toUri(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return java.net.URI.create(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private StaleApplicationResponse toStaleResponse(StaleApplicationView view) {
        return new StaleApplicationResponse()
                .id(view.id())
                .userId(view.userId())
                .jobTitle(view.jobTitle())
                .company(view.company() != null ? view.company() : "")
                .currentStatus(ApplicationStatusMapper.toContract(view.currentStatus()))
                .daysSinceLastActivity(view.daysSinceLastActivity());
    }

    private InterestProfileResponse toInterestProfileResponse(InterestProfile profile) {
        return new InterestProfileResponse()
                .userId(profile.getUserId())
                .locations(profile.getLocations())
                .companies(profile.getCompanies())
                .keywords(profile.getKeywords());
    }
}
