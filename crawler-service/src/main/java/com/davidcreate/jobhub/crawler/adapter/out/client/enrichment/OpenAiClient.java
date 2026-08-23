package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment;

import com.davidcreate.jobhub.openai.contract.model.ChatCompletionRequest;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Plain JAX-RS interface for OpenAI-compatible chat-completions APIs (DeepSeek,
 * Groq, Together, Mistral, etc.). Request/response models come from api-contracts
 * (openai.yaml). Built programmatically per-provider by
 * {@link com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.EnrichmentProviderFactory}
 * — not a CDI rest-client, since the base URL is per-provider config.
 */
@Path("/v1/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface OpenAiClient {

    @POST
    @Path("/completions")
    ChatCompletionResponse complete(@HeaderParam("Authorization") String bearerToken,
                                     ChatCompletionRequest request);
}
