# application-service

**Architecture:** [Hexagonal](../architecture/hexagonal.md) · **Schema:** `applications` ·
**Port:** 8083 · **Routed at:** `/applications`

## Responsibility

Tracks the user's job applications — both those started from a crawled posting and manually-entered
ones — and aggregates the metrics shown on the Dashboard. JWT **verify-only**.

## Endpoint groups

| Group | Paths | Purpose |
|-------|-------|---------|
| Applications | `/applications`, `/applications/{id}`, `/applications/{id}/status`, `/applications/{id}/job` | Create, read, update status, attach/lookup the linked job |
| Stats | `/applications/stats`, `/applications/stats/history` | Aggregate metrics + history for the Dashboard |

Status transitions are modelled in the domain; the REST layer maps domain exceptions to HTTP via
`ExceptionMapper`s (never catch-and-build in the resource).

Full contract: [API reference → application-service](../api/application.md).
