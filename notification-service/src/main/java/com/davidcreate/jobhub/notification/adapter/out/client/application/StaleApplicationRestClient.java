package com.davidcreate.jobhub.notification.adapter.out.client.application;

import com.davidcreate.jobhub.application.contract.model.StaleApplicationListResponse;
import com.davidcreate.jobhub.application.contract.model.UpdateApplicationStatusRequest;
import com.davidcreate.jobhub.application.contract.model.InternalStatusUpdateResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.UUID;

@RegisterRestClient(configKey = "app-stale")
@Path("/internal/applications")
public interface StaleApplicationRestClient {

    @GET
    @Path("/stale")
    @Produces(MediaType.APPLICATION_JSON)
    StaleApplicationListResponse listStaleApplications(@QueryParam("days") int days,
                                                        @HeaderParam("X-Service-Key") String serviceKey);

    @PUT
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    InternalStatusUpdateResponse updateApplicationStatus(@PathParam("id") UUID id,
                                                          UpdateApplicationStatusRequest request,
                                                          @HeaderParam("X-Service-Key") String serviceKey);
}
