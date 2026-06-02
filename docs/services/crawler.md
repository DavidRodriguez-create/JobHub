# crawler-service

**Architecture:** [Hexagonal](../architecture/hexagonal.md) · **Schema:** `crawler` · **Runtime:**
background (scheduled, no public HTTP API)

## Responsibility

Scheduled batch crawling of job boards. It fetches postings on a timer, normalizes them, and
persists them into the `crawler` schema for `job-service` to expose. It is the only service with
**outbound HTTP** (to the job boards) and the only one **without** JWT — it serves no user requests.

## Shape

- `adapter/in/scheduler/` — Quarkus `@Scheduled` jobs drive each crawl run.
- `adapter/out/client/` — MicroProfile REST Client(s) calling the external boards.
- `adapter/out/persistence/` — Panache repositories writing postings + languages.
- `domain/service/` — crawl orchestration; `crawlNext()` takes its own transaction so each target
  commits independently.

## Config

Failure/retry and scheduling knobs live under `crawler.*` prefixes (e.g. `crawler.failure.*`) in
`application.properties`, each with a sensible `defaultValue`.

!!! note "No OpenAPI page"
    crawler-service exposes no REST API, so it has no entry under [API reference](../api/auth.md).
