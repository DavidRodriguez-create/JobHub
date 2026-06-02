# Code conventions

## Java

- **Java 21.** Use records, sealed classes, switch expressions and text blocks where they add clarity.
- **Lombok:** `@Getter @Builder` on domain models; `@RequiredArgsConstructor` on services that use it.
  Avoid `@Data` — domain objects are immutable.
- **Constructor injection** everywhere (no field `@Inject`).
- **No comments** unless the *why* is non-obvious. No Javadoc on straightforward getters/setters.
- Logging via `org.jboss.logging.Logger` (`Logger.getLogger(Foo.class)`); use `LOG.infof / warnf /
  errorf` for parameterised messages.
- Config via `@ConfigProperty` with a sensible `defaultValue`. Group related keys under a common
  prefix (`job.search.*`, `crawler.failure.*`).

## REST layer

- Resources: `@ApplicationScoped @Path("/noun-plural")`; both `@Produces` and `@Consumes` are
  `APPLICATION_JSON`.
- Return `Response` when a header must be set (e.g. `X-Total-Count`); otherwise return the DTO
  directly.
- Validate query params with `@Min` / `@Max`; enforce config-derived upper bounds in code too
  (`maxSize`).
- Every domain exception maps to HTTP via a `@Provider ExceptionMapper<T>` — **never** catch-and-build
  responses inside a resource method.
- Fallback `GenericExceptionMapper implements ExceptionMapper<Throwable>` at
  `@Priority(Integer.MAX_VALUE)`: passes `WebApplicationException` through, logs + returns 500 for the
  rest.
- Error body shape: `{ "error": "Human Title", "message": "detail" }`.

## Persistence

- Entities extend `PanacheEntityBase`, live in `adapter/out/persistence/entity/`.
- Explicit `@Column(name = "snake_case")` on every column; schema set on `@Table` (e.g.
  `schema = "crawler"`).
- Repository **interface** in `domain/port/out/`; implementation in `adapter/out/persistence/`
  implementing both the port and `PanacheRepositoryBase<Entity, Id>`.
- Dynamic JPQL: build with `StringBuilder` + `Map<String,Object>` params in a private `buildQuery()`
  — never concatenate user values.
- `@Transactional` on service methods that write.

## Database

- All DDL lives in `db/init/` (numbered `NNN-name.sql`). Hibernate is **never** the source of truth
  for schema in prod (`validate`).
- Dev/test use `drop-and-create` + a seed script; schemas are created by an init script before
  Hibernate starts.
- Naming: `snake_case` for tables/columns/indexes/constraints. Prefix constraints: `uq_`, `chk_`,
  `idx_`, `trg_`, `fk_`.

## Configuration profiles

Each service has three properties files:

| File | When active | Holds |
|---|---|---|
| `application.properties` | always | app name, port, shared keys |
| `application-dev.properties` | `%dev` | DevServices DB, `drop-and-create`, seed script, Swagger UI |
| `application-prod.properties` | `%prod` | `${DATABASE_URL/USERNAME/PASSWORD}`, `validate` strategy |

Tests use `src/test/resources/application.properties` (no profile suffix).
