# Adding a feature

The order is the same idea in both architectures: **start in the domain, end at the edges.** Pick
the checklist for the service's architecture.

## Hexagonal services (`job`, `crawler`, `application`)

1. **Domain first** — add the model, port interface(s), and a domain exception if needed.
2. **Service** — implement the use-case interface(s) in `domain/service/`. Inject **ports only**.
3. **Persistence** — entity → mapper → Panache repository implementing the port.
4. **REST** — resource method → DTO(s) → an `ExceptionMapper` if you added a new domain exception.
5. **Config** — add `@ConfigProperty` keys to `application.properties` with defaults; document
   dev-only keys in `application-dev.properties`.
6. **DB migration** — add a numbered SQL file under `db/init/` for schema changes. Never rely on
   `drop-and-create` in prod.
7. **Tests** — unit tests for service + mapper/DTO; component tests for the endpoint. See
   [Testing](testing.md).

## Clean service (`auth`)

1. **Entity / domain service** — add or extend a Layer 1 entity (invariants via guard methods) or a
   pure domain service.
2. **Use case** — create `application/usecase/<UseCase>/` with `Command`, `Handler`, `Response`.
   The handler injects Layer 2 **ports** (repositories, domain services) — never adapters.
3. **Ports** — if the handler needs something from the outside, add an interface in
   `application/port/out/`.
4. **Adapters** — implement the port in `adapter/out/persistence/` (JPA entity + mapper + repository),
   and wire REST in `adapter/in/rest/` (DTO ↔ Command mapper, resource method).
5. **Config / DB / Tests** — same as steps 5–7 above. Handlers are unit-testable with mocked ports.

## Definition of done

- Domain has **no** framework annotations.
- New exceptions have an `ExceptionMapper`.
- Schema change shipped as a numbered `db/init/` file.
- Unit + component tests added and green (`mvn -pl <module> test`).
- If the change touches a public endpoint, the OpenAPI contract in
  `api-contracts/src/main/resources/openapi/<service>.yaml` is updated — the
  [API reference](../api/auth.md) renders from it automatically.
