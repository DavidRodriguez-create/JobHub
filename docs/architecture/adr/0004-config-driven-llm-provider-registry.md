# ADR 0004: Config-driven LLM provider registry for crawler enrichment

- **Status:** Proposed
- **Date:** 2026-06-12
- **Deciders:** Principal Architect (jobhub-architect); David R H
- **Affects:** crawler-service (Hexagonal), api-contracts (gemini.yaml, ollama.yaml), podman-compose.yml, podman-compose.native.yml, deploy/shared/compose/docker-compose.prod.yml

## Context

The crawler-service enrichment chain is hardcoded to two providers: Gemini (hosted, primary)
and Ollama (local CPU, fallback). The local Ollama container consumes significant CPU even
when idle and must always be running because `crawler-service` declares a hard `depends_on:
ollama`. Users who only have a free hosted API key (Gemini) should not need to run the Ollama
container at all. Additionally, there is no way to add a third provider (DeepSeek, Groq,
Together, Mistral, or any OpenAI-compatible API) without writing new Java code.

Issue #51 requests replacing the hardcoded two-provider chain with a **config-driven provider
registry** so that:

1. Any number of LLM providers can be declared in `application.properties` (indexed list).
2. Each provider specifies its API type (gemini, openai, ollama), base URL, model(s), API key,
   cooldown, and enabled flag.
3. Ollama is opt-in (disabled by default) -- the compose container starts only when explicitly
   requested.
4. The system works out of the box with just `GEMINI_API_KEY` set (backward-compatible).
5. If all providers are exhausted, the enrichment call throws so `EnrichmentService` marks the
   job as failed (no silent swallowing).

crawler-service is **Hexagonal**. The domain port `JobEnricher` is unchanged. All changes are
within the adapter layer (`adapter/out/client/enrichment/`). No schema changes are required.

Three distinct API formats exist today and must be supported as provider types:

- **gemini** -- Google Generative Language API: `POST /v1beta/models/{model}:generateContent`,
  `x-goog-api-key` header, Google-specific request/response schema. Has model-family quirks:
  `gemma*` models lack `systemInstruction` and JSON mode.
- **ollama** -- Local Ollama: `POST /api/chat`, its own schema (model, messages, format,
  stream, options).
- **openai** -- OpenAI-compatible (DeepSeek, Groq, Together, Mistral, etc.):
  `POST /v1/chat/completions`, `Authorization: Bearer <key>` header, standard OpenAI
  chat-completions schema.

## Decision

We will replace the hardcoded `FallbackJobEnricher` / `@HostedEnricher` / `@LocalEnricher`
pattern with a **config-driven provider chain** using Quarkus `@ConfigMapping` indexed
properties. The domain port (`JobEnricher`) is untouched. Everything below lives in the
adapter layer.

### 1. Configuration shape

A new `@ConfigMapping(prefix = "crawler.enrichment")` interface replaces the scattered
`@ConfigProperty` keys for the enrichment provider setup.

