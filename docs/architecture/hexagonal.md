# Hexagonal (Ports & Adapters)

**For:** simpler, technically-oriented services where a one-to-one mapping between REST endpoints
and use cases is natural: `job-service`, `crawler-service`, `application-service`,
`notification-service`.

## Layering

```
domain/
  model/          immutable domain objects (@Getter @Builder, no JPA annotations)
  port/
    in/           use-case interfaces (one interface per use case)
    out/          repository/client interfaces (no implementation details)
  service/        domain services implementing use-case interfaces
  exception/      domain exceptions (unchecked, extend RuntimeException)

adapter/
  in/
    rest/         JAX-RS resources + DTOs + ExceptionMappers
    scheduler/    Quarkus @Scheduled jobs
  out/
    persistence/  Panache repositories + entities + mappers
    client/       MicroProfile REST Client implementations
```

## Rules — never break these

- Domain classes have **zero** framework annotations (no JPA, CDI, JAX-RS).
- Services depend only on **port interfaces**, never on adapter implementations.
- Resources inject **use-case interfaces**, not services directly.
- DTOs live in `adapter/in/rest/dto/`; entities in `adapter/out/persistence/entity/`.
- Mappers live next to what they map:
    - entity → domain: `adapter/out/persistence/mapper/`
    - domain → response: `adapter/in/rest/dto/` (often a static `from()` on the DTO).

## Why this shape

The domain is testable without a database or HTTP server: unit-test services against mocked ports.
Adapters are swappable — switch Panache for another store by writing a new `out/persistence`
implementation of the same port, with no domain change.
