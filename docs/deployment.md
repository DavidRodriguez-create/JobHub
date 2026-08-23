# Deployment

JobHub runs in containers via the root compose files. There are two deployment situations, and
they need different procedures:

| Situation | Use |
|---|---|
| Clean machine, no database yet | [Deploy from zero](#deploy-from-zero) below |
| Stack already running with data you want to keep | [Redeploy with data kept](#redeploy-with-data-kept) below |

The difference matters because Postgres runs the `db/init/` scripts **only** on a completely empty
data directory. From zero they all run for you; on an existing volume none of them do.

For running services in dev mode with hot reload instead of containers, see
[Development → Local setup](development/local-setup.md).

---

## Deploy from zero

A clean machine to a working stack. Every command below is run from the repository root.

### 1. Prerequisites

- **JDK 21** and **Maven 3.9+**. Check with `java -version` and `mvn -v`; Maven must report
  Java 21, since the build targets release 21.
- **podman** with compose support (or docker; the compose file works with both). On Windows or
  macOS the podman machine must be running: `podman machine start`.
- Roughly **8 GB RAM** free. The stack is Postgres plus five JVM services plus the UI.

### 2. Get the code and create the env file

```bash
git clone <repository-url> JobHub
cd JobHub
cp .env.example .env
```

`.env` is git-ignored and holds the per-service database passwords. The defaults in
`.env.example` are fine for a local deployment. Two values are worth setting deliberately even
locally:

- `JOBHUB_INTERNAL_SERVICE_KEY` guards the service-to-service `/internal/*` endpoints. It must be
  **identical for every service**, which it is if you leave it in `.env` and let compose inject it.
- `TOTP_ENCRYPTION_KEY` encrypts stored two-factor secrets.

### 3. Build the backend artifacts

```bash
mvn -DskipTests package
```

The compose images copy `target/quarkus-app/` from each service, so this must run **before** the
first `up`. Use `mvn clean verify` instead if you also want the full test suite, which needs a
working container runtime for Testcontainers.

This step also generates the **JWT keypair**. Keys are never committed: the build writes a shared
dev keypair to `.dev-keys/` at the repository root and copies the public key into each service, with
auth-service also receiving the private key it signs with.

### 4. Start the stack

```bash
podman compose -f podman-compose.yml up -d --build
```

### 5. What happens on that first start

Worth knowing, because it happens exactly once per data volume:

1. Postgres initialises an empty volume and runs everything mounted into
   `docker-entrypoint-initdb.d` in numeric order: first `db/init-users.sh`, which creates one
   least-privilege user per service, then every `db/init/*.sql` migration, plus the seed files.
2. Each service starts with the Quarkus **prod** profile, where Hibernate is set to `validate`.
   It does not create or alter tables; it checks the schema the init scripts produced and refuses
   to start if anything is missing. A service that comes up healthy is therefore real evidence the
   schema is correct.

### 6. Verify

Containers first. All of them should reach `healthy`:

```bash
podman compose -f podman-compose.yml ps
```

Then the health endpoints. Note that **auth-service is root-pathed at `/auth`**, so its health
lives under that prefix and the unprefixed path returns 404:

```bash
curl -s http://localhost:8081/q/health/ready        # job-service
curl -s http://localhost:8082/auth/q/health/ready   # auth-service
curl -s http://localhost:8083/q/health/ready        # application-service
curl -s http://localhost:8084/q/health/ready        # notification-service
```

crawler-service is deliberately internal: it runs scheduled work and publishes no host port, so it
has no URL to check from outside. Verify it through `podman compose ps` and its logs.

A 200 only proves the process is alive, so finish by reading real data through the API and opening
the UI at [http://localhost:5173](http://localhost:5173):

```bash
curl -s "http://localhost:8081/jobs?page=0&size=1"
```

### 7. Optional: LLM enrichment

The crawler can enrich postings through a hosted model or a local one. Both are optional and the
stack runs fine with enrichment off.

- **Hosted:** set `GEMINI_API_KEY` in `.env` and recreate crawler-service.
- **Local:** set `CRAWLER_OLLAMA_ENABLED=true` in `.env` and start with the ollama profile, then
  pull the model once into the volume:

```bash
podman compose -f podman-compose.yml --profile ollama up -d
podman exec jobhub-ollama ollama pull llama3.2
```

Turn the whole pass off with `CRAWLER_ENRICHMENT_ENABLED=false`.

### Troubleshooting the first deploy

| Symptom | Cause and fix |
|---|---|
| A service restarts repeatedly, logs show `SchemaManagementException` | The schema does not match the JPA model. On a fresh volume this means a migration is not mounted in the compose file; see the checklist at the end of this page. |
| `curl` to auth-service returns 404 on `/q/health` | auth-service is root-pathed at `/auth`. Use `/auth/q/health/ready`. |
| Ports already in use | Something else holds 5432, 8081-8084 or 5173. Stop it, or change the published port in the compose file. |
| The UI is very slow to load on Windows or macOS | The UI container bind-mounts the source directory, and Vite reads it through the VM filesystem boundary on every request. It is slow, not broken. The backend services are unaffected. |
| Tests fail with `Could not find a valid Docker environment` | The container runtime is not reachable by Testcontainers. Restart it (`podman machine stop && podman machine start`) and re-run. This affects `mvn verify` only, not the deployed stack. |

---

## Redeploy with data kept

**Redeploying newer code onto a stack whose database volume already has data you want to keep.**
This is the normal situation once you have been running JobHub for a while.

### Why this needs its own procedure

- `db/init/*.sql` scripts only run automatically through `docker-entrypoint-initdb.d`, and Postgres
  only runs those on a **completely fresh** data directory. This is deliberate: prod runs Hibernate
  with `validate`, and `db/init/` is the single, append-only source of truth for schema (see
  CLAUDE.md → Database).
- Once a volume already exists, adding a new `db/init/NNN-*.sql` file and redeploying
  (`up -d --build`) rebuilds and restarts the service images — but the `db` container skips its
  init scripts entirely, since it's already initialized. **The new migration never runs on its
  own.**
- Skip this and the redeployed service crashes on startup: the Quarkus prod profile validates the
  JPA model against the live schema and throws
  `SchemaManagementException: missing table [...]` (or a missing-column/constraint variant) for
  whatever the un-applied migration was supposed to add.

### Procedure: redeploy and apply pending migrations

#### 1. Build the latest code

```bash
mvn -DskipTests package        # or `mvn clean verify` if you also want the test suite to run
```

#### 2. Redeploy the stack

```bash
podman compose -f podman-compose.yml up -d --build
```

This never touches the `pgdata` volume — only `down -v` does that (see
[Local setup → What persists vs what gets wiped](development/local-setup.md#what-persists-vs-what-gets-wiped)).
It rebuilds whichever service images changed and recreates those containers. On an existing
volume this step alone does **not** apply any new `db/init/` file.

#### 3. Find which migrations haven't run yet

There's no migration-tracking table — `db/init/` is a flat, numbered, forward-only list — so a
pending migration just looks like "the live schema is missing whatever this file adds." Two ways
to find the gap:

**a) You know the commit last deployed to this volume** — diff `db/init/` between that commit and
`HEAD`; every file that shows up is a candidate:

```bash
git diff --stat <last-deployed-sha> HEAD -- db/init/
```

**b) You don't know, or it's a long-lived volume** — check each recent numbered file against the
live schema directly, starting from the lowest one you're unsure about. Each file changes exactly
one kind of object, so check the matching thing:

| The migration file does this | Check with |
|---|---|
| `CREATE TABLE x.y` | `\dt x.*` — is `y` listed? |
| `ALTER TABLE ... ADD COLUMN` | `\d x.y` — is the column there? |
| `CREATE INDEX` | `\di x.idx_name` |
| `CREATE EXTENSION` | `SELECT extname FROM pg_extension WHERE extname='ext_name';` |
| Narrows/drops a `CHECK` constraint | `SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='x.y'::regclass;` |
| `GRANT ... TO role` | `\dp x.y` |

Run these against the live container, e.g.:

```bash
podman exec -i jobhub-db psql -U jobhub -d jobhub -c "\dt crawler.*"
podman exec -i jobhub-db psql -U jobhub -d jobhub -c "\dt auth.*"
```

#### 4. Apply the missing migrations, in ascending numeric order

```bash
podman exec -i jobhub-db psql -U jobhub -d jobhub < db/init/050-auth-apply-profile.sql
podman exec -i jobhub-db psql -U jobhub -d jobhub < db/init/051-job-company.sql
```

!!! warning "Check idempotency before re-running any file"
    Index/extension-only files use `IF NOT EXISTS` and are safe to re-run. Files that
    `CREATE TABLE` or run a one-time backfill are **not** idempotent — re-running them either
    errors (`relation "x" already exists`) or double-applies a backfill. Always confirm with step 3
    first; never apply "just in case."

#### 5. Restart any service that crashed on the old schema

A service that started before its migration landed will have failed Hibernate's `validate` check
and exited. Bring it back once the schema is right — no rebuild needed if the image didn't change:

```bash
podman compose -f podman-compose.yml up -d job-service auth-service
```

#### 6. Verify

Hit an endpoint that reads the changed table and confirm real data comes back, not just a 200:

```bash
curl -s "http://localhost:8081/jobs?page=0&size=1"
```

## Checklist for whoever adds the next migration

When a PR adds a new `db/init/NNN-*.sql`:

- [ ] Mount it in **both** `podman-compose.yml` and `podman-compose.native.yml`. The lists are
  maintained by hand and drift independently. A migration that exists but is not mounted silently
  never runs, and the owning service then fails `validate` on a fresh volume. Neither CI nor the
  component tests catch it, because tests use Hibernate `drop-and-create` rather than the init
  scripts. Verify with:

    ```bash
    # prints nothing when the lists are in sync
    diff <(ls db/init/ | sort)          <(grep '/docker-entrypoint-initdb.d/' podman-compose.yml             | grep -o 'db/init/[^:]*\.sql' | sed 's|db/init/||' | sort)
    ```
- [ ] If it changes a table/column/constraint that Hibernate validates, say so in the file's header
  comment and give the exact hand-apply command (`podman exec -i jobhub-db psql -U jobhub -d jobhub
  < db/init/NNN-name.sql`) — follow the existing header style in `049-job-post-facet-stamp-index.sql`
  or `050-auth-apply-profile.sql`.
- [ ] After the PR merges, apply it to any long-lived volume your team actually uses via the
  procedure above. A merged migration file does nothing on its own until someone runs it.
