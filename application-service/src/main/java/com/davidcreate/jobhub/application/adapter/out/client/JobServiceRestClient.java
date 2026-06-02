package com.davidcreate.jobhub.application.adapter.out.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.UUID;

@RegisterRestClient(configKey = "job-service")
@Path("/jobs")
@Produces(MediaType.APPLICATION_JSON)
public interface JobServiceRestClient {

    @GET
    @Path("/{id}")
    JobPostRemoteResponse getById(@PathParam("id") UUID id);
}
