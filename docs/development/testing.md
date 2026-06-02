# Testing

## Strategy by layer

| Layer | Tool | Annotation |
|---|---|---|
| Domain model / DTO / mapper | JUnit 5 (plain) | none / `@ExtendWith(MockitoExtension.class)` |
| Domain service | Mockito | `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks` |
| REST adapter (happy + 4xx) | Quarkus DevServices (real Postgres) + RestAssured | `@QuarkusTest` |
| REST adapter (5xx paths) | Quarkus + Mockito | `@QuarkusTest` + `@InjectMock` on the repository |

WireMock is only added to a service that makes **outbound HTTP calls**. `job-service` has none — it
uses DevServices only.

## Test layout

Tests mirror the production package tree under two roots:

```
src/test/java/.../
  unit_tests/
    domain/service/                  XxxServiceTest
    adapter/in/rest/dto/             XxxRequestTest, XxxResponseTest
    adapter/out/persistence/mapper/  XxxMapperTest
  component_tests/
    XxxResourceComponentTest         happy + 4xx paths, real DevServices DB
    XxxResourceFailureComponentTest  5xx paths using @InjectMock
```

## Component-test rules (learned the hard way)

- **Don't** use `@TestHTTPEndpoint` with `@Nested` — it doesn't propagate into inner classes. Use a
  `private static final String BASE = "/noun"` and pass it to RestAssured.
- **Don't** use `@TestTransaction` inside `@Nested` inner classes — CDI interceptors are invalid
  there. Keep the baseline small enough that assertions work against the fixed seed.
- Seed data: `src/test/resources/db/test-seeds.sql`; the init script (`db/init-test.sql`) creates the
  schema before Hibernate.
- The `@InjectMock` class must be a **separate top-level** `@QuarkusTest` class — mixing mocked and
  real beans in one class loses the real DevServices DB.
- Assert `X-Total-Count` against **exact** seed counts when pagination is involved.

## Running tests

```bash
mvn -pl job-service test                          # one module
mvn -pl job-service test -Dtest='*unit_tests*'    # unit only
mvn -pl job-service test -Dtest='*component_tests*'  # component only
mvn test                                          # all modules
```

Podman/Docker must be running for DevServices (Testcontainers pulls a Postgres image).
