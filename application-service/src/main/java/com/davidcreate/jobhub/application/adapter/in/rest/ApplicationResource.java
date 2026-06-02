package com.davidcreate.jobhub.application.adapter.in.rest;

import com.davidcreate.jobhub.application.adapter.in.rest.dto.ApplicationResponseMapper;
import com.davidcreate.jobhub.application.adapter.in.rest.dto.ApplicationStatusMapper;
import com.davidcreate.jobhub.application.application.port.in.ApplicationUseCase;
import com.davidcreate.jobhub.application.application.port.in.CreateApplicationCommand;
import com.davidcreate.jobhub.application.application.port.in.DeleteAllApplicationsCommand;
import com.davidcreate.jobhub.application.application.port.in.JobDetailsCommand;
import com.davidcreate.jobhub.application.application.port.in.ListApplicationsQuery;
import com.davidcreate.jobhub.application.application.port.in.UpdateApplicationCommand;
import com.davidcreate.jobhub.application.application.port.in.UpdateApplicationStatusCommand;
import com.davidcreate.jobhub.application.contract.api.ApplicationsApi;
import com.davidcreate.jobhub.application.contract.model.ApplicationStatus;
import com.davidcreate.jobhub.application.contract.model.CreateApplicationRequest;
import com.davidcreate.jobhub.application.contract.model.JobDetailsRequest;
import com.davidcreate.jobhub.application.contract.model.NextStep;
import com.davidcreate.jobhub.application.contract.model.UpdateApplicationRequest;
import com.davidcreate.jobhub.application.contract.model.UpdateApplicationStatusRequest;
import com.davidcreate.jobhub.application.contract.model.VerifiedActionRequest;
import com.davidcreate.jobhub.application.domain.entity.ApplicationView;
import com.davidcreate.jobhub.application.domain.exception.ValidationException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.net.URI;
import java.util.UUID;

@ApplicationScoped
@Path("/applications")
@RolesAllowed("user")
@RequiredArgsConstructor
public class ApplicationResource implements ApplicationsApi {

    private final ApplicationUseCase useCase;
    private final JsonWebToken jwt;

    @Override
    public Response createApplication(CreateApplicationRequest req) {
        JobDetailsCommand jobDetails = req.getJobDetails() == null ? null
                : toJobDetails(req.getJobDetails());
        ApplicationView created = useCase.create(
                new CreateApplicationCommand(callerId(), req.getJobPostId(), jobDetails));
        return Response.status(Response.Status.CREATED)
                .entity(ApplicationResponseMapper.toResponse(created))
                .build();
    }

    @Override
    public Response listApplications(ApplicationStatus status, Integer page, Integer size) {
        var query = new ListApplicationsQuery(
                callerId(),
                ApplicationStatusMapper.toDomain(status),
                page == null ? 0 : page,
                size == null ? 20 : size);
        ApplicationUseCase.PagedResult<ApplicationView> result = useCase.list(query);
        return Response.ok(ApplicationResponseMapper.toPage(result, query.page(), query.size())).build();
    }

    @Override
    public Response getApplicationStats() {
        return Response.ok(ApplicationResponseMapper.toStats(useCase.stats(callerId()))).build();
    }

    @Override
    public Response getApplicationStatsHistory(Integer months) {
        int m = months == null ? 6 : months;
        if (m < 1 || m > 24) {
            throw new ValidationException("months must be between 1 and 24");
        }
        return Response.ok(ApplicationResponseMapper.toHistory(useCase.statsHistory(callerId(), m))).build();
    }

    @Override
    public Response getApplication(UUID id) {
        return Response.ok(ApplicationResponseMapper.toResponse(useCase.get(callerId(), id))).build();
    }

    @Override
    public Response updateApplication(UUID id, UpdateApplicationRequest req) {
        validateUpdate(req);
        NextStep nextStep = req.getNextStep();
        UpdateApplicationCommand cmd = new UpdateApplicationCommand(
                callerId(), id,
                req.getNotes(),
                req.getAppliedAt(),
                req.getContact(),
                uriToString(req.getPortalUrl()),
                nextStep != null,
                nextStep == null ? null : nextStep.getLabel(),
                nextStep == null ? null : nextStep.getDate(),
                nextStep == null ? null : nextStep.getReminderAt());
        return Response.ok(ApplicationResponseMapper.toResponse(useCase.update(cmd))).build();
    }

    @Override
    public Response updateApplicationStatus(UUID id, UpdateApplicationStatusRequest req) {
        ApplicationView updated = useCase.updateStatus(new UpdateApplicationStatusCommand(
                callerId(), id, ApplicationStatusMapper.toDomain(req.getStatus())));
        return Response.ok(ApplicationResponseMapper.toResponse(updated)).build();
    }

    @Override
    public Response updateApplicationJob(UUID id, JobDetailsRequest req) {
        ApplicationView updated = useCase.updateJob(callerId(), id, toJobDetails(req));
        return Response.ok(ApplicationResponseMapper.toResponse(updated)).build();
    }

    @Override
    public Response deleteApplication(UUID id) {
        useCase.delete(callerId(), id);
        return Response.noContent().build();
    }

    @Override
    public Response deleteAllApplications(VerifiedActionRequest req) {
        useCase.deleteAll(new DeleteAllApplicationsCommand(
                callerId(), req.getVerificationId(), req.getCode(), bearerToken()));
        return Response.noContent().build();
    }

    private void validateUpdate(UpdateApplicationRequest req) {
        if (req.getNotes() != null && req.getNotes().length() > 5000) {
            throw new ValidationException("notes must be at most 5000 characters");
        }
        if (req.getContact() != null && req.getContact().length() > 200) {
            throw new ValidationException("contact must be at most 200 characters");
        }
        if (req.getNextStep() != null && req.getNextStep().getLabel() != null
                && req.getNextStep().getLabel().length() > 200) {
            throw new ValidationException("nextStep.label must be at most 200 characters");
        }
    }

    private JobDetailsCommand toJobDetails(JobDetailsRequest r) {
        return new JobDetailsCommand(r.getTitle(), r.getCompany(), uriToString(r.getUrl()), r.getLocation());
    }

    private String uriToString(URI uri) {
        return uri == null ? null : uri.toString();
    }

    private UUID callerId() {
        return UUID.fromString(jwt.getSubject());
    }

    private String bearerToken() {
        return "Bearer " + jwt.getRawToken();
    }
}
