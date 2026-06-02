# Architecture — Decision guide

JobHub deliberately uses **two** architectures. Pick based on complexity and whether domain logic
dominates.

| Architecture | Use when | Services |
|---|---|---|
| **[Hexagonal](hexagonal.md)** | Technical service: few, mechanistic use cases where REST/persistence are the main complexity | `job-service`, `crawler-service`, `application-service` |
| **[Clean](clean.md)** | Complex domain: rich business rules, entities with state, many interrelated use cases where domain logic dominates | `auth-service` (identity, permissions, sessions, token lifecycle) |

## The shared principle

Both architectures enforce the same core rule: **source-code dependencies point inward, toward the
domain.** Frameworks (Quarkus, Panache, JAX-RS) live at the edges; the domain never imports them.

- Domain classes carry **zero framework annotations** — no JPA, no CDI, no JAX-RS.
- Outer layers implement interfaces defined by inner layers.
- DTOs exist only at boundaries; entities only at the persistence edge.

## How they differ

```
Hexagonal (job/crawler/application)        Clean (auth)
───────────────────────────────────        ───────────────────────────────
domain/                                     domain/        (entities + pure services)
  model/  port/in,out/  service/            application/   (use cases + ports)
adapter/in/rest|scheduler                   adapter/in/rest, out/persistence
adapter/out/persistence|client
one use-case interface per endpoint         one Command/Handler/Response per use case
```

Hexagonal maps REST endpoints one-to-one to use cases. Clean adds an explicit **use-case layer**
(Command → Handler → Response) so business rules and entity state have room to live.

See the dedicated pages for the full layering rules and worked examples:
[Hexagonal](hexagonal.md) · [Clean](clean.md).
