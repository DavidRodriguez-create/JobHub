package com.davidcreate.jobhub.crawler.adapter.in.rest;

import com.davidcreate.jobhub.crawler.domain.model.CrawlBatchResult;
import com.davidcreate.jobhub.crawler.domain.port.in.CrawlUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
@Path("/crawl")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CrawlerResource {

    private static final Logger LOG = Logger.getLogger(CrawlerResource.class);

    private final CrawlUseCase crawlUseCase;

    @ConfigProperty(name = "crawler.crawl.min-new-posts", defaultValue = "100")
    int defaultMinNewPosts;

    public CrawlerResource(CrawlUseCase crawlUseCase) {
        this.crawlUseCase = crawlUseCase;
    }

    /**
     * Trigger an ad-hoc crawl batch.
     *
     * @param limit the new-post target for this run (default: configured min-new-posts).
     *              Must be >= 1; no upper bound (safety cap applies instead).
     */
    @POST
    public Response crawlBatch(@QueryParam("limit") Integer limit) {
        int minNewPosts = (limit != null) ? limit : defaultMinNewPosts;
        LOG.infof("Received request to crawl batch, new-post target: %d", minNewPosts);
        CrawlBatchResult result = crawlUseCase.crawlBatch(minNewPosts);
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
