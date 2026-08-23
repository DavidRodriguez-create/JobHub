package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment;

import com.davidcreate.jobhub.gemini.contract.model.GenerateContentRequest;
import com.davidcreate.jobhub.gemini.contract.model.GenerateContentResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Plain JAX-RS interface for Google's Generative Language API. The method is
 * declared here rather than inherited from a generated API: the path carries a
 * literal {@code :generateContent} suffix and the key travels in an
 * {@code x-goog-api-key} header, neither of which the generated contract expresses.
 * Request/response models still come from api-contracts (gemini.yaml). Built
 * programmatically per-provider by
 * {@link com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.EnrichmentProviderFactory}
 * — not a CDI rest-client, since the base URL is per-provider config.
 */
@Path("/v1beta/models")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GeminiClient {

    @POST
    @Path("/{model}:generateContent")
    GenerateContentResponse generateContent(@PathParam("model") String model,
                                            @HeaderParam("x-goog-api-key") String apiKey,
                                            GenerateContentRequest request);
}