```
# ── LLM enrichment — provider chain ──────────────────────────────────────
# Providers are tried in declaration order (index 0 first). Each provider
# declares a type (gemini | openai | ollama) that determines the HTTP
# client and request format. A provider is skipped when enabled=false or
# when its api-key is blank (except ollama, which needs no key).
#
# Backward-compatible default: Gemini hosted chain only, Ollama disabled.

crawler.enrichment.providers[0].name=gemini
crawler.enrichment.providers[0].type=gemini
crawler.enrichment.providers[0].base-url=https://generativelanguage.googleapis.com
crawler.enrichment.providers[0].api-key=${GEMINI_API_KEY:}
crawler.enrichment.providers[0].models=gemini-3.1-flash-lite,gemma-4-31b-it,gemma-4-26b-a4b-it
crawler.enrichment.providers[0].cooldown-minutes=30
crawler.enrichment.providers[0].read-timeout-ms=90000
crawler.enrichment.providers[0].enabled=true

# Ollama — opt-in local fallback. Disabled by default so the container
# does not need to run. Enable by setting CRAWLER_OLLAMA_ENABLED=true
# in .env and starting the ollama compose profile.
crawler.enrichment.providers[1].name=ollama
crawler.enrichment.providers[1].type=ollama
crawler.enrichment.providers[1].base-url=${OLLAMA_BASE_URL:http://localhost:11434}
crawler.enrichment.providers[1].api-key=
crawler.enrichment.providers[1].models=${CRAWLER_ENRICHMENT_MODEL:llama3.2}
crawler.enrichment.providers[1].cooldown-minutes=0
crawler.enrichment.providers[1].read-timeout-ms=600000
crawler.enrichment.providers[1].enabled=${CRAWLER_OLLAMA_ENABLED:false}

# Example: adding DeepSeek (or any OpenAI-compatible provider) — just
# append a new indexed entry in .env / application-prod.properties:
#
# crawler.enrichment.providers[2].name=deepseek
# crawler.enrichment.providers[2].type=openai
# crawler.enrichment.providers[2].base-url=https://api.deepseek.com
# crawler.enrichment.providers[2].api-key=${DEEPSEEK_API_KEY:}
# crawler.enrichment.providers[2].models=deepseek-chat
# crawler.enrichment.providers[2].cooldown-minutes=30
# crawler.enrichment.providers[2].read-timeout-ms=60000
# crawler.enrichment.providers[2].enabled=true
```

**Property semantics:**

| Property | Required | Default | Notes |
|---|---|---|---|
| `name` | yes | -- | Human-readable identifier for logging. Must be unique. |
| `type` | yes | -- | One of `gemini`, `openai`, `ollama`. Selects the HTTP client and request builder. |
| `base-url` | yes | -- | Base URL for the provider's API. |
| `api-key` | no | `""` | API key. Blank means "no key configured". For `gemini`/`openai`, a blank key causes the provider to be skipped at startup (logged as WARN). `ollama` ignores this. |
| `models` | yes | -- | Comma-separated ordered list of model IDs. Each model is tried in order within this provider before moving to the next provider. |
| `cooldown-minutes` | no | `30` | Per-model cooldown after a failure (429, timeout, HTTP error). 0 = no cooldown (retry immediately next call). |
| `read-timeout-ms` | no | `30000` | HTTP read timeout for this provider's REST client. |
| `enabled` | no | `true` | Master switch. `false` = skip entirely. |

**Existing flat keys that are REMOVED:**

- `crawler.enrichment.hosted.enabled`
- `crawler.enrichment.hosted.models`
- `crawler.enrichment.hosted.api-key`
- `crawler.enrichment.hosted.cooldown-minutes`
- `crawler.enrichment.model`
- `quarkus.rest-client.gemini.url`
- `quarkus.rest-client.gemini.read-timeout`
- `quarkus.rest-client.ollama.url`
- `quarkus.rest-client.ollama.read-timeout`

**Existing keys that are KEPT (enrichment scheduler, not provider-specific):**

- `crawler.enrichment.enabled`
- `crawler.enrichment.batch-size`
- `crawler.enrichment.max-attempts`
- `crawler.enrichment.cron`

### 2. Config mapping interface

```
adapter/out/client/enrichment/config/
  EnrichmentConfig.java          -- @ConfigMapping(prefix = "crawler.enrichment")
  ProviderConfig.java            -- nested interface: one provider entry
  ProviderType.java              -- enum: GEMINI, OPENAI, OLLAMA
```

`EnrichmentConfig` is a Quarkus `@ConfigMapping` interface:

```java
@ConfigMapping(prefix = "crawler.enrichment")
public interface EnrichmentConfig {
    List<ProviderConfig> providers();
}

public interface ProviderConfig {
    String name();
    ProviderType type();
    String baseUrl();
    @WithDefault("") String apiKey();
    List<String> models();
    @WithDefault("30") int cooldownMinutes();
    @WithDefault("30000") int readTimeoutMs();
    @WithDefault("true") boolean enabled();
}

public enum ProviderType {
    GEMINI, OPENAI, OLLAMA
}
```

