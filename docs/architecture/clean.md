# Clean (Concentric layers)

**For:** complex, domain-rich services where business rules and entity state are central —
`auth-service` (user identity, permissions, sessions, token lifecycle).

> Reference: [Uncle Bob — The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

## The dependency rule

**Source-code dependencies can only point inward.** Inner layers define interfaces; outer layers
implement them. Business logic stays independent of frameworks.

## Four concentric layers

```
domain/                          ← Layer 1: Entities
  entity/                          immutable value objects, mutable aggregates
  exception/                       domain exceptions only
  service/                         pure domain logic (entities in, entities out)

application/                     ← Layer 2: Use cases & ports
  port/out/                        interfaces the outer layers implement (UserRepository, TokenGenerator)
  usecase/<UseCase>/
    <UseCase>Command.java          input (immutable)
    <UseCase>Handler.java          orchestrates domain + ports
    <UseCase>Response.java         output (immutable)

adapter/                         ← Layer 3: Interface adapters
  in/rest/    (AuthResource, DTOs, mappers)
  out/persistence/  (JPA entities, mappers, repository implementations)

⬆ Frameworks & drivers (Quarkus, Panache, JAX-RS)
```

## Interfaces at each boundary

- **Layer 1 → 2:** domain entities are the interface; handlers take/return entities.
- **Layer 2 → 3:** handlers define port interfaces in `application/port/`; adapters implement them.
- **REST → Layer 2:** the resource receives a REST DTO, builds a `Command`, calls the handler.

```java
// adapter/in/rest/AuthResource.java (Layer 3)
@Inject LoginHandler login;

@POST @Path("/login")
public LoginResponse login(LoginRequest req) {       // REST DTO
  LoginCommand cmd = LoginCommand.builder()
      .username(req.username()).password(req.password()).build();
  return login.handle(cmd);                            // handler works with entities
}
```

## Key rules

- **Entities enforce invariants.** Private setters + guard methods: `user.recordLogin()`, not
  `setLastLogin()`.
- **Handlers orchestrate.** They inject repositories and domain services (Layer 2 ports), **never**
  adapters.
- **Repositories are Layer 2 ports.** Interface in `application/port/out/`, implementation in
  `adapter/out/`.
- **DTOs only at boundaries.** REST DTO ↔ Command at the REST entry; domain entity ↔ JPA entity at
  the persistence edge.
- **No framework annotations below Layer 3.** Zero `@Entity`, `@Transactional`, `@Path` in `domain/`
  or `application/`.
