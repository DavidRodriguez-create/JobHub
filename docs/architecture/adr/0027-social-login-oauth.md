# ADR 0027: Social login (Google/Gmail and GitHub) via OAuth authorization-code

- **Status:** Accepted
- **Date:** 2026-07-30
- **Deciders:** jobhub-architect (story #459), orchestrator gate
- **Affects:** auth-service, api-contracts (auth-service.yaml), db/init, JobHub-ui

## Context

Story #459 ("Allow Gmail and GitHub login") adds social sign-in alongside the existing
email/password flow. auth-service is our one **Clean architecture** service (rich identity
domain: users, sessions, 2FA, verification codes). It has **no outbound REST clients today**;
social login introduces outbound calls to provider token and userinfo endpoints, so the
backend ticket needs WireMock for the first time here.

Constraints in scope:

- Contract-first: `api-contracts/src/main/resources/openapi/auth-service.yaml` is the single
  source of truth (interface-only generation). External APIs we consume are also modelled in
  api-contracts, with the `@RegisterRestClient` interface kept in the consumer (memory:
  `api-contracts-external-apis`).
- Schema is owned by `db/init/*.sql`, forward-only, Hibernate `validate` in prod.
- Clean layering must hold: no framework annotations below Layer 3; handlers depend on ports.
- The existing `auth.user.password_hash` is `NOT NULL`; social-only accounts have no password.

## Decision

We will add **authorization-code social login** for two providers, `google` (Gmail) and
`github`, with the **code exchange performed server-side in auth-service** (confidential
client, using the provider client secret). The flow issues the **same JWT and same
`LoginResponse`** as `POST /auth/login`.

### Flow (SPA-relay, no JWT in URLs)

1. UI calls `GET /auth/oauth/{provider}/start`. auth-service returns
   `OAuthAuthorizationResponse { authorizationUrl }` and binds an opaque `state`
   (HttpOnly, SameSite=Lax cookie) for CSRF protection.
2. UI redirects the browser to `authorizationUrl`. The provider `redirect_uri` points at the
   **UI** route `{APP_BASE_URL}/oauth/{provider}/callback`, not at auth-service.
3. After consent the provider redirects the browser back to the UI with `code` + `state`.
   The UI relays them as `POST /auth/oauth/{provider}/callback`.
4. auth-service validates `state` against the bound cookie, exchanges `code` for a provider
   access token (outbound HTTP), reads userinfo, creates-or-links the account, and returns
   `LoginResponse`. If the resolved account has app-level TOTP enabled it returns the same
   first-step challenge (`twoFactorRequired` + `twoFactorToken`); the UI completes it via the
   existing `POST /auth/login/2fa`.

We avoid PKCE-state-in-browser leakage and an extra DB table by using a **confidential client
plus a cookie-bound `state`**: the code exchange carries the client secret server-side, and
CSRF is covered by the state cookie. No `oauth_authorization_request` table is needed.

### Clean layering (auth-service)

- `application/port/in/`: `StartOAuthAuthorizationUseCase`, `CompleteOAuthLoginUseCase`
  (+ `OAuthCallbackCommand`, reuse `LoginResult`).
- `application/port/out/`: `OAuthProviderClient` (exchange code, fetch userinfo, returns a
  domain `ExternalIdentity`), `UserIdentityRepository`. Handlers reuse `UserRepository` and
  `TokenGenerator`.
- `domain/`: `UserIdentity` entity (`provider`, `providerUserId`, `userId`), `ExternalIdentity`
  value object (`provider`, `providerUserId`, `email`, `emailVerified`, `firstName`,
  `lastName`), new exceptions (`OAuthStateMismatchException`, `ProviderUnavailableException`,
  `UnverifiedProviderEmailException`).
- `adapter/out/client/{google,github}/`: the `@RegisterRestClient` interfaces (kept in the
  consumer) + one `OAuthProviderClient` implementation per provider referencing the generated
  provider models. This is auth-service's first outbound adapter, hence WireMock in tests.
- `adapter/out/persistence/`: `UserIdentityEntity` + `UserIdentityJpaMapper` +
  `UserIdentityJpaRepository`.

### Account-linking rule

On callback, resolve identity in this order:

1. **Existing link:** a `user_identity` row matches `(provider, provider_user_id)` -> log that
   user in.
2. **Auto-link (verified email only):** the provider reports an email AND flags it verified
   AND an `auth.user` exists with that email -> attach a new `user_identity` to that user, set
   `email_verified = true`, and log in.
3. **Just-in-time provision:** no matching user -> create a new **password-less** user
   (`password_hash NULL`, `email_verified` = provider's verified flag, name from provider),
   create the `user_identity`, log in.
4. **Refuse (unverified collision):** the provider email is NOT provider-verified and it
   collides with an existing account -> reject with 401 and ask the user to sign in with their
   existing method and link from settings. Do not link, do not provision onto that email.

**Security reasoning:** auto-linking a federated identity to an existing local account by email
is only safe when the email is **provider-verified**. Otherwise an attacker could set a
victim's address as an unverified email at the provider and take over the victim's JobHub
account. Google: honour the `email_verified` claim. GitHub: userinfo omits email unless the
`user:email` scope is granted; call `/user/emails` and select the entry that is both `primary`
and `verified`.

## Consequences

- Positive: reuses the existing JWT, `LoginResponse`, and 2FA second step unchanged; additive
  contract; social-only accounts are first-class; no new state table.
- Positive/additive: no existing auth response model changes, so downstream consumers
  (notification-service et al.) are unaffected (memory: `contract-change-check-consumers`).
- Negative/cost: auth-service gains its first outbound HTTP dependency (provider token +
  userinfo), so it now needs WireMock and provider-failure handling (502). `password_hash`
  becomes nullable, so every password path must guard a null hash (see follow-ups).
- Follow-ups:
  - **Schema (forward-only), assigned range db/init/055 to 056 for backend ticket #495:**
    - `055-auth-password-hash-nullable.sql`: `ALTER TABLE auth.user ALTER COLUMN password_hash DROP NOT NULL;`
    - `056-auth-user-identity.sql`: create `auth.user_identity` (see below).
    - Set `UserEntity.password_hash` `@Column(nullable = true)` to match.
  - **Password-path audit (null-hash guards), backend ticket #495:**
    - `LoginService.login()`: if `user.getPasswordHash() == null` throw
      `InvalidCredentialsException` (401, same as bad password: no enumeration, no NPE from
      the BCrypt matcher).
    - `ChangePasswordService.changePassword()`: a password-less (social-only) account has no
      current password; guard the null hash and return `InvalidCredentialsException` (a
      "set password for social accounts" flow is a separate future story).
    - `RegisterUserService`: unchanged (password registrations still set a non-null hash;
      email uniqueness still yields 409 against a social-only account).
  - **DevOps:** add `055`/`056` to BOTH compose files' init mount lists (memory:
    `compose-init-mounts-lag-migrations`); only a fresh `podman compose up -v` applies them.
  - **Provider models in api-contracts:** model Google/GitHub token + userinfo responses as
    models-only external specs (`google-oauth.yaml`, `github-oauth.yaml`,
    `<generateApis>false</generateApis>`) per the external-API convention; the
    `@RegisterRestClient` interface stays in auth-service. Plugin wiring belongs to ticket #495
    (kept out of this design freeze to avoid touching reactor build config).
  - **Config (sensible dev placeholders, no test-only prod code):**
    `auth.oauth.<provider>.client-id` / `.client-secret` (default empty),
    `auth.oauth.redirect-base-url` (default `http://localhost:5173`), and provider endpoint
    URLs as config with real defaults.

### `auth.user_identity` (056)

```sql
CREATE TABLE auth.user_identity (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES auth.user(id) ON DELETE CASCADE,
    provider          VARCHAR(20) NOT NULL CHECK (provider IN ('google', 'github')),
    provider_user_id  TEXT        NOT NULL,
    email             TEXT,                 -- provider-reported email at link time (audit only)
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_identity_provider_subject UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_user_identity_user_provider    UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_identity_user ON auth.user_identity (user_id);
```

`uq_user_identity_provider_subject` makes one provider account map to exactly one JobHub user;
`uq_user_identity_user_provider` caps a user at one identity per provider.

## Alternatives considered

- **SPA (public client) code exchange with PKCE** rejected: the client secret and token
  exchange stay server-side in our confidential client, which is stronger and keeps provider
  secrets out of the browser; a cookie-bound `state` covers CSRF without PKCE-in-browser.
- **auth-service as the browser redirect target (GET callback)** rejected: it would force the
  JWT back through a URL fragment/redirect to the UI; relaying `code`+`state` via a UI POST
  keeps tokens out of URLs and reuses `LoginResponse` verbatim.
- **A sentinel password hash instead of nullable** rejected: a sentinel invites accidental
  "matches" edge cases and hides intent; a nullable column plus explicit null guards is clearer.
- **Blocking (never auto-linking) or always-linking by email** rejected: block-only fragments a
  user into duplicate accounts; always-link enables takeover via unverified provider emails.
  Verified-email auto-link is the safe middle.