### 3. Adapter interface and implementations

An internal adapter-layer contract `EnrichmentProvider` (NOT a domain port -- it is an
implementation detail of the adapter that implements `JobEnricher`):

```
adapter/out/client/enrichment/
  provider/
    EnrichmentProvider.java            -- interface
    GeminiEnrichmentProvider.java      -- type=gemini
    OpenAiEnrichmentProvider.java      -- type=openai
    OllamaEnrichmentProvider.java      -- type=ollama
    EnrichmentProviderFactory.java     -- creates providers from ProviderConfig
  config/
    EnrichmentConfig.java
    ProviderConfig.java
    ProviderType.java
  ProviderChainJobEnricher.java        -- replaces FallbackJobEnricher

  # KEPT (shared utilities):
  GeminiClient.java                    -- @RegisterRestClient (still used by GeminiEnrichmentProvider)
  OllamaClient.java                   -- @RegisterRestClient (still used by OllamaEnrichmentProvider)
  EnrichmentParser.java                -- (already in adapter/out/client/support/)
  EnrichmentPrompt.java                -- (already in adapter/out/client/support/)

  # DELETED:
  FallbackJobEnricher.java
  GeminiJobEnricher.java
  OllamaJobEnricher.java
  HostedEnricher.java
  LocalEnricher.java
```

**EnrichmentProvider interface:**

```java
public interface EnrichmentProvider {
    /** Human-readable name for logging. */
    String name();

    /**
     * Try to enrich using this provider. Returns the enrichment on success.
     * Throws on any failure (HTTP error, timeout, unparseable response, all
     * models exhausted within this provider). The chain catches and moves on.
     */
    JobEnrichment enrich(String title, String description, String city, String country);
}
```

**GeminiEnrichmentProvider** -- absorbs the logic from today's `GeminiJobEnricher`:
- Constructed with a `ProviderConfig`, `GeminiClient` (programmatically created -- see
  below), `ObjectMapper`, `CurrencyConverter`.
- Iterates `config.models()` in order, with per-model cooldown (same `ConcurrentHashMap`
  pattern).
- Handles the `gemma*` model-family quirk (no systemInstruction, no JSON mode).
- Extracts JSON from prose using the existing `extractJsonObject()` logic (moved here or
  kept as a shared static utility).
- Parses via `EnrichmentParser.parse()`.

**OpenAiEnrichmentProvider** -- new:
- Constructed with a `ProviderConfig` and a programmatic REST client targeting the
  provider's `base-url`.
- Sends `POST /v1/chat/completions` with `Authorization: Bearer <api-key>`,
  `model`, `messages` (system + user), `temperature: 0`, `response_format: {"type":"json_object"}`.
- The request/response models for the OpenAI chat-completions API are added to
  `api-contracts/src/main/resources/openapi/openai.yaml` (models only,
  `generateApis=false` -- same pattern as `ollama.yaml`). The `@RegisterRestClient`
  interface lives in crawler-service.
- Iterates `config.models()` with per-model cooldown.
- Parses via `EnrichmentParser.parse()`.

**OllamaEnrichmentProvider** -- absorbs the logic from today's `OllamaJobEnricher`:
- Constructed with a `ProviderConfig`, `OllamaClient` (programmatically created),
  `ObjectMapper`, `CurrencyConverter`.
- Single-model (takes the first entry from `config.models()`).
- No cooldown needed (local, no rate limits).
- Parses via `EnrichmentParser.parse()`.

### 4. REST client creation -- programmatic vs declarative

The current design uses `@RegisterRestClient(configKey = "gemini")` and
`@RegisterRestClient(configKey = "ollama")`, which bind to a single base URL each at
startup. With a provider registry, we may have **multiple providers of the same type** at
different base URLs (e.g. two different OpenAI-compatible endpoints), and the base URL is
now per-provider-config, not a global rest-client key.

