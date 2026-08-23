# crawler-service

**Architecture:** [Hexagonal](../architecture/hexagonal.md) · **Schema:** `crawler` · **Runtime:**
background (scheduled, no public HTTP API)

## Responsibility

Scheduled batch crawling of job boards. It fetches postings on a timer, normalizes them, and
persists them into the `crawler` schema for `job-service` to expose. It is the only service with
**outbound HTTP** (to the job boards) and the only one **without** JWT — it serves no user requests.

## Shape

- `adapter/in/scheduler/` — Quarkus `@Scheduled` jobs: the crawl run and the enrichment pass.
- `adapter/out/client/` — MicroProfile REST Clients, grouped into `source/` (job-board clients),
  `enrichment/` (the Ollama enricher) and `support/` (parsers/converters/helpers).
- `adapter/out/persistence/` — Panache repositories writing postings + languages.
- `domain/service/` — crawl orchestration; `crawlNext()` takes its own transaction so each target
  commits independently.

## Config

Failure/retry, scheduling and enrichment knobs live under `crawler.*` prefixes
(`crawler.crawl.*`, `crawler.failure.*`, `crawler.enrichment.*`) in `application.properties`,
each with a sensible `defaultValue`. The LLM enrichment pass can be switched off at deploy time —
see [Enrichment](#enrichment-llm).

## Enrichment (LLM)

After a crawl, an asynchronous pass enriches `pending` postings with an LLM: it fills
`employment_type`, `career_level`, `languages` and `requirements`, and normalises the location
from the posting text, then stamps `enrichment_status` (`pending` → `done`/`failed`). It runs on
its own schedule (`crawler.enrichment.cron`) and processes a bounded batch per pass
(`crawler.enrichment.batch-size`); overlapping runs are skipped.

### Hosted-first with local fallback

`FallbackJobEnricher` is the default `JobEnricher`. Each posting is enriched **hosted-first**
(Google Gemini — fast, free tier, runs off this box's CPU), and on **any** hosted failure
(429/rate-limit, HTTP error, timeout, or unparseable response) it transparently **falls back to
the local model** (Ollama). A row is only marked `failed` if *both* paths fail. Both paths share
the same system prompt + schema (`EnrichmentPrompt`) and the same defensive parser
(`EnrichmentParser`), so their output is interchangeable.

```
EnrichmentService ──▶ JobEnricher (@Default = FallbackJobEnricher)
                                     ├─ try  @HostedEnricher  GeminiJobEnricher  (primary)
                                     └─ on failure → @LocalEnricher OllamaJobEnricher (fallback)
```

The two concrete enrichers are distinguished by the `@HostedEnricher` / `@LocalEnricher` CDI
qualifiers; `EnrichmentService` injects the unqualified default and is unaware of the split.

### Configuration

| Key (env) | Default | Purpose |
|---|---|---|
| `CRAWLER_ENRICHMENT_ENABLED` | `true` | Master switch; `false` → scheduler no-ops, no calls made |
| `GEMINI_API_KEY` | *(blank)* | Hosted key. Blank → hosted skipped, **local-only** |
| `CRAWLER_ENRICHMENT_HOSTED_ENABLED` | `true` | `false` → force local-only even with a key |
| `CRAWLER_ENRICHMENT_HOSTED_MODEL` | `gemini-2.5-flash-lite` | Hosted model |
| `CRAWLER_ENRICHMENT_MODEL` | `qwen2.5:1.5b` | Local fallback model |

Get a free Gemini key (no card) from <https://aistudio.google.com/apikey> and set it in `.env`.
With no key the crawler runs local-only, so it still works — just slower on the shared CPU. The
local path needs the `ollama` service reachable at `OLLAMA_BASE_URL` (`http://ollama:11434` in
compose) with the model pulled once:

```bash
podman exec jobhub-ollama ollama pull qwen2.5:1.5b
```

!!! note "CPU inference is slow (fallback only)"
    The hosted path is sub-second. The local fallback runs on CPU, where a single chat call can
    take minutes, so the Ollama read-timeout is 600s (`quarkus.rest-client.ollama.read-timeout`).
    Lower it where Ollama runs on a GPU.

!!! note "No OpenAPI page"
    crawler-service exposes no REST API, so it has no entry under [API reference](../api/auth.md).
