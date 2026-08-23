package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment;

import com.davidcreate.jobhub.ollama.contract.api.ChatApi;

/**
 * Plain JAX-RS interface for the local Ollama server. The contract — operation,
 * paths and request/response models — comes from the generated Ollama API in
 * api-contracts ({@link ChatApi}). api-contracts is Jandex-indexed in this
 * service so the inherited JAX-RS method is visible to the rest-client builder.
 * Built programmatically per-provider by
 * {@link com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.EnrichmentProviderFactory}
 * — not a CDI rest-client, since the base URL is per-provider config.
 */
public interface OllamaClient extends ChatApi {
}