**Approach:** use **programmatic REST client creation** via Quarkus's `RestClientBuilder`
(from MicroProfile Rest Client) in `EnrichmentProviderFactory`:

```java
GeminiClient client = RestClientBuilder.newBuilder()
    .baseUri(URI.create(providerConfig.baseUrl()))
    .readTimeout(providerConfig.readTimeoutMs(), TimeUnit.MILLISECONDS)
    .build(GeminiClient.class);
```

This means the `@RegisterRestClient` annotation on `GeminiClient` and `OllamaClient` is
**removed** -- they become plain JAX-RS interfaces (still `@Path`, `@POST`, etc.) without
the CDI registration. The factory builds them programmatically per provider.

For the new `OpenAiClient`, it is declared as a plain JAX-RS interface in crawler-service
(same pattern) and also built programmatically.

The `quarkus.rest-client.gemini.*` and `quarkus.rest-client.ollama.*` config keys are
removed since the base URL and timeout are now per-provider in the indexed config.

**Note:** the `quarkus.index-dependency.api-contracts.*` keys must remain -- they ensure
the api-contracts JAR is Jandex-indexed so inherited JAX-RS annotations are visible. But
with programmatic client creation, `OllamaClient` no longer needs to extend the generated
`ChatApi` (it can declare its own `@POST @Path("/api/chat")` method directly). Whether to
keep the inheritance or inline the method is a developer decision during implementation;
either works. If inheritance is kept, the index dependency key stays.

### 5. ProviderChainJobEnricher (replaces FallbackJobEnricher)

```java
@ApplicationScoped
public class ProviderChainJobEnricher implements JobEnricher {

    private final List<EnrichmentProvider> providers;

    ProviderChainJobEnricher(EnrichmentConfig config,
                             EnrichmentProviderFactory factory) {
        this.providers = factory.createProviders(config.providers());
        if (providers.isEmpty()) {
            LOG.warn("No enrichment providers are enabled -- "
                   + "enrichment calls will fail until a provider is configured.");
        }
    }

    @Override
    public JobEnrichment enrich(String title, String description,
                                String city, String country) {
        for (EnrichmentProvider provider : providers) {
            try {
                return provider.enrich(title, description, city, country);
            } catch (Exception e) {
                LOG.debugf("Provider '%s' failed (%s) -- trying next.",
                           provider.name(), e.getMessage());
            }
        }
        throw new IllegalStateException(
            "All enrichment providers exhausted -- no usable response");
    }
}
```

### 6. EnrichmentProviderFactory

An `@ApplicationScoped` bean that reads the provider list and creates the right
`EnrichmentProvider` implementation for each enabled entry:

```java
@ApplicationScoped
public class EnrichmentProviderFactory {

    private final ObjectMapper objectMapper;
    private final CurrencyConverter converter;

    // constructor-injected

    public List<EnrichmentProvider> createProviders(List<ProviderConfig> configs) {
        return configs.stream()
            .filter(ProviderConfig::enabled)
            .filter(this::hasRequiredKey)
            .map(this::createProvider)
            .toList();
    }

    private boolean hasRequiredKey(ProviderConfig config) {
        if (config.type() == ProviderType.OLLAMA) return true;
        boolean hasKey = config.apiKey() != null && !config.apiKey().isBlank();
        if (!hasKey) {
            LOG.warnf("Provider '%s' is enabled but has no API key -- skipping.",
                      config.name());
        }
        return hasKey;
    }

    private EnrichmentProvider createProvider(ProviderConfig config) {
        return switch (config.type()) {
            case GEMINI -> {
                GeminiClient client = buildClient(config, GeminiClient.class);
                yield new GeminiEnrichmentProvider(config, client,
                                                   objectMapper, converter);
            }
            case OPENAI -> {
                OpenAiClient client = buildClient(config, OpenAiClient.class);
                yield new OpenAiEnrichmentProvider(config, client,
                                                    objectMapper, converter);
            }
            case OLLAMA -> {
                OllamaClient client = buildClient(config, OllamaClient.class);
                yield new OllamaEnrichmentProvider(config, client,
                                                    objectMapper, converter);
            }
        };
    }

    private <T> T buildClient(ProviderConfig config, Class<T> iface) {
        return RestClientBuilder.newBuilder()
            .baseUri(URI.create(config.baseUrl()))
            .readTimeout(config.readTimeoutMs(), TimeUnit.MILLISECONDS)
            .build(iface);
    }
}
```

