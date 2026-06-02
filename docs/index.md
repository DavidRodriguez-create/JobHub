# JobHub

JobHub is a multi-module Maven monorepo: three backend services and a frontend, sharing a single
PostgreSQL 16 database with **schema-per-service** isolation. All backends are **Quarkus 3 on
Java 21**.

```
jobhub-parent (pom.xml)
├── crawler-service      — scheduled batch crawling of job boards
├── job-service          — REST API for browsing job postings
├── auth-service         — user identity, sessions, JWT issuing
├── application-service  — tracks the user's job applications
└── JobHub-ui            — React + Vite frontend
```

## What each module does

| Module | Role | Architecture | Port (local) |
|--------|------|--------------|--------------|
| `crawler-service` | Scheduled crawling of job boards, persists postings | Hexagonal | background |
| `job-service` | Browse/search job postings | Hexagonal | 8081 |
| `auth-service` | Register/login, JWT signing, account management | Clean | 8082 (`/auth`) |
| `application-service` | Create & track job applications | Hexagonal | 8083 |
| `JobHub-ui` | React SPA | — | 5173 |
| PostgreSQL 16 | One DB, schema per service (`crawler`, `job`, `auth`, `applications`) | — | 5432 |

## Where to go next

- **Run it on your machine** → [Development → Local setup](development/local-setup.md)
- **Understand the layering** → [Architecture → Overview](architecture/overview.md)
- **Add a feature** → [Development → Adding a feature](development/adding-a-feature.md)
- **Call the APIs** → [API reference](api/auth.md) (rendered live from `api-contracts`)

!!! info "Two architectures on purpose"
    Technical, mechanistic services (`job`, `crawler`, `application`) use **Hexagonal**; the
    domain-rich `auth-service` uses **Clean**. The [Architecture overview](architecture/overview.md)
    explains when to pick which.
