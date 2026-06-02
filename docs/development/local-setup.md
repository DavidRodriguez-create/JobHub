# Local setup

This page gets JobHub running on your own machine. There are two ways:

- **Full stack in one go** (compose) — everything (DB + 4 services + UI) in containers. Best to just
  *use* the app.
- **Dev mode** (`quarkus:dev`) — hot reload on a single service. Best to *work on* a service.

## Prerequisites

- **Java 21** and **Maven** (`mvn -v`).
- **Podman** (or Docker) with `podman compose`.
- **Node 20 + pnpm** (only if you run the UI outside containers).

!!! note "Windows / macOS — start the Podman machine first"
    Podman runs containers inside a small Linux VM. Start it once per session:
    ```powershell
    podman machine start
    ```
    If it fails to start, update the backend and retry: `wsl --update` then `wsl --shutdown`
    (Windows), then `podman machine start`.

## Option A — Full stack (compose)

The backends are packaged first because the images copy `target/quarkus-app/`.

```bash
# 1. Build the backend artifacts
mvn -DskipTests package

# 2. Bring up DB + services + UI
podman compose -f podman-compose.yml up -d --build
```

Then open:

| What | URL |
|------|-----|
| UI | http://localhost:5173 |
| job-service | http://localhost:8081 |
| auth-service | http://localhost:8082/auth |
| application-service | http://localhost:8083 |
| PostgreSQL | localhost:5432 |

The database schema and seed data (including **demo users** for local testing) are applied
automatically from `db/init/` + `db/seeds/` on first start.

```bash
podman compose -f podman-compose.yml logs -f        # tail logs
podman compose -f podman-compose.yml down           # stop (keeps data)
podman compose -f podman-compose.yml down -v        # stop + wipe the DB volume
```

!!! tip "Re-applying schema changes"
    Seeds and DDL only run on a **fresh** data volume. After editing schema SQL, run
    `down -v` then `up` so the init scripts re-run.

## Option B — Dev mode (hot reload)

Run just the DB in a container, then a service in dev mode (auto-reload on save):

```bash
# DB only
podman run -d --name jobhub-db \
  -e POSTGRES_USER=jobhub -e POSTGRES_PASSWORD=jobhub -e POSTGRES_DB=jobhub \
  -p 5432:5432 docker.io/library/postgres:16-alpine

# A service in dev mode (separate terminal each)
mvn -pl job-service quarkus:dev
mvn -pl crawler-service quarkus:dev
```

Swagger UI is available in dev mode at `http://localhost:<port>/swagger`.

## Running this documentation locally

The docs are MkDocs Material with a plugin for live OpenAPI. Build the image once, then serve —
**from the repo root** so the OpenAPI hook can see `api-contracts/`:

```bash
podman build -t jobhub-docs docs/
podman run --rm -p 8000:8000 -v ${PWD}:/docs jobhub-docs   # → http://localhost:8000
```

Edits to `docs/**` or to the contracts in `api-contracts/` show up on rebuild — the
[API reference](../api/auth.md) is generated from the contracts, never hand-copied.
