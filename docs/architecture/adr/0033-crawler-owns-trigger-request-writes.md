# ADR 0033: crawler-service owns all writes to `crawler.trigger_request`

- **Status:** Proposed
- **Date:** 2026-08-16
- **Deciders:** jobhub-architect, product owner (story #574)
- **Affects:** crawler-service, job-service, api-contracts, db/init

## Context

job-service INSERTs into `crawler.trigger_request` to queue an admin pass and UPDATEs it to
cancel one. Two writers on one table breaks the "a service writes only its own schema"
invariant. "crawler owns run state" is enforced only by `insertable=false, updatable=false` on
job-service's JPA entity: Postgres still lets `job_user` overwrite `origin`, `outcome` and every
`progress_*` column. The real invariants, the active-row unique index (`db/init/060`) and the
status CHECK (`db/init/018`), live in the crawler schema.

## Decision

**crawler-service becomes the sole writer.** It exposes a minimal internal HTTP surface,
`POST /internal/trigger-requests` (queue) and `POST /internal/trigger-requests/{kind}/cancel`,
frozen in `api-contracts/.../openapi/crawler-service.yaml`. job-service authorizes the admin
(JWT group, ADR 0019 2FA) and then calls crawler-service through a `@RegisterRestClient`
interface modelled on `AuthServiceRestClient`.

**Auth is a pre-shared key, not a JWT** (ADR 0008 pattern). crawler-service gets a copy of
`ServiceKeyFilter` in `adapter/in/rest/filter/`, reading `jobhub.internal.service-key`
(`JOBHUB_INTERNAL_SERVICE_KEY`), returning 401 on a missing or wrong `X-Service-Key`. No
`quarkus-smallrye-jwt`, no published port, no proxy route: crawler-service already serves 8081
on the container network only.

Both services stay Hexagonal: new ports in `domain/port/out/`, adapters in `adapter/in/rest/`
and `adapter/out/client/`.

**Reads stay direct.** `job_user` keeps `SELECT`; the admin status/history panel is untouched.
Ticket #582 (crawler) owns `db/init/061`, revoking only INSERT and UPDATE on
`crawler.trigger_request` from `job_user`. Ticket #583 (job) gets no migration.

**Unreachable crawler is visible.** Both admin write endpoints gain a `503 Crawler Unavailable`
saying nothing was started or changed. No retry: the admin retries. crawler-service's cancel 404
maps back to the existing public 409, so the UI contract is otherwise unchanged.

## Consequences

- Queueing now depends on crawler-service being up. That is honest: previously a row was written
  that nobody would claim.
- One extra network hop and a WireMock-backed client test suite in job-service.
- The 409 double-queue rule is enforced by the owning service against its own index, so
  concurrent callers cannot both win.
- Deployment must supply `JOBHUB_INTERNAL_SERVICE_KEY` to crawler-service.

## Alternatives rejected

- **Keep the cross-schema write, tighten column grants.** Leaves two writers and no server-side
  invariant check.
- **Event/queue table hand-off.** New infrastructure for two rare operations; the admin needs a
  synchronous accept/reject answer.
- **Publish crawler-service's port and reuse the JWT.** Enlarges the attack surface and adds a
  JWT stack to a service that has none.
