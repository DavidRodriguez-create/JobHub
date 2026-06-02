package com.davidcreate.jobhub.crawler.adapter.in.rest;

import com.davidcreate.jobhub.crawler.domain.model.CrawlBatchResult;
import com.davidcreate.jobhub.crawler.domain.port.in.CrawlUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
@Path("/crawl")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CrawlerResource {

    private static final Logger LOG = Logger.getLogger(CrawlerResource.class);

    private final CrawlUseCase crawlUseCase;

    public CrawlerResource(CrawlUseCase crawlUseCase) {
        this.crawlUseCase = crawlUseCase;
    }

    @POST
    public Response crawlBatch(@QueryParam("limit") @DefaultValue("10") int limit) {
        LOG.infof("Received request to crawl batch with limit: %d", limit);
        CrawlBatchResult result = crawlUseCase.crawlBatch(limit);
        return result.isEmpty()
                ? Response.status(Response.Status.NO_CONTENT).build()
                : Response.ok(result).build();
    }

    @POST
    @Path("/{targetId}")
    public Response crawl(@PathParam("targetId") UUID targetId) {
        LOG.infof("Received request to crawl target: %s", targetId);
        crawlUseCase.crawl(targetId);
        return Response.ok().build();
    }
}