### 7. Chain algorithm

1. `ProviderChainJobEnricher.enrich()` iterates `providers` (already filtered to enabled +
   keyed) in declaration order (index 0, 1, 2, ...).
2. For each provider, calls `provider.enrich(title, desc, city, country)`.
3. Inside each provider (Gemini, OpenAI), the provider iterates its own `models` list:
   - Skip models in cooldown (`ConcurrentHashMap<String, Long> modelRetryAt`).
   - Call the API. On HTTP/timeout/parse failure: enter cooldown for that model, try next.
   - On success: clear cooldown, parse via `EnrichmentParser`, return.
   - If all models exhausted: throw `IllegalStateException`.
4. Back in the chain: catch the exception, log at DEBUG, try the next provider.
5. If **all** providers throw: `ProviderChainJobEnricher` throws
   `IllegalStateException("All enrichment providers exhausted")`.
6. `EnrichmentService` catches this and calls `jobPostRepository.markEnrichmentFailed()` --
   this is the existing behavior, unchanged.

**Per-model cooldown** is owned by each provider instance (not shared across providers).
The `ConcurrentHashMap<String, Long>` pattern from `GeminiJobEnricher` is carried forward
identically. Cooldown minutes come from `config.cooldownMinutes()`. A value of 0 means
"no cooldown" (always retry immediately).

### 8. OpenAI contract in api-contracts

A new `api-contracts/src/main/resources/openapi/openai.yaml` spec defines **models only**
for the OpenAI chat-completions API:

- `ChatCompletionRequest` (model, messages[], temperature, response_format)
- `ChatCompletionMessage` (role, content)
- `ChatCompletionResponse` (id, choices[], usage)
- `ChatCompletionChoice` (index, message, finish_reason)
- `ChatCompletionUsage` (prompt_tokens, completion_tokens, total_tokens)

Generated into `com.davidcreate.jobhub.openai.contract.model` with
`<generateApis>false</generateApis>` -- same pattern as `ollama.yaml` and `gemini.yaml`.

The `OpenAiClient` `@Path("/v1/chat")` interface lives in crawler-service at
`adapter/out/client/enrichment/OpenAiClient.java` (same location as `GeminiClient`). It
declares a single method:

```java
@Path("/v1/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface OpenAiClient {
    @POST
    @Path("/completions")
    ChatCompletionResponse complete(@HeaderParam("Authorization") String bearerToken,
                                    ChatCompletionRequest request);
}
```

The provider prepends `"Bearer "` to the API key when calling.

### 9. Compose approach -- Ollama as opt-in via profiles

Use **Docker Compose profiles** (supported in compose spec 3.9+ and by Podman Compose) to
make the Ollama container opt-in:

```yaml
ollama:
  image: docker.io/ollama/ollama:latest
  container_name: jobhub-ollama
  profiles:
    - ollama
  volumes:
    - ollama_models:/root/.ollama
  ports:
    - "11434:11434"
```

With this:
- `podman compose up -d` -- starts everything **except** Ollama (default).
- `podman compose --profile ollama up -d` -- starts everything **including** Ollama.

The `crawler-service` definition must **remove** the hard `depends_on: ollama` so it can
start without the Ollama container. The Ollama provider in config is `enabled=false` by
default; when someone enables it (`CRAWLER_OLLAMA_ENABLED=true` in `.env`) they must also
start the Ollama profile.

Apply the same change to `podman-compose.native.yml` and
`deploy/shared/compose/docker-compose.prod.yml`.

### 10. What to keep vs remove

