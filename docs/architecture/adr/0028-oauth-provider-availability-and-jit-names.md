# ADR 0028: OAuth provider availability, the unconfigured-provider state, and just-in-time name provisioning

- **Status:** Accepted (amended 2026-08-05 at the pre-PR review gate, see Decision 6)
- **Date:** 2026-07-31
- **Deciders:** jobhub-architect (story #506, ticket #507), orchestrator gate
- **Affects:** auth-service, api-contracts (`auth-service.yaml`, `github-oauth.yaml`), JobHub-ui
- **Extends:** ADR 0027 (social login via OAuth authorization-code)
- **Schema impact:** none (no `db/init` migration, see Decision 3)

## Context

Social login shipped with story #459 / ADR 0027 and is broken in production in three related
ways, with no signal to the user (story #506):

1. **Config crash.** `GoogleOAuthProviderClient` and `GithubOAuthProviderClient` inject
   credentials as `@ConfigProperty(name = "auth.oauth.<p>.client-id", defaultValue = "")`.
   SmallRye Config treats an *empty* value as absent, and an empty `defaultValue` therefore
   provides nothing to fall back to. Quarkus validates every `@ConfigProperty` injection point
   at startup, so an unset `GOOGLE_OAUTH_CLIENT_ID` (the documented default in `.env.example`
   and both compose files) makes the whole service fail to boot with
   `Failed to load config value of type class java.lang.String for: auth.oauth.google.client-id`.
   "Unconfigured", the state ADR 0027 explicitly designed for (404 from `/oauth/{provider}/start`),
   is currently unreachable: it is a crash-loop instead.
2. **No availability signal.** The login screen renders both social buttons unconditionally.
   A deployment that configures neither provider (the default) shows two buttons that can only
   fail. Nothing in the contract lets the UI know.
3. **Just-in-time provisioning 500.** `db/init/020-auth.sql` declares `first_name TEXT NOT NULL`
   and `last_name TEXT NOT NULL`; `CompleteOAuthLoginService.resolveUser` builds
   `User.builder().firstName(identity.getFirstName()).lastName(identity.getLastName())` straight
   from the provider profile. A real Google account returned `given_name` but no `family_name`,
   so the insert violated the not-null constraint and the callback answered 500 (story #506,
   comment 1). GitHub has the same gap in a different shape: its profile often carries only
   `name`, and sometimes only `login`.

The reporter also observed that after the 500 they appeared to be "inside the profile" but could
not log in, that GitHub reports itself "unavailable", and asked (comment 3) that the logo on the
login page navigate back to the main page.

Constraints that bound the fix:

- Contract-first: `api-contracts/.../auth-service.yaml` is the single source of truth and must be
  frozen before the backend and frontend build in parallel.
- `AccountResponse` already declares `required: [id, firstName, lastName, email, emailVerified]`,
  and it is embedded in every `LoginResponse`. Names cannot silently become null.
- auth-service is the Clean-architecture service: no framework annotations below Layer 3, handlers
  depend on ports, DTOs only at boundaries.
- The database is owned by `db/init/*.sql`, forward-only, Hibernate `validate` in prod.

## Decision

### 1. `Optional<String>` for provider credentials, so "unconfigured" is a real state

The four credential injection points become:

```java
@ConfigProperty(name = "auth.oauth.google.client-id")
Optional<String> clientId;          // no defaultValue at all
```

`isConfigured()` stays the single source of truth for "can this deployment offer the provider",
and reads `clientId.filter(v -> !v.isBlank()).isPresent() && clientSecret.filter(...).isPresent()`.
`buildAuthorizationUrl()` and `exchange()` are only ever reached through `isConfigured()`, so they
resolve the value with `orElseThrow(() -> new ProviderNotConfiguredException(provider))` rather
than `orElse("")`: an unconfigured provider must never produce a half-formed authorization URL or
a token request with an empty `client_id`.

`Optional<String>` is chosen because it is the only shape where *both* "property absent" and
"property present but empty" collapse into the same non-throwing, explicitly-modelled state, and
because the repo already uses exactly this idiom (`OAuthResource.adminEmailsConfig`). The
`application.properties` entries (`${GOOGLE_OAUTH_CLIENT_ID:}`), `.env.example` and both compose
files stay as they are: with `Optional`, an empty expansion is now correct rather than fatal.

Startup logs one INFO line per provider stating configured / not configured (names only, never
values), so a deployment can be diagnosed without guessing.

### 2. `GET /auth/oauth/providers`, unauthenticated, list-of-objects

Frozen in `auth-service.yaml`:

```
GET /oauth/providers        (server url /auth, so the UI-facing path is /auth/oauth/providers)
operationId: listOAuthProviders
security: []                 (deliberately unauthenticated: it gates the login screen)
200 -> OAuthProvidersResponse { providers: [ OAuthProviderAvailability { provider, available } ] }
500 -> ErrorResponse
```

- **List of objects, not a map or a fixed pair of booleans.** The response enumerates *every*
  provider auth-service knows about (`OAuthProvider.values()`, stable order `google`, `github`),
  each with its flag. This keeps "which providers exist" as data rather than schema: adding a
  provider later is a pure enum change plus an icon in the UI, with no property added to a model
  and no consumer break. It also leaves room for a future `reason` field without reshaping.
- **`available` means credentials are configured**, nothing else. It is not a health probe and
  makes no outbound call: an available provider can still be down, which surfaces as 502 on the
  callback. Client ids, secrets and redirect URIs are never exposed.
- **Unauthenticated is mandatory**, not a convenience: it is called before any token exists. It
  returns two booleans about deployment configuration and no user data, so it leaks nothing an
  attacker cannot already learn by clicking the button.
- **UI rule: fail open.** The login and signup screens call it once on mount, hide (not
  disable-with-an-error) the buttons whose provider is `available: false`, and on any error or
  non-200 render every known button, exactly as today. A gating call must never be able to leave a
  user with zero ways to sign in.

**Generated-interface consequence (must not be missed).** The `jaxrs-spec` generator groups by
first path segment and puts the group's longest common prefix on the interface. Adding
`/oauth/providers` shortens that prefix from `/oauth/{provider}` to `/oauth`, so `OauthApi` now
carries `@Path("/oauth")` with method paths `/{provider}/start`, `/{provider}/callback` and
`/providers`. `OAuthResource`'s class-level `@Path` is therefore changed to `/oauth` in this same
commit; the effective routes are byte-for-byte unchanged and `OAuthStartComponentTest` passes.
Ticket #510 must keep them in sync and must replace the `501 Not Implemented` scaffold in
`OAuthResource.listOAuthProviders()`.

Clean layering for the build-out:

- `application/port/in/ListOAuthProvidersUseCase` returning `List<OAuthProviderAvailability>`, a
  record next to `LoginResult` / `OAuthAuthorizationResult` in `port/in` (existing precedent).
- `application/usecase/ListOAuthProvidersService` iterates `OAuthProvider.values()` and, for each,
  finds the `OAuthProviderClient` that `supports()` it and reads `isConfigured()`. It depends only
  on the existing out-port, and gains no new port.
- `OAuthResource` maps the result to the generated `OAuthProvidersResponse`. No new resource class:
  one implementing class per generated interface is the house pattern.

### 3. Missing names: derive a non-null display name at provisioning (option b). No migration.

**Option (a), relaxing `first_name` / `last_name` to nullable, is rejected.** `AccountResponse`
declares both as `required`, and it rides inside every `LoginResponse` and `GET /account`. Making
the columns nullable without also removing them from `required` produces a contract-violating
payload; removing them from `required` is a breaking contract change that ripples into the UI
(initials, avatars, greetings) and every consumer, to model a case the product does not actually
want: a JobHub account with no name at all. The not-null constraint is not the bug. It caught the
bug. We satisfy it instead of deleting it.

**Decision: the provider adapters report raw signals; a pure domain rule derives the name.**

- `ExternalIdentity` gains two nullable raw fields, `fullName` and `username`. `GoogleIdentityMapper`
  fills `fullName` from `name` (already in `google-oauth.yaml`); `GithubIdentityMapper` fills
  `fullName` from `name` and `username` from `login`, and stops doing its own splitting. Adapters
  map, they do not decide.
- A new pure Layer 1 domain service, `domain/service/ProviderDisplayName` (zero framework
  annotations), applies one rule for both providers and returns a never-null pair:
  - `firstName` = first non-blank of: the provider's own first-name field; the first whitespace
    token of `fullName`; `username`; the local part of the normalized email; the literal `User`.
  - `lastName` = first non-blank of: the provider's own last-name field; the remainder of
    `fullName` after its first token; otherwise `""`. An empty string is the honest value for a
    mononym; we never fabricate a surname.
  - Both values are trimmed, internal whitespace collapsed, and truncated to 100 characters.
- `CompleteOAuthLoginService.resolveUser` calls it on the just-in-time provisioning branch only.
  **Names are provisioning-time only:** the auto-link branch and every subsequent login leave the
  stored name untouched, so a provider can never overwrite a name the user has edited. A derived
  name is always correctable through the existing `PATCH /account`.

Because the columns keep their `NOT NULL`, **no `db/init` migration is required, no migration
number is assigned to #510, and neither `podman-compose.yml` nor `podman-compose.native.yml` needs
a new init mount entry.** `db/init` and the compose mount lists are untouched by story #506. If a
future story genuinely wants nameless accounts, that story starts by relaxing `AccountResponse`,
not by relaxing the column.

**No partial write, no partial session (the "inside the profile" report).**

- Server side: `CompleteOAuthLoginService.handle` is a single `@Transactional` (REQUIRED) covering
  the user insert, the `user_identity` insert and token generation; there is no `REQUIRES_NEW`
  anywhere in auth-service. The failing insert therefore rolled the whole transaction back and no
  `auth.user` row was committed. The id in the log line is the id of the row that was *attempted*.
  This is now a constraint, not an accident: **the callback stays one transaction, and no code on
  that path may open a nested or independent transaction.** #510 keeps a component test asserting
  that a failed callback leaves zero rows in `auth.user` and `auth.user_identity`.
- Client side: `completeOAuthLogin` writes a token only from a 2xx body that carries one
  (`request()` throws `ApiError` on any non-2xx first), and `OAuthCallbackScreen` calls
  `onComplete` only on success. No session was created. The reported symptom is explained by a
  pre-existing session in the same browser plus the rolled-back id in the log. The UI ticket keeps
  a regression test: a 500 from the callback shows the callback error screen, leaves the stored
  token untouched, and never enters the app shell. It must **not** clear an existing token either:
  that session belongs to a different, legitimately logged-in account.

### 4. GitHub "unavailable" is a separate defect, not the unconfigured path

The finding is explicit: **this is not `ProviderNotConfiguredException` and decisions 1 + 2 do not
fix it.** The evidence chain:

1. If either GitHub credential had been empty, the pre-fix `@ConfigProperty String` injection would
   have failed auth-service's startup config validation and the service would have been
   crash-looping. It was serving the Google flow in the same log, so both GitHub values were
   present and `isConfigured()` returned true.
2. The wording the reporter saw is the UI's **502** branch (`"GitHub is unavailable right now."`),
   not the 404 branch, whose message is `"unknown or unconfigured oauth provider: github"`.
3. GitHub does not use a 4xx status for OAuth token failures. A wrong client id/secret, a stale
   `code`, or a callback URL that does not match `OAUTH_REDIRECT_BASE_URL + /oauth/github/callback`
   all come back as **HTTP 200 with no `access_token` and an `error` field**. The current
   `GithubOAuthProviderClient` treats that as success, sends `Authorization: Bearer null` to
   `/user`, receives 401, and wraps it as `ProviderUnavailableException` -> 502. A credential or
   callback-URL misconfiguration is thus reported to the user as a GitHub outage.

Scope for #510 (small, bounded): after the token exchange, a blank `access_token` throws
`ProviderAuthorizationFailedException` (401, "we could not sign you in") instead of continuing;
`error` and `error_description` are logged at WARN so the real cause is diagnosable. `error`,
`error_description` and `error_uri` are added to `GithubTokenResponse` in `github-oauth.yaml` in
this freeze. Google needs no equivalent: its token endpoint answers 4xx and is already mapped to
401. The underlying deployment cause (the GitHub OAuth App's registered callback URL) remains a
DevOps check, but after this change it fails honestly instead of blaming GitHub.

### 5. Login-page logo navigation (comment 3): no contract, no guard conflict

Confirmed safe. `JobSearchScreen` ("search") is not in `PROTECTED_ROUTES` and already takes
`authed` as a prop, so an anonymous user may browse it. The auth gate only intercepts
`route === "login" | "signup"` and protected routes, so `goto("search")` from the login screen
exits the auth screen and does not bounce back. The frontend ticket passes a navigation callback
into `LoginScreen` / `SignUpScreen` for the logo. It is deliberately **not** wired on
`OAuthCallbackScreen`: that screen is still sitting on the `/oauth/{provider}/callback` URL, whose
only correct exits are its existing "Back to sign in" action and `clearOAuthCallbackUrl()`.

### 6. The "classloader eats every `@JsonProperty` rename" reframing is rejected (amendment, 2026-08-05)

Ticket #510 reported, at the P3 gate, that Decision 3's premise was wrong: that under this
module's Jackson/Quarkus classloading a generated `api-contracts` model can resolve through a
different classloader than the one that loaded `@JsonProperty`, so Jackson's annotation-identity
check silently misses the rename and EVERY snake_case provider field (`given_name`,
`family_name`, `email_verified`, `access_token`, `error_description`) deserializes as `null`.
Two pieces of production code were added on that premise:
`adapter/out/client/ExternalProviderJsonSupport.snakeCase()` wired into the four Google/GitHub
REST clients via `@ClientObjectMapper`, and `adapter/in/rest/EnumJsonValueObjectMapperCustomizer`,
an app-wide `ObjectMapperCustomizer` enabling `WRITE_ENUMS_USING_TO_STRING`. #510 asked that
Decision 3 be downgraded to "defensive fallback".

**The reframing does not survive the evidence. Decision 3's original premise stands unchanged,
and `ProviderDisplayName` remains the primary fix, not a fallback.**

1. **The production log in story #506 falsifies it directly.** The failing insert was
   `(id, David_tests, NULL, david.tests.email@gmail.com, NULL, t, NULL, ts, ts, f)` against
   `auth.user`'s physical column order `id, first_name, last_name, email, password_hash,
   email_verified, email_verified_at, created_at, updated_at, two_factor_enabled`. So in the
   real running service `given_name` deserialized to `David_tests` and `email_verified`
   deserialized to `true`. Both are snake_case `@JsonProperty` renames on
   `GoogleUserInfoResponse`. Only `family_name` was null, which is exactly "Google supplied no
   family name" for a mononym account.
2. **`access_token` deserialized too.** The flow reached a DB insert carrying the account's real
   email, which is only obtainable from a `/v1/userinfo` call authorized with a non-null bearer
   token. Had `access_token` been null the call would have 401'd into a 502, never a constraint
   violation.
3. **Neither workaround is load-bearing in the test suite either.** Removing both and running
   `mvn -pl auth-service test` on the story branch leaves 262/262 unit and 190/190 component
   tests green, including all 29 `OAuthCallbackComponentTest` cases, the 13 new
   `OAuthCallbackNameDerivationComponentTest` cases, the 6 `GithubTokenExchangeComponentTest`
   cases, and `OAuthProvidersComponentTest` TC-506-B1, which asserts the lower-case wire value
   `providers[0].provider == "google"`. `@JsonValue` and `@JsonProperty` are honoured with the
   customizer and the `@ClientObjectMapper` methods absent.
4. **The mechanism cannot arise in a deployed service.** The duplicate-classloader precondition
   requires `jackson-annotations` to be resolved twice. The fast-jar and native runtimes use a
   single runner classloader for application classes and dependencies; the multi-classloader
   hierarchy exists only in dev mode and under `@QuarkusTest`.
5. **The fix would also have been incomplete on its own premise.** Inbound enum binding
   (`ConsumeVerificationRequest.ActionEnum`, `VerificationRequest.ActionEnum`) depends on
   `@JsonCreator`, which is annotation-identity sensitive in exactly the same way and is not
   addressed by `WRITE_ENUMS_USING_TO_STRING`. Those endpoints work.

**What the observation probably was.** The reported "13 of #459's 29 existing
`OAuthCallbackComponentTest` cases" almost certainly came from the pre-existing local
full-suite fork cascade (memory: `job-service-local-fullsuite-wsl-artifact`), where a
`@TestProfile`-triggered Quarkus reboot inside a reused fork poisons every later test in that
fork. Ticket #510 fixed that properly, in the same ticket, with the 3-bucket surefire split
(commit 115ed63). That split is the accepted fix and stays.

**Decisions:**

- `EnumJsonValueObjectMapperCustomizer` is **rejected and must be removed.** It mutates the
  app-wide `ObjectMapper` used by every auth-service response and every outbound REST client, to
  work around a symptom that does not exist. It is a no-op on today's wire format only because
  every generated enum's `toString()` already returns its `@JsonValue` wire value and no
  hand-written auth-service enum overrides `toString()` at all; the day someone adds an enum
  with a human-readable `toString()`, its wire format changes silently and globally. Production
  code whose only justification is a test observation is forbidden
  (memory: `no-production-code-for-testing`).
- `ExternalProviderJsonSupport` plus the four `@ClientObjectMapper` methods are **rejected and
  must be removed.** Beyond being unnecessary, a blanket `SNAKE_CASE` naming strategy on the
  provider clients is a second, competing source of truth for wire names alongside the
  generated `@JsonProperty` annotations, and it silently masks a genuine future spec/model
  mismatch instead of surfacing it.
- If a defence-in-depth argument is ever made for either, it needs a failing test that
  reproduces the mechanism, not a javadoc hedge. Neither is re-introduced without a new ADR.
- No other JobHub service is exposed to the alleged bug, because the alleged bug is not real.
  For the record, `ollama.yaml` (`done_reason`, `prompt_eval_count`, `eval_count`,
  `total_duration`, `load_duration`) and `openai.yaml` (`response_format`, `finish_reason`,
  `prompt_tokens`, `completion_tokens`, `total_tokens`) are the only other specs with renamed
  properties; crawler-service consumes them with no naming-strategy workaround and works in
  production, which is a third independent data point.

## Consequences

- Positive: an unconfigured deployment now boots, answers a documented 404 on `/start`, and tells
  the UI which buttons to render. The default `.env.example` (no credentials at all) becomes a
  supported configuration instead of a crash.
- Positive: the availability endpoint is additive and unauthenticated, so it costs no change to any
  existing model and no downstream consumer is affected (memory: `contract-change-check-consumers`).
- Positive: no schema change, therefore no migration ordering risk and no compose mount drift
  (memory: `compose-init-mounts-lag-migrations`).
- Cost: a derived name can be cosmetically wrong (an email local part as a first name). It is
  user-correctable through `PATCH /account`, which we judge better than either a 500 or a nameless
  account.
- Cost: the generated `OauthApi` base path changed, so `OAuthResource` and the generated interface
  must be kept in sync. Covered by the existing `OAuthStartComponentTest` / `OAuthCallbackComponentTest`.
- Known debt, deliberately not touched here: `x-implementation-status` in `auth-service.yaml` is
  stale across the file (shipped 2FA, admin and OAuth work is still marked `planned`). New work in
  this ADR is marked `planned` to match the file's current convention; a separate hygiene pass
  should reconcile the whole file rather than one section of it.

## Build-out plan

**Backend, ticket #510 (auth-service, no migration):**

1. `Optional<String>` credentials + `isConfigured()` + `orElseThrow` guards in both provider
   clients; startup INFO line per provider.
2. `ListOAuthProvidersUseCase` port in + `ListOAuthProvidersService` + replace the `501` scaffold in
   `OAuthResource.listOAuthProviders()`.
3. `ExternalIdentity.fullName` / `.username`; adapters map raw signals only;
   `domain/service/ProviderDisplayName`; call it from the JIT branch of `resolveUser`.
4. Blank-`access_token` guard in `GithubOAuthProviderClient` -> 401 + WARN log of
   `error` / `error_description`.
5. Tests: unit for `ProviderDisplayName` (Google mononym, Google no names at all, GitHub name-only,
   GitHub login-only, whitespace, over-length) and for `isConfigured()` (absent, blank, set);
   component for `GET /oauth/providers` (both configured, one configured, neither) and for the
   rollback invariant (failed callback leaves zero rows); WireMock for the GitHub HTTP-200-with-error
   token response.

**Frontend ticket:** call `GET /auth/oauth/providers` on the login and signup screens, hide
unavailable buttons, fail open on error, logo navigates to the main page, and keep the
callback-failure regression test described in Decision 3.

## Alternatives considered

- **A sentinel default (`defaultValue = "unset"`)** rejected: a magic string that silently becomes
  a real `client_id` in an authorization URL the moment one guard is missed. `Optional` makes the
  absent case unrepresentable-by-accident.
- **`@ConfigMapping` for `auth.oauth.*`** rejected for now: it is a larger refactor of four classes
  and their tests, still needs `Optional` for exactly the same reason, and buys no behaviour that
  the two-line fix does not.
- **A map response (`{"google": true, "github": false}`) or two named booleans** rejected: it
  generates a `Map<String, Boolean>` (or a model that must grow a property per provider) and turns
  "which providers exist" into schema. The list keeps that as data.
- **Making the endpoint authenticated, or folding availability into an existing endpoint**
  rejected: it gates the pre-login screen, so it must be callable with no token, and no existing
  pre-auth endpoint is a natural host.
- **Relaxing `first_name` / `last_name` to nullable (option a)** rejected: see Decision 3. It
  breaks a `required` contract field to model a case the product does not want.
- **Deriving the name in each provider adapter** rejected: two adapters would drift, which is
  precisely how GitHub ended up with a `""` surname while Google produced a null one. One pure
  domain rule, unit-tested once, covers both.
