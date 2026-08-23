package com.davidcreate.jobhub.notification.adapter.out.client.application;

import com.davidcreate.jobhub.application.contract.model.ApplicationSummaryListResponse;
import com.davidcreate.jobhub.application.contract.model.InterestProfileResponse;
import com.davidcreate.jobhub.application.contract.model.UpcomingNextStepsResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.UUID;

@RegisterRestClient(configKey = "app-internal")
public interface AppInternalRestClient {

    @GET
    @Path("/internal/users/{userId}/interest-profile")
    @Produces(MediaType.APPLICATION_JSON)
    InterestProfileResponse getUserInterestProfile(@PathParam("userId") UUID userId,
                                                     @HeaderParam("X-Service-Key") String serviceKey);

    @GET
    @Path("/internal/applications/upcoming-next-steps")
    @Produces(MediaType.APPLICATION_JSON)
    UpcomingNextStepsResponse getUpcomingNextSteps(@QueryParam("withinHours") int withinHours,
                                                    @HeaderParam("X-Service-Key") String serviceKey);

    @HEAD
    @Path("/internal/applications/{id}/owner/{userId}")
    Response headOwner(@PathParam("id") UUID applicationId,
                        @PathParam("userId") UUID userId,
                        @HeaderParam("X-Service-Key") String serviceKey);

    /**
     * Resolves a batch of application ids to their display summary (company + jobTitle)
     * in a single call (ADR 0014, story #207). {@code ids} must already be comma-joined
     * (form style, not exploded) per the frozen contract; callers build that string.
     */
    @GET
    @Path("/internal/applications/summaries")
    @Produces(MediaType.APPLICATION_JSON)
    ApplicationSummaryListResponse getApplicationSummaries(@QueryParam("ids") String ids,
                                                             @HeaderParam("X-Service-Key") String serviceKey);
}
