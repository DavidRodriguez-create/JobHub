# auth-service

**Architecture:** [Clean](../architecture/clean.md) · **Schema:** `auth` · **Port:** 8082 ·
**Routed at:** `/auth`

## Responsibility

User identity and the JWT lifecycle. It is the **only** service that **signs** tokens (with the
private key); `job-service` and `application-service` only **verify** them (with the public key).
Because identity is domain-rich — registration, login, account state, password changes, email-based
two-factor for irreversible actions — it uses Clean architecture with explicit use cases.

## Endpoint groups

| Group | Paths | Purpose |
|-------|-------|---------|
| Public | `/register`, `/login` | Create an account; obtain a JWT |
| Account | `/account`, `/account/change-password` | Authenticated account management |
| Email 2FA | `/account/verify-email`, `/account/resend-verification`, `/account/verifications`, `/account/verifications/consume` | Email-based verification gating irreversible operations |

## Use-case structure

Each operation is an `application/usecase/<UseCase>/` triple — `Command` (input), `Handler`
(orchestration over Layer 2 ports), `Response` (output). Handlers inject `UserRepository`,
`TokenGenerator`, password/verification services — all ports, never adapters. See
[Architecture → Clean](../architecture/clean.md).

Full contract: [API reference → auth-service](../api/auth.md).
