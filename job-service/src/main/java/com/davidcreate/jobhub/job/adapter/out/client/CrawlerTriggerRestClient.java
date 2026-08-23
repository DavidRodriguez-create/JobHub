package com.davidcreate.jobhub.job.adapter.out.client;

import com.davidcreate.jobhub.crawler.contract.model.QueueTriggerRequest;
import com.davidcreate.jobhub.crawler.contract.model.TriggerRequestResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Service-to-service write surface for {@code crawler.trigger_request} (ADR 0033,
 * ticket #583). crawler-service is the sole writer and authenticates only the
 * *service*, via {@code X-Service-Key} (no JWT support on that side).
 */
@RegisterRestClient(configKey = "crawler-service")
@Path("/internal/trigger-requests")
public interface CrawlerTriggerRestClient {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    TriggerRequestResponse queue(QueueTriggerRequest body, @HeaderParam("X-Service-Key") String serviceKey);

    @POST
    @Path("/{kind}/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    TriggerRequestResponse cancel(@PathParam("kind") String kind, @HeaderParam("X-Service-Key") String serviceKey);
}
