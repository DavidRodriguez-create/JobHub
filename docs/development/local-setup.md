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
| notification-service | http://localhost:8084 |
| PostgreSQL | localhost:5432 |

The database schema and seed data (including **demo users** for local testing) are applied
automatically from `db/init/` + `db/seeds/` on first start.

```bash
podman compose -f podman-compose.yml logs -f        # tail logs
podman compose -f podman-compose.yml down           # stop (keeps data)
podman compose -f podman-compose.yml down -v        # stop + wipe the DB volume
```

!!! tip "Re-applying schema changes"
    Seeds and DDL only run on a **fresh** data volume. After editing schema SQL, `down -v` then
    `up` so the init scripts re-run — fine here since local dev data is disposable. If you need to
    keep the data in the volume (a shared or long-lived environment), don't wipe it: see
    [Deployment](../deployment.md) for how to hand-apply just the new migration file instead.

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
mvn -pl notification-service quarkus:dev
```

Swagger UI is available in dev mode at `http://localhost:<port>/swagger`.

## Data maintenance

Your data **survives service rebuilds and restarts by default** — you have to ask for it to be
wiped. The database lives in a named volume (`pgdata`) on the `jobhub-db` container, which is
completely separate from the service containers. Rebuilding or updating a service swaps that
service's image; it never touches `pgdata`.

### Updating / reloading services (data kept)

After changing service code, repackage and bring the stack back up — only changed images are
rebuilt and recreated, the DB is left alone:

```bash
mvn -DskipTests package                                   # or: -pl job-service for just one
podman compose -f podman-compose.yml up -d --build        # rebuilds changed services only
podman compose -f podman-compose.yml up -d --build job-service   # rebuild a single service
```

Lighter options when you don't need a rebuild:

```bash
podman compose -f podman-compose.yml restart crawler-service   # restart one service
podman compose -f podman-compose.yml down                      # stop everything (volumes kept)
podman compose -f podman-compose.yml up -d                     # start again, data intact
```

### What persists vs what gets wiped

| Command | DB data (`pgdata`) | Notes |
|---------|--------------------|-------|
| `up -d --build`, `restart`, `down` then `up` | **kept** | normal update/reload cycle |
| `down -v` | **wiped** | also wipes `ollama_models` + `ui_node_modules` |

!!! danger "`down -v` is the only thing that deletes your data"
    The `-v` removes **all** named volumes. Besides the DB, that drops the pulled LLM model —
    re-pull it after the next `up`: `podman exec jobhub-ollama ollama pull qwen2.5:1.5b`.

!!! note "Seeds don't re-run on a reload"
    `db/init/` + `db/seeds/` execute **only on a fresh `pgdata` volume**. Updating services (or
    editing a seed file) won't re-apply them — that only happens after a `down -v` + `up`.

### Resetting just the data

To start from clean seed data without rebuilding service code:

```bash
podman compose -f podman-compose.yml down -v
podman compose -f podman-compose.yml up -d
```
