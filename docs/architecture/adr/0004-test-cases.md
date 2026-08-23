# Test cases — ADR 0004 (config-driven LLM provider registry)

Companion to `0004-config-driven-llm-provider-registry.md` (issue #51). These are the
labelled cases the crawler-service developer implements via TDD. All cases are **unit**
layer (`unit_tests/adapter/out/client/enrichment/...`), JUnit 5 + Mockito
(`@ExtendWith(MockitoExtension.class)`), mocking the REST clients (`GeminiClient`,
`OpenAiClient`, `OllamaClient`) and/or `EnrichmentProvider`. No component tests are added —
no REST endpoints change, and `EnrichmentServiceTest` (mocks the domain port `JobEnricher`)
is unaffected.

Package layout for new/renamed test classes:

```
unit_tests/adapter/out/client/enrichment/
  ProviderChainJobEnricherTest.java
  EnrichmentProviderFactoryTest.java
  provider/
    GeminiEnrichmentProviderTest.java     (renamed from GeminiJobEnricherTest)
    OpenAiEnrichmentProviderTest.java     (new)
    OllamaEnrichmentProviderTest.java     (new)
```

(Match whatever package the developer puts the production `provider/` classes in —
`unit_tests` mirrors `src/main` 1:1 per `CLAUDE.md`.)

Shared fixtures across provider tests: build a `ProviderConfig` via a tiny test record/stub
implementing the `@ConfigMapping` interface (same pattern `GeminiJobEnricherTest` used for
`FxRateConfig` — an anonymous class implementing the interface's accessor methods), or via
`io.smallrye.config.SmallRyeConfigProviderResolver` / `SmallRyeConfigBuilder` if the
developer prefers a real bound `EnrichmentConfig` for a couple of cases (TC-EP-09,
TC-EP-10). Either approach is acceptable; the case only specifies the **field values**
needed.

---

## 1. ProviderChainJobEnricherTest

Mocks a `List<EnrichmentProvider>` (or individual `EnrichmentProvider` mocks assembled into
a list) injected into `ProviderChainJobEnricher`. Does **not** go through
`EnrichmentProviderFactory` — that's covered separately (section 2).

| ID | Description | Given | When | Then |
|---|---|---|---|---|
| **TC-PC-01** | First provider succeeds — chain stops, no fallback called | Two mocked providers `p1`, `p2` in order `[p1, p2]`. `p1.enrich(...)` returns a valid `JobEnrichment`. | `enricher.enrich(title, desc, city, country)` is called. | Returns `p1`'s `JobEnrichment` unchanged. `verify(p2, never()).enrich(any(), any(), any(), any())`. |
| **TC-PC-02** | First provider throws — chain falls through to second, which succeeds | `[p1, p2]`. `p1.enrich(...)` throws `RuntimeException("p1 down")`. `p2.enrich(...)` returns a valid `JobEnrichment`. | `enricher.enrich(...)` is called. | Returns `p2`'s result. Both `p1.enrich` and `p2.enrich` are verified called exactly once with the same `(title, desc, city, country)` arguments. No exception propagates. |
| **TC-PC-03** | All providers throw — chain throws `IllegalStateException` | `[p1, p2]`, both `enrich(...)` throw (`RuntimeException`). | `enricher.enrich(...)` is called. | Throws `IllegalStateException` with message containing `"All enrichment providers exhausted"`. Both `p1` and `p2` are verified called exactly once. |
| **TC-PC-04** | Empty provider list — throws immediately without invoking anything | `providers = List.of()` (e.g. factory returned no providers — all disabled/unkeyed). | `enricher.enrich(...)` is called. | Throws `IllegalStateException` with message containing `"All enrichment providers exhausted"`. (Constructing with an empty list must not throw at construction time — only `enrich()` throws.) |
| **TC-PC-05** | Chain iteration order is declaration order, not insertion-into-map order | Three mocks `[p1, p2, p3]`. `p1` and `p2` throw; `p3` succeeds. | `enricher.enrich(...)` is called. | `p1`, `p2`, `p3` are each called exactly once, **in that order** (use `InOrder` verification). Returns `p3`'s result. |
| **TC-PC-06** | A single-element list where the provider succeeds | `providers = List.of(p1)`. `p1.enrich(...)` returns a valid `JobEnrichment`. | `enricher.enrich(...)` is called. | Returns `p1`'s result; no exception. |
| **TC-PC-07** | Exception from a provider does not leak provider-internal exception type | `[p1, p2]`. `p1.enrich(...)` throws a checked-style runtime exception specific to a provider (e.g. a hypothetical `WebApplicationException`); `p2.enrich(...)` returns a valid result. | `enricher.enrich(...)` is called. | Returns `p2`'s result; the exception from `p1` is swallowed (caught as `Exception`), not rethrown or wrapped. |

**Constructor note for the developer**: `ProviderChainJobEnricher`'s real constructor takes
`(EnrichmentConfig, EnrichmentProviderFactory)` per the ADR. For these unit tests, either:
- add a package-visible/test constructor that accepts `List<EnrichmentProvider>` directly, **or**
- mock `EnrichmentProviderFactory.createProviders(...)` to return the desired list and pass a
  trivial `EnrichmentConfig` stub.

Either is fine — the case only specifies the resulting `providers` list and the expected
`enrich()` behavior.

---

## 2. EnrichmentProviderFactoryTest

Constructs `EnrichmentProviderFactory` with real (or trivially-stubbed) `ObjectMapper` and
`CurrencyConverter` (same pattern as `GeminiJobEnricherTest.setUp()` — instantiate
`CurrencyConverter` via its package-private constructor with a stub `FxRateConfig`, or mock
it since the factory itself never calls it). Calls `createProviders(List<ProviderConfig>)`
and inspects the returned `List<EnrichmentProvider>`.

Build `ProviderConfig` instances as small test doubles (anonymous class or record
implementing the `@ConfigMapping`-style interface) with explicit field values per case.

| ID | Description | Given | When | Then |
|---|---|---|---|---|
| **TC-EPF-01** | `type=GEMINI`, enabled, keyed → produces a `GeminiEnrichmentProvider` | One `ProviderConfig`: `name="gemini"`, `type=GEMINI`, `baseUrl="https://generativelanguage.googleapis.com"`, `apiKey="test-key"`, `models=["gemini-3.1-flash-lite"]`, `enabled=true`. | `factory.createProviders(List.of(config))` is called. | Returns a list of size 1; the element is an instance of `GeminiEnrichmentProvider`. |
| **TC-EPF-02** | `type=OPENAI`, enabled, keyed → produces an `OpenAiEnrichmentProvider` | One `ProviderConfig`: `name="deepseek"`, `type=OPENAI`, `baseUrl="https://api.deepseek.com"`, `apiKey="dsk-test"`, `models=["deepseek-chat"]`, `enabled=true`. | `factory.createProviders(List.of(config))` is called. | Returns a list of size 1; the element is an instance of `OpenAiEnrichmentProvider`. |
| **TC-EPF-03** | `type=OLLAMA`, enabled, blank key → produces an `OllamaEnrichmentProvider` (key not required) | One `ProviderConfig`: `name="ollama"`, `type=OLLAMA`, `baseUrl="http://localhost:11434"`, `apiKey=""`, `models=["llama3.2"]`, `enabled=true`. | `factory.createProviders(List.of(config))` is called. | Returns a list of size 1; the element is an instance of `OllamaEnrichmentProvider`. |
| **TC-EPF-04** | Disabled provider (`enabled=false`) is skipped, regardless of type/key | One `ProviderConfig`: `type=GEMINI`, `apiKey="test-key"`, `enabled=false`. | `factory.createProviders(List.of(config))` is called. | Returns an empty list. |
| **TC-EPF-05** | Enabled `GEMINI` with blank `apiKey` is skipped | One `ProviderConfig`: `type=GEMINI`, `apiKey=""`, `enabled=true`. | `factory.createProviders(List.of(config))` is called. | Returns an empty list. |
| **TC-EPF-06** | Enabled `OPENAI` with blank `apiKey` is skipped | One `ProviderConfig`: `type=OPENAI`, `apiKey="   "` (whitespace-only), `enabled=true`. | `factory.createProviders(List.of(config))` is called. | Returns an empty list (blank includes whitespace-only — `isBlank()` semantics). |
| **TC-EPF-07** | Enabled `OLLAMA` with blank `apiKey` is **kept** (no key required) | One `ProviderConfig`: `type=OLLAMA`, `apiKey=""`, `enabled=true`, valid `baseUrl`/`models`. | `factory.createProviders(List.of(config))` is called. | Returns a list of size 1; element is `OllamaEnrichmentProvider`. (Contrast with TC-EPF-05/06 — confirms the `hasRequiredKey` short-circuit for OLLAMA.) |
| **TC-EPF-08** | Mixed list — order preserved, disabled/unkeyed entries filtered out | Three `ProviderConfig`s in this order: `[0] GEMINI enabled+keyed`, `[1] GEMINI enabled, blank key`, `[2] OLLAMA enabled, blank key`. | `factory.createProviders(...)` is called with the list above. | Returns a list of size 2: `[GeminiEnrichmentProvider, OllamaEnrichmentProvider]`, **in that relative order** (config `[1]` dropped). |
| **TC-EPF-09** | Empty input list → empty output, no exception | `configs = List.of()`. | `factory.createProviders(List.of())` is called. | Returns an empty list; no exception thrown. |
| **TC-EPF-10** | `cooldownMinutes`/`readTimeoutMs` defaults are honoured when constructing the provider (no NPE/0 used incorrectly) | One `ProviderConfig` with `cooldownMinutes` and `readTimeoutMs` left at their `@WithDefault` values (`30`, `30000`), `type=GEMINI`, keyed, enabled. | `factory.createProviders(List.of(config))` is called, then the returned `GeminiEnrichmentProvider`'s `enrich(...)` is exercised with a mocked client (combine with TC-GEM-02-style cooldown assertion, or simply assert construction does not throw and `name()` returns `config.name()`). | Provider constructs successfully; `provider.name()` equals the config's `name` (`"gemini"`). |

**Note on REST client construction**: `createProviders` calls `RestClientBuilder.newBuilder()
.baseUri(...).readTimeout(...).build(iface)` internally for each kept config. This is real
MicroProfile rest-client code and **will run** in these unit tests (it does not make a
network call at build time — `.build()` just creates a proxy). If this proves awkward to
test without CDI/Quarkus context, the developer may extract a protected/package-visible
`buildClient(ProviderConfig, Class<T>)` seam and verify TC-EPF-01..09 against the returned
`EnrichmentProvider`'s **type** and `name()` only (not against the live client instance) —
the case intent (right type, right filtering, right order) is unchanged either way.

---

## 3. GeminiEnrichmentProviderTest (rename of GeminiJobEnricherTest)

Mocks `GeminiClient`. Constructs `GeminiEnrichmentProvider` with a `ProviderConfig` (instead
of the individual `@ConfigProperty` values the old `GeminiJobEnricher` took). All existing
`GeminiJobEnricherTest` cases are carried forward with the new constructor shape — listed
here renumbered for traceability. New cases (TC-GEM-09, TC-GEM-10) cover the
`enabled`/`apiKey` semantics now owned by the factory vs the provider itself.

Default `ProviderConfig` for these cases unless stated otherwise: `name="gemini"`,
`type=GEMINI`, `baseUrl="https://generativelanguage.googleapis.com"`, `apiKey="test-key"`,
`cooldownMinutes=30`, `readTimeoutMs=90000`, `enabled=true`, `models=<set per case>`.

| ID | Description | Given | When | Then |
|---|---|---|---|---|
| **TC-GEM-01** | Rolls to the next model when the first fails, returning its result | `models=["model-a","model-b"]`. `client.generateContent("model-a", ...)` throws `RuntimeException("429 Too Many Requests")`. `client.generateContent("model-b", ...)` returns a response containing the valid enrichment JSON. | `provider.enrich("Engineer", "Builds things in London.", "London", "United Kingdom")`. | Returns a non-null `JobEnrichment` with `careerLevel="senior"`. `client.generateContent` is verified called once for `"model-a"` and once for `"model-b"`. |
| **TC-GEM-02** | Skips a model that is still in cooldown on a subsequent call | Same as TC-GEM-01 mocks. `provider.enrich(...)` is called **twice** in sequence (same provider instance). | Second call to `provider.enrich(...)`. | After both calls: `client.generateContent("model-a", ...)` called exactly **1** time total (cooled down after first failure); `client.generateContent("model-b", ...)` called exactly **2** times total. |
| **TC-GEM-03** | Throws `IllegalStateException` when every model fails | `models=["model-a","model-b"]`. `client.generateContent(any(), any(), any())` throws `RuntimeException("boom")` for all models. | `provider.enrich(...)`. | Throws `IllegalStateException`. Both `"model-a"` and `"model-b"` verified called exactly once each. |
| **TC-GEM-04** | Gemma model request shape — no `systemInstruction`, no JSON mime type, system prompt folded into user turn | `models=["gemma-4-26b-it"]`. `client.generateContent("gemma-4-26b-it", ...)` returns a valid-JSON response (captured via `ArgumentCaptor<GenerateContentRequest>`). | `provider.enrich(...)`. | Captured request: `getSystemInstruction()` is `null`; `getGenerationConfig().getResponseMimeType()` is `null`; `getContents().get(0).getParts().get(0).getText()` contains `EnrichmentPrompt.SYSTEM_PROMPT`. |
| **TC-GEM-05** | Non-Gemma (Gemini) model request shape — `systemInstruction` present, JSON mime type set | `models=["gemini-3.1-flash-lite"]`. `client.generateContent("gemini-3.1-flash-lite", ...)` returns a valid-JSON response (captured). | `provider.enrich(...)`. | Captured request: `getSystemInstruction()` is non-null; `getGenerationConfig().getResponseMimeType()` equals `"application/json"`. |
| **TC-GEM-06** | Parses a Gemma "thinking" reply — reasoning prose + fenced ```json object | `models=["gemma-4-31b-it"]`. `client.generateContent("gemma-4-31b-it", ...)` returns a response whose text is reasoning prose followed by `` ```json\n{...valid JSON...}\n``` ``. | `provider.enrich(...)`. | Returns non-null `JobEnrichment`; `employmentType="full-time"`, `city="London"`. |
| **TC-GEM-07** | Extracts a balanced JSON object amid prose even when string values contain literal braces | `models=["gemini-3.1-flash-lite"]`. Response text = `"here you go:\n{...,\"requirements\":[\"C++ templates {weird}\"],...}\nhope that helps"`. | `provider.enrich(...)`. | Returns non-null `JobEnrichment`; `requirements()` contains exactly `"C++ templates {weird}"`; `city="London"`. |
| **TC-GEM-08** | A truncated/unbalanced reply is unusable — provider throws so the chain moves on | `models=["m1"]`. `client.generateContent(...)` returns a response whose text is `"{\"employmentType\": \"full-time\""` (no closing brace). | `provider.enrich(...)`. | Throws `IllegalStateException`. |
| **TC-GEM-09 (EP-L-17 carry-forward)** | Language normalization — variant mapped, programming language dropped, unknown human language → "Unknown" | `models=["gemini-3.1-flash-lite"]`. Response JSON has `"languages":["Deutsch","Python","Swahili","en"]`. | `provider.enrich(...)`. | `result.languages()` equals exactly `["German","Unknown","English"]` (Python dropped, dedup/order preserved). |
| **TC-GEM-10 (new)** | `provider.name()` returns the configured name for chain logging | `ProviderConfig.name() == "gemini"`. | `provider.name()` is called. | Returns `"gemini"`. |
| **TC-GEM-11 (new)** | Blank/no `apiKey` is the factory's concern, not the provider's — if constructed anyway, provider attempts the call (no internal "key not configured" guard) | `ProviderConfig.apiKey()=""`, `models=["model-a"]`. `client.generateContent("model-a", any(), any())` returns a valid response. | `provider.enrich(...)`. | Returns a non-null `JobEnrichment`; the provider does **not** throw `IllegalStateException("...API key not configured")` itself (that check moved to `EnrichmentProviderFactory.hasRequiredKey`, covered by TC-EPF-05). If the developer instead chooses to keep a defensive check inside the provider, this case should be removed and the behavior re-verified at TC-EPF-05 only — flag this decision back to QA at end-review. |

---

## 4. OpenAiEnrichmentProviderTest (new)

Mocks `OpenAiClient` (`complete(String bearerToken, ChatCompletionRequest request) →
ChatCompletionResponse`, per ADR section 8). Constructs `OpenAiEnrichmentProvider` with a
`ProviderConfig`: `name="deepseek"`, `type=OPENAI`, `baseUrl="https://api.deepseek.com"`,
`apiKey="dsk-test-key"`, `cooldownMinutes=30`, `readTimeoutMs=60000`, `enabled=true`,
`models=<per case>`.

Response helper: build a `ChatCompletionResponse` with one `ChatCompletionChoice` whose
`message.content` is the JSON (or prose+JSON) string under test, `finish_reason="stop"`, and
a `ChatCompletionUsage` with token counts (mirrors `responseWith(...)` in the old test).

| ID | Description | Given | When | Then |
|---|---|---|---|---|
| **TC-OAI-01** | Successful single-model call returns a parsed `JobEnrichment` | `models=["deepseek-chat"]`. `client.complete("Bearer dsk-test-key", request-for-"deepseek-chat")` returns a response whose choice message content is the valid enrichment JSON (same `VALID_JSON` shape as TC-GEM-01). | `provider.enrich("Engineer", "Builds things in London.", "London", "United Kingdom")`. | Returns non-null `JobEnrichment` with `careerLevel="senior"`, `city="London"`. `client.complete` verified called once. |
| **TC-OAI-02** | `Authorization` header is `"Bearer " + apiKey` | `models=["deepseek-chat"]`, `apiKey="dsk-test-key"`. Capture the first argument to `client.complete(...)`. Response is valid JSON. | `provider.enrich(...)`. | Captured `bearerToken` argument equals exactly `"Bearer dsk-test-key"`. |
| **TC-OAI-03** | Request shape — `model`, `messages` (system + user), `temperature=0`, `response_format={"type":"json_object"}` | `models=["deepseek-chat"]`. Capture the `ChatCompletionRequest` argument via `ArgumentCaptor`. Response is valid JSON. | `provider.enrich("Engineer", "desc", "London", "United Kingdom")`. | Captured request: `getModel()` equals `"deepseek-chat"`; `getMessages()` has 2 entries — first with `role="system"` and `content` equal to/containing `EnrichmentPrompt.SYSTEM_PROMPT`, second with `role="user"` and `content` containing the user prompt (title/description/location, per `EnrichmentPrompt.buildUserPrompt(...)`); `getTemperature()` equals `0.0` (or `0`); `getResponseFormat()` (however modelled — e.g. a `Map`/nested type with `type="json_object"`) is set to `json_object`. |
| **TC-OAI-04** | Rolls to the next model when the first fails | `models=["deepseek-chat","deepseek-reasoner"]`. `client.complete(any(), requestFor("deepseek-chat"))` throws `RuntimeException("rate limited")`. `client.complete(any(), requestFor("deepseek-reasoner"))` returns valid JSON. (Match on model via `ArgumentMatchers` against the captured request's `model` field, or use two separate `when(...)` stubs distinguished by an argument matcher lambda.) | `provider.enrich(...)`. | Returns non-null `JobEnrichment`. Both models verified called exactly once each. |
| **TC-OAI-05** | Per-model cooldown — second `enrich()` call on the same instance skips the cooled-down model | Same mocks as TC-OAI-04. `provider.enrich(...)` is called twice on the same `OpenAiEnrichmentProvider` instance. | Second call. | `"deepseek-chat"` is called exactly **1** time total across both invocations (cooled down after first failure); `"deepseek-reasoner"` is called exactly **2** times total. |
| **TC-OAI-06** | All models fail → `IllegalStateException` | `models=["deepseek-chat","deepseek-reasoner"]`. `client.complete(any(), any())` throws `RuntimeException("boom")` for every call. | `provider.enrich(...)`. | Throws `IllegalStateException`. Both models verified called exactly once. |
| **TC-OAI-07** | `cooldownMinutes=0` disables cooldown — failed model retried on the very next call | `models=["deepseek-chat","deepseek-reasoner"]`, `cooldownMinutes=0`. First call: `"deepseek-chat"` throws, `"deepseek-reasoner"` succeeds. Reset/re-stub so on the **second** call `"deepseek-chat"` now succeeds too. | `provider.enrich(...)` called twice. | On the second call, `"deepseek-chat"` is attempted again (not skipped) — i.e. `client.complete` is invoked for `"deepseek-chat"` on **both** calls (2 times total), confirming `cooldown-minutes=0` ⇒ no cooldown window. |
| **TC-OAI-08** | Unparseable/truncated content throws so the chain moves on | `models=["deepseek-chat"]`. Response choice message content = `"{\"employmentType\": \"full-time\""` (truncated, no closing brace). | `provider.enrich(...)`. | Throws `IllegalStateException`. |
| **TC-OAI-09** | JSON extracted from prose-wrapped reply (parity with Gemini's prose handling) | `models=["deepseek-chat"]`. Response content = `"Sure, here is the JSON:\n{...valid JSON...}\nLet me know if you need anything else."` | `provider.enrich(...)`. | Returns non-null `JobEnrichment` with the expected fields parsed correctly (mirrors TC-GEM-07's brace-extraction expectation — confirm the shared `extractJsonObject` utility, wherever it lives, is reused/exercised here too). |
| **TC-OAI-10** | `provider.name()` returns the configured name | `ProviderConfig.name() == "deepseek"`. | `provider.name()` called. | Returns `"deepseek"`. |

---

## 5. OllamaEnrichmentProviderTest (new)

Mocks `OllamaClient` (`chat(ChatRequest) → ChatResponse`, same generated Ollama contract
models as today's `OllamaJobEnricher`). Constructs `OllamaEnrichmentProvider` with a
`ProviderConfig`: `name="ollama"`, `type=OLLAMA`, `baseUrl="http://localhost:11434"`,
`apiKey=""`, `cooldownMinutes=0`, `readTimeoutMs=600000`, `enabled=true`,
`models=["llama3.2"]` (single-element — only the first model is used per ADR §3).

| ID | Description | Given | When | Then |
|---|---|---|---|---|
| **TC-OLL-01** | Successful call returns a parsed `JobEnrichment` | `models=["llama3.2"]`. `client.chat(any())` returns a `ChatResponse` whose `message.content` is the valid enrichment JSON. | `provider.enrich("Engineer", "Builds things in London.", "London", "United Kingdom")`. | Returns non-null `JobEnrichment` with `careerLevel="senior"`, `city="London"`. `client.chat` verified called exactly once. |
| **TC-OLL-02** | Request shape — `model`, `stream=false`, `format="json"`, `temperature=0`, two messages (system + user) | Capture the `ChatRequest` argument via `ArgumentCaptor`. `models=["llama3.2"]`. Response is valid JSON. | `provider.enrich("Engineer", "desc", "London", "United Kingdom")`. | Captured request: `getModel()` equals `"llama3.2"`; `isStream()`/`getStream()` is `false`; `getFormat()` equals `"json"`; `getOptions().getTemperature()` equals `0.0`; `getMessages()` has 2 entries — `role="system"` content equals/contains `EnrichmentPrompt.SYSTEM_PROMPT`, `role="user"` content contains the user prompt built via `EnrichmentPrompt.buildUserPrompt(...)`. |
| **TC-OLL-03** | Empty/blank response content throws `IllegalStateException` | `models=["llama3.2"]`. `client.chat(any())` returns a `ChatResponse` whose `message.content` is `""` (or `message` is `null`). | `provider.enrich(...)`. | Throws `IllegalStateException` with message indicating an empty response (e.g. contains `"empty response"`). |
| **TC-OLL-04** | Non-JSON content throws `IllegalStateException` | `models=["llama3.2"]`. `client.chat(any())` returns a `ChatResponse` whose `message.content` is `"not json at all"`. | `provider.enrich(...)`. | Throws `IllegalStateException` (wrapping the underlying `JsonProcessingException`), message indicates non-JSON content. |
| **TC-OLL-05** | No cooldown / no model-chain behavior — repeated calls always hit the single configured model | `models=["llama3.2"]`, `cooldownMinutes=0`. First call: `client.chat(any())` throws `RuntimeException("temporarily unavailable")`. Second call: `client.chat(any())` returns valid JSON. | `provider.enrich(...)` called twice (first throws, caller catches; second succeeds). | First call throws (propagates — no internal retry within Ollama). Second call returns a valid `JobEnrichment`. `client.chat` is verified called exactly twice total, both targeting `"llama3.2"` (no cooldown skip, since Ollama has none). |
| **TC-OLL-06** | `provider.name()` returns the configured name | `ProviderConfig.name() == "ollama"`. | `provider.name()` called. | Returns `"ollama"`. |
| **TC-OLL-07** | Only the first model in `models` is used even if multiple are configured | `models=["llama3.2","mistral"]`. `client.chat(any())` returns valid JSON regardless of input. Capture the request. | `provider.enrich(...)`. | Captured request's `getModel()` equals `"llama3.2"` (the first entry only) — `client.chat` is verified called exactly once (no attempt at `"mistral"`). |

---

## Coverage map vs ADR §12 ("Test impact")

| ADR requirement | Cases |
|---|---|
| `GeminiJobEnricherTest` → `GeminiEnrichmentProviderTest`, same structure, new constructor | TC-GEM-01..11 |
| New `OpenAiEnrichmentProviderTest` — model chaining, cooldown, JSON parsing | TC-OAI-01..10 |
| New `OllamaEnrichmentProviderTest` — single-model call, parse | TC-OLL-01..07 |
| New `ProviderChainJobEnricherTest` — chain iteration, throw-on-exhaustion | TC-PC-01..07 |
| New `EnrichmentProviderFactoryTest` — right type per `ProviderType`, skip disabled, skip keyless non-ollama | TC-EPF-01..10 |
| `EnrichmentServiceTest` unchanged | no new cases — out of scope, already covered |

## Edge / unhappy-path inventory (cross-cutting)

- **All-providers-exhausted** → `IllegalStateException("All enrichment providers exhausted")`: TC-PC-03, TC-PC-04.
- **All-models-exhausted within a provider** → `IllegalStateException`: TC-GEM-03, TC-OAI-06.
- **Cooldown enforcement** (per-model, `cooldown-minutes > 0`): TC-GEM-02, TC-OAI-05.
- **Cooldown disabled** (`cooldown-minutes = 0`): TC-OAI-07, TC-OLL-05 (Ollama has none by design).
- **Unparseable/truncated model output**: TC-GEM-08, TC-OAI-08, TC-OLL-04.
- **Empty model output**: TC-OLL-03.
- **Config filtering** (disabled, blank key, ollama-exempt): TC-EPF-04..09.
- **Provider-type dispatch correctness**: TC-EPF-01..03.
- **Ordering preserved through filtering and chaining**: TC-EPF-08, TC-PC-05.

## Open items to flag back

- **TC-GEM-11**: whether `GeminiEnrichmentProvider`/`OpenAiEnrichmentProvider` retain any
  internal "no API key" guard, or whether that's now exclusively
  `EnrichmentProviderFactory.hasRequiredKey`'s job (per ADR §6). The ADR places the
  responsibility in the factory — TC-GEM-11 encodes that, but flag if the implementation
  differs so the case can be corrected before merge, not after.
- **TC-EPF-10 / programmatic REST client construction**: if `RestClientBuilder.build(...)`
  inside `createProviders` proves untestable without a Quarkus/CDI context, the developer
  should extract a seam (see note under section 2) — confirm at end-review which approach
  was taken so the case assertions still match.
- **TC-OAI-03 `response_format` modelling**: the exact generated-model shape for
  `response_format: {"type": "json_object"}` depends on how `openai.yaml` models
  `ChatCompletionRequest.response_format` (nested object vs `Map<String,String>`). Confirm
  the generated type at implementation time and adjust the assertion accessor accordingly —
  the *value* being asserted (`"json_object"`) does not change.