**DELETE (files):**
- `FallbackJobEnricher.java` -- replaced by `ProviderChainJobEnricher`
- `GeminiJobEnricher.java` -- logic absorbed into `GeminiEnrichmentProvider`
- `OllamaJobEnricher.java` -- logic absorbed into `OllamaEnrichmentProvider`
- `HostedEnricher.java` -- CDI qualifier, no longer needed
- `LocalEnricher.java` -- CDI qualifier, no longer needed

**REFACTOR (files):**
- `GeminiClient.java` -- remove `@RegisterRestClient(configKey = "gemini")`, keep as plain
  JAX-RS interface (programmatically built by the factory)
- `OllamaClient.java` -- remove `@RegisterRestClient(configKey = "ollama")`, keep as plain
  JAX-RS interface. Whether it still extends `ChatApi` or inlines the method is a developer
  choice.

**KEEP (unchanged):**
- `EnrichmentParser.java` (in `adapter/out/client/support/`)
- `EnrichmentPrompt.java` (in `adapter/out/client/support/`)
- `CurrencyConverter.java` (in `adapter/out/client/support/`)
- `JobEnricher.java` (domain port -- untouched)
- `EnrichmentService.java` (domain service -- untouched)
- `EnrichmentScheduler.java` (adapter/in/scheduler -- untouched)

**CREATE (new files):**
- `adapter/out/client/enrichment/config/EnrichmentConfig.java`
- `adapter/out/client/enrichment/config/ProviderConfig.java`
- `adapter/out/client/enrichment/config/ProviderType.java`
- `adapter/out/client/enrichment/provider/EnrichmentProvider.java`
- `adapter/out/client/enrichment/provider/GeminiEnrichmentProvider.java`
- `adapter/out/client/enrichment/provider/OpenAiEnrichmentProvider.java`
- `adapter/out/client/enrichment/provider/OllamaEnrichmentProvider.java`
- `adapter/out/client/enrichment/provider/EnrichmentProviderFactory.java`
- `adapter/out/client/enrichment/ProviderChainJobEnricher.java`
- `adapter/out/client/enrichment/OpenAiClient.java`
- `api-contracts/src/main/resources/openapi/openai.yaml`

**UPDATE (existing files):**
- `application.properties` -- replace flat enrichment keys with indexed provider config
- `application-prod.properties` -- update env var mappings
- `application-dev.properties` -- if any dev-specific enrichment overrides exist
- `src/test/resources/application.properties` -- test config for providers
- `podman-compose.yml` -- Ollama profile, remove `depends_on: ollama`
- `podman-compose.native.yml` -- same
- `deploy/shared/compose/docker-compose.prod.yml` -- same
- `.env.example` -- document new env vars (`CRAWLER_OLLAMA_ENABLED`, remove old ones)
- `api-contracts/pom.xml` -- add openai.yaml generator execution

### 11. Backward compatibility

The default `application.properties` ships with:
- `providers[0]` = Gemini, `enabled=true`, `api-key=${GEMINI_API_KEY:}`
- `providers[1]` = Ollama, `enabled=${CRAWLER_OLLAMA_ENABLED:false}`

