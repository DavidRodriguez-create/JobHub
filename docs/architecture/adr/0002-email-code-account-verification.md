# ADR 0002: Email-code account verification (verify-before-login)

- **Status:** Accepted
- **Date:** 2026-06-06
- **Deciders:** Principal Architect (jobhub-architect); David R H
- **Affects:** auth-service (Clean), api-contracts (auth-service.yaml), db/init, podman-compose (DevOps)

## Context

Story #6 (sub-issue #21) requires that on registration a user receives an email containing
a **6-digit code**, and must enter that code to verify their account **before** they can log
in. Email delivery is built now; SMTP is supplied by deploy config (dev/test mock, prod SMTP
via env). This ADR is the contract-freeze gate that backend (#24), frontend (#25), and
devops (#26) build on.

auth-service is the project's **Clean-architecture** service (domain-rich identity, sessions,
token lifecycle). The Dependency Rule applies: `domain/` and `application/` carry no framework
annotations; persistence and email live in `adapter/out/`. The API surface is contract-first
in `api-contracts/.../openapi/auth-service.yaml` (interface-only generation). The schema is
owned by `db/init/*.sql`; Hibernate is `validate` in prod and never the schema source.

The worktree already contains two parallel verification mechanisms:

1. `auth.email_verification_token` + `EmailVerificationToken` + `EmailVerificationService` —
   an **opaque single-use UUID token** (24h TTL) emailed at registration; consumed by
   `POST /auth/account/verify-email { token }`.
2. `auth.verification_code` + `VerificationCode` + `AccountVerificationService` — a
   **hashed 6-digit code** (15-min TTL) scoped by a `VerificationAction` CHECK
   (`delete-account`, `delete-all-applications`), issued for *authenticated* destructive
   actions and keyed on `(userId, verificationId)`.

The locked product decision is a **6-digit code**, which is mechanism (2), not (1).

## Decision

We will deliver email verification as a **6-digit code stored in `auth.verification_code`**
under a new `verify-email` action, and **retire the opaque-token mechanism**.

1. **Code storage — reuse `verification_code` (option b).** Add `'verify-email'` to the
   `verification_code.action` CHECK. The table already stores a hashed code, `expires_at`
   (15-min via `auth.verification.code-ttl-seconds=900`), `consumed_at`, and `user_id`, and
   the `VerificationCode` entity / `VerificationCodeRepository` port / mapper / generate-hash-
   consume logic are reusable as-is. We do **not** add `code_hash`/`expires_at` to
   `email_verification_token` (option a): that bolts a second identity model onto a table
   whose `UNIQUE(token)` / `token NOT NULL` shape is structurally wrong for short hashed codes.

2. **Pre-login lookup by `{email, code}`.** Email verification runs before login, so the
   client holds no JWT and no `verificationId`. Verification resolves `user_id` from `email`,
   then matches the **newest unconsumed `verify-email` code** for that user and checks the
   hash + expiry. This is an additive `VerificationCodeRepository` lookup
   (`findActiveByUserAndAction(userId, VerificationAction)` or equivalent); the existing
   `(userId, verificationId)` destructive-action path is unchanged.

3. **Verify-before-login.** `LoginService` rejects an otherwise-valid login when
   `!user.isEmailVerified()` with a new `EmailNotVerifiedException` → **HTTP 403**
   `{ error: "Email Not Verified", message: ... }`. Bad credentials stay **401** and are
   indistinguishable from an unknown email (no account enumeration).

4. **Contract (frozen, all new/changed ops `x-implementation-status: planned`):**
   - `POST /register` → **201 `RegisterResponse { account, verificationRequired:true }`**;
     creates the account unverified and emails a code.
   - `POST /auth/account/verify-email { email, code }` → **200 `AccountResponse`** on success;
     **400** invalid/expired/used; **429** repeated failed attempts. Public (`security: []`).
   - `POST /auth/account/resend-verification { email }` → **204** (silent no-op to avoid
     enumeration); **429** when throttled. Issues a fresh code, invalidating prior ones.
   - `POST /login` gains **403** for unverified email.

5. **Rate-limit & expiry.** Code lifetime stays **15 minutes** (`code-ttl-seconds=900`).
   `resend-verification` is **throttled per email address** (429) and `verify-email` is
   **throttled on repeated failed attempts** (429) to bound brute force over the 10^6 space.
   Throttle thresholds are config-driven (`auth.verification.*`) with sane defaults; exact
   counter mechanism is a backend (#24) decision, not part of the frozen contract.

6. **Mailer — `VerificationNotifier` port, profile-selected adapters.** Keep the outbound
   port `application/port/out/VerificationNotifier`. Add a prod `MailerVerificationNotifier`
   in `adapter/out/notification/` using Quarkus Mailer (`io.quarkus.mailer.Mailer`); keep
   `LoggingVerificationNotifier` for dev/test. Select by profile so exactly one bean is active
   (`@IfBuildProfile("prod")` on the mailer adapter; `@UnlessBuildProfile("prod")` or
   `@DefaultBean` on the logging adapter). SMTP is configured **only** via env in prod —
   **no credentials are committed**. The domain/application layers depend on the port only.

7. **Migration `db/init/022-auth-email-verification.sql` (forward-only).** Widens the
   `verification_code` action CHECK, adds `idx_verification_code_user_action`, and
   `DROP TABLE IF EXISTS auth.email_verification_token`. The token table is unreleased and
   holds no production data; nothing seeds it. DevOps (#26) mounts the file in
   `podman-compose.yml` after `021-auth-seeds.sql`.

## Consequences

- Positive: one verification primitive (`verification_code`) for both email verification and
  destructive actions — less schema, less code, one mapper/entity/port to maintain. The
  6-digit UX matches the product decision and the existing hash-and-consume security model.
- Positive: the mailer is a swap-in adapter behind an existing port; dev/test need no SMTP.
- Negative / cost: the backend must **delete** `EmailVerificationToken`, its repository,
  mapper, entity, `EmailVerificationTokenRepository` port, and fold/rename
  `EmailVerificationService`'s `sendFor/verify/resend` onto the `verification_code` path
  (its `SendEmailVerificationUseCase`, `VerifyEmailUseCase`, `ResendVerificationUseCase`
  ports stay, their implementation changes). The `verify-email` flow needs a new
  email→user→active-code lookup and a throttle.
- Negative / cost: prod gains a Quarkus Mailer dependency and four required env keys; without
  them prod email silently fails (mitigate with a deploy-time check — DevOps #26).
- Follow-ups: backend #24 (use-case/handler rewrite + `EmailNotVerifiedException` +
  ExceptionMapper→403 + throttle), frontend #25 (code-entry screen keyed off
  `verificationRequired` / login 403), devops #26 (compose mount for 022 + `quarkus.mailer.*`
  env wiring + secrets).

## Mailer config — keys for DevOps (#26)

Prod SMTP is env-only. The `MailerVerificationNotifier` reads standard Quarkus Mailer config;
add to `auth-service/src/main/resources/application-prod.properties` mapped to env vars (no
values committed), and define the env in `.env.example` / compose for auth-service:

| Quarkus property | Env var (suggested) | Notes |
|---|---|---|
| `quarkus.mailer.from` | `MAILER_FROM` | e.g. `no-reply@jobhub.example` |
| `quarkus.mailer.host` | `MAILER_HOST` | SMTP host |
| `quarkus.mailer.port` | `MAILER_PORT` | e.g. 587 |
| `quarkus.mailer.username` | `MAILER_USERNAME` | secret |
| `quarkus.mailer.password` | `MAILER_PASSWORD` | secret — never commit |
| `quarkus.mailer.start-tls` | `MAILER_START_TLS` | `REQUIRED` for 587 |
| `quarkus.mailer.mock` | (n/a) | leave default: off in prod, on in dev/test |

## Alternatives considered

- **Option (a): add `code_hash`+`expires_at`(+attempts) to `email_verification_token`** —
  rejected. Forces a hashed-code model onto a table built for unique opaque tokens
  (`UNIQUE(token)`, `token NOT NULL`), leaving dead columns and two divergent code paths.
- **Keep the opaque link-token UX** — rejected; the product decision is a 6-digit code.
- **Keep both mechanisms** (token for email-verify, code for destructive actions) — rejected;
  two tables and two services for one concept (a short-lived emailed challenge) is needless
  duplication and a maintenance trap.
- **Rely on Quarkus Mailer's built-in mock instead of `LoggingVerificationNotifier`**
  (`quarkus.mailer.mock` is on by default in dev/test) — viable, but keeping the logging
  adapter means dev/test never construct the Mailer, component tests keep asserting on the
  existing masked log line, and the port abstraction stays explicit. Recorded as the
  fallback if we later drop the logging adapter.
- **Block login at the JWT/policy layer instead of in `LoginService`** — rejected; the
  unverified-state rule is a domain/use-case concern and belongs in the handler, not in an
  adapter-level security filter.