Behavior with just `GEMINI_API_KEY` set (today's default):
- Gemini provider is enabled and keyed -- tries its model chain.
- Ollama provider is disabled -- not attempted.
- If Gemini exhausts all models, `ProviderChainJobEnricher` throws,
  `EnrichmentService` marks the job as failed.
- This is different from today (where it would silently fall back to local Ollama).
  This is **intentional** -- the user explicitly does not want CPU-heavy local fallback.

Behavior with no env vars at all (fresh checkout, `quarkus:dev`):
- Gemini provider is enabled but `api-key` is blank -- factory skips it (logs WARN).
- Ollama provider is disabled.
- All providers exhausted on first call -- enrichment jobs fail.
- This is the correct default for a developer who has not configured any LLM key.
  The enrichment scheduler still runs but marks jobs as failed, which is visible and
  recoverable once a key is configured.

Behavior to restore today's Ollama fallback:
- Set `CRAWLER_OLLAMA_ENABLED=true` in `.env`.
- Start with `podman compose --profile ollama up -d`.

### 12. Test impact

**Unit tests:**

- `GeminiJobEnricherTest` -- rename/refactor to `GeminiEnrichmentProviderTest`. The test
  structure is identical (mock `GeminiClient`, verify model chaining, cooldown, Gemma quirks,
  JSON extraction). The constructor changes to accept a `ProviderConfig` instead of
  individual `@ConfigProperty` values.
- New `OpenAiEnrichmentProviderTest` -- same shape: mock `OpenAiClient`, verify model
  chaining, cooldown, JSON parsing.
- New `OllamaEnrichmentProviderTest` -- same shape: mock `OllamaClient`, verify single-model
  call, parse.
- New `ProviderChainJobEnricherTest` -- mock `EnrichmentProvider` list, verify chain
  iteration, verify throw-on-exhaustion.
- New `EnrichmentProviderFactoryTest` -- verify factory creates the right provider type per
  `ProviderType`, skips disabled entries, skips keyless non-ollama entries.
- `EnrichmentServiceTest` -- **unchanged** (it mocks `JobEnricher`, which is the domain port
  -- the adapter behind it is irrelevant to this test).

**Existing tests that reference deleted classes:**
- Any test importing `FallbackJobEnricher`, `HostedEnricher`, `LocalEnricher`,
  `GeminiJobEnricher`, or `OllamaJobEnricher` must be updated to the new class names.

## Consequences

- Positive: adding a new LLM provider (DeepSeek, Groq, any OpenAI-compatible API) is
  pure configuration -- no Java code changes needed.
- Positive: Ollama is opt-in, so the default compose stack no longer consumes CPU for a
  container most users do not need.
- Positive: the domain layer (`JobEnricher`, `EnrichmentService`) is completely untouched.
  The refactoring is confined to the adapter layer, respecting the hexagonal boundary.
- Positive: backward-compatible for users who already have `GEMINI_API_KEY` set.
- Negative / cost: the Ollama fallback is no longer automatic -- users who relied on
  "hosted fails, silently use local" must now explicitly enable the Ollama provider. This is
  intentional per user decision #4, but is a behavior change that must be documented.
- Negative / cost: programmatic REST client creation loses some Quarkus DevTools integration
  (e.g. client-level config in application.properties, dev UI rest-client panel). Acceptable
  because the per-provider config in `EnrichmentConfig` covers the same knobs (URL, timeout).
- Negative / cost: a new OpenAPI spec (`openai.yaml`) in api-contracts and a new generator
  execution in its POM.
- Follow-ups: update `CLAUDE.md` to reflect the new enrichment config shape; update
  `.env.example`; consider adding a startup log line listing all active providers and their
  model chains for operational visibility.

## Alternatives considered

- **Single flat config with `crawler.enrichment.provider-order=gemini,deepseek,ollama`** and
  per-provider dot keys (`crawler.enrichment.gemini.models=...`). Rejected because it caps
  at one instance per type (cannot have two OpenAI-compatible providers with different base
  URLs/keys) and requires a separate naming convention for the order.

- **External JSON/YAML config file** (`enrichment-providers.json`). Rejected because it
  adds a file-management concern (mounting in compose, managing in CI/deploy), when Quarkus
  indexed properties in `application.properties` are sufficient and native to the framework.

- **CDI `@Any Instance<EnrichmentProvider>` discovery** (each provider is a CDI bean,
  priority-ordered). Rejected because the number and configuration of providers is a
  deployment concern, not a compile-time concern. A `@Produces` method would still need the
  same config mapping, and CDI instance iteration does not give stable ordering without
  `@Priority` annotations that would have to be derived from config anyway.

- **Keep `@RegisterRestClient` and use Quarkus named-client config** (one config key per
  provider). Rejected because Quarkus rest-client config keys are static (one per
  `configKey` string); you cannot have N dynamically-named rest-clients from indexed config.
  Programmatic `RestClientBuilder` is the standard MicroProfile approach for dynamic clients.
