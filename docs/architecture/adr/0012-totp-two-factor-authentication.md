# ADR 0012: TOTP Two-Factor Authentication

- **Status:** Accepted
- **Date:** 2026-06-22
- **Deciders:** David R H (owner), jobhub-architect
- **Affects:** auth-service, notification-service, api-contracts, db/init, JobHub-ui

## Context

Users have no second factor protecting their accounts. A compromised password gives
full access to the account, its applications, and all personal data stored in JobHub.
Story #133 / sub-issue #168 adds TOTP-based two-factor authentication (RFC 6238) as an
opt-in security feature. The auth-service follows Clean Architecture (CLAUDE.md decision
guide), so the domain layer must own invariants (secret lifecycle, code validation
interface) while adapters handle the TOTP library and persistence.

Key constraints that shape the design:

1. The existing `POST /auth/login` contract returns `{ token, expiresIn, account }` and
   is consumed by the UI and potentially by the application-service rest client. Adding a
   mandatory field would break non-2FA users. The change must be backward-compatible.
2. TOTP secrets are high-value cryptographic material (equivalent to a password). They
   must be encrypted at rest in the database, not stored as plaintext.
3. Backup/recovery codes are needed so users are not permanently locked out if they lose
   their authenticator device.
4. The existing email-based verification flow (`VerificationCode`, `VerificationAction`)
   is for destructive-action confirmation. TOTP is a fundamentally different mechanism
   (time-based, device-bound, not email-delivered) and must not be conflated with it.
5. The notification-service should send a one-time SYSTEM notification recommending 2FA
   setup to newly registered users.

## Decision

### 1. TOTP secret storage: separate table, not a column on auth.user

We will store TOTP configuration in a new `auth.totp_secret` table rather than adding
columns to `auth.user`. Rationale:

- Separation of concerns: TOTP data (encrypted secret, backup codes, verified-at
  timestamp) is a distinct lifecycle from the user profile.
- The `auth.user` table already has nine columns; adding five more dilutes its purpose.
- A separate table allows a clean `1:0..1` relationship: no TOTP row means 2FA is not
  enabled, which is the natural default.
- The table can be independently audited, backed up, or rotated.

A `two_factor_enabled` boolean column will be added to `auth.user` as a denormalized
read flag, so the login flow and account response can check 2FA status without joining
the TOTP table on every request. This flag is set to `true` only after setup verification
succeeds and set to `false` when 2FA is disabled.

### 2. Login flow: two-step with a short-lived 2FA challenge token

For users with 2FA enabled, login becomes two steps:

**Step 1** (`POST /auth/login`, existing endpoint):
- Validates email + password as today.
- If 2FA is NOT enabled: returns the full `LoginResponse` (unchanged behavior).
- If 2FA IS enabled: returns HTTP 200 with a new response shape
  `TwoFactorChallengeResponse` containing a `twoFactorToken` (short-lived, opaque,
  NOT a JWT) and `twoFactorRequired: true`. The `token` and `account` fields are absent.

To maintain backward compatibility, the existing `LoginResponse` schema gains two
optional fields: `twoFactorRequired` (boolean, default false) and `twoFactorToken`
(string, nullable). When `twoFactorRequired` is true, the `token` field is null and
the UI must proceed to step 2. When false, behavior is identical to today.

**Step 2** (`POST /auth/login/2fa`, new endpoint):
- Accepts `{ twoFactorToken, totpCode }`.
- Validates the challenge token (not expired, not consumed, belongs to the right user).
- Validates the TOTP code against the user's stored secret.
- On success: returns the full `LoginResponse` (same shape as a non-2FA login).
- The challenge token is single-use and expires after 5 minutes.

The challenge token is stored in a new `auth.two_factor_challenge` table, not as a JWT,
because: (a) it must be single-use (consumed on success), (b) it carries no claims the
client needs, (c) it should be invalidatable server-side.

### 3. 2FA setup flow

1. `POST /account/2fa/setup` (authenticated): generates a new TOTP secret, stores it
   as unverified in `auth.totp_secret`, and returns the `otpauth://` URI (for QR code
   generation on the client) plus a base32-encoded setup key for manual entry.
2. `POST /account/2fa/verify-setup` (authenticated): accepts `{ totpCode }`, validates
   it against the unverified secret, marks it as verified, sets
   `auth.user.two_factor_enabled = true`, and returns a set of single-use backup codes.
3. Calling setup again while 2FA is already enabled returns 409 Conflict.

### 4. 2FA disable flow

`POST /account/2fa/disable` (authenticated): accepts `{ totpCode }` (a valid TOTP code
from the current secret). On success, deletes the `auth.totp_secret` row, deletes any
remaining backup codes, sets `auth.user.two_factor_enabled = false`. Returns 204.

### 5. Backup / recovery codes

- 8 single-use recovery codes are generated when 2FA setup is verified.
- Stored hashed (bcrypt) in `auth.totp_backup_code`, each with a `consumed_at` column.
- A backup code can be used in place of a TOTP code at `POST /auth/login/2fa` and at
  `POST /account/2fa/disable`. The code is consumed (single-use) on successful use.
- Users can regenerate backup codes via `POST /account/2fa/backup-codes` (requires a
  valid TOTP code). This invalidates all prior backup codes and returns 8 new ones.

### 6. Password change with 2FA

The existing `ChangePasswordRequest` gains an optional `totpCode` field. When 2FA is
enabled, the backend requires a valid TOTP code (or backup code) in addition to the
current password. When 2FA is not enabled, the field is ignored. This avoids a separate
endpoint.

### 7. Account response: twoFactorEnabled flag

The existing `AccountResponse` schema gains a `twoFactorEnabled` boolean field
(default false). The UI uses this to show/hide the 2FA settings toggle and to know
whether to expect a two-step login. This is a non-breaking additive change.

### 8. Domain model (Clean Architecture layers)

**Layer 1 (domain/entity):**
- `TotpSecret`: id, userId, encryptedSecret, verified, verifiedAt, createdAt.
  Invariant: `isActive()` returns true only when `verified == true`.
- `TwoFactorChallenge`: id, userId, tokenHash, expiresAt, consumedAt, createdAt.
  Invariant: `isUsable(now)` returns true when unconsumed and unexpired.
- `BackupCode`: id, totpSecretId, codeHash, consumedAt, createdAt.
  Invariant: `isUsable()` returns true when `consumedAt == null`.
- New domain exception: `TwoFactorRequiredException` (carries the challenge token).
- New domain exception: `InvalidTotpCodeException`.
- New domain exception: `TwoFactorAlreadyEnabledException`.
- New domain exception: `TwoFactorNotEnabledException`.

**Layer 2 (application/port + usecase):**
- Port out: `TotpSecretRepository`, `TwoFactorChallengeRepository`,
  `BackupCodeRepository`, `TotpCodeVerifier` (interface for the TOTP library adapter),
  `SecretEncryptor` (interface for AES encryption of the TOTP secret at rest).
- Use cases: `SetupTwoFactorUseCase`, `VerifyTwoFactorSetupUseCase`,
  `DisableTwoFactorUseCase`, `VerifyLoginTwoFactorUseCase`,
  `RegenerateBackupCodesUseCase`.
- The existing `LoginUseCase` / `LoginService` is modified: when 2FA is enabled,
  instead of returning a `LoginResult`, it throws `TwoFactorRequiredException` which
  carries the challenge token. The `LoginResource` catches this via an
  `ExceptionMapper` and returns the 200 + `twoFactorRequired: true` response.
  Alternatively, `LoginResult` can be extended with an optional challenge field,
  avoiding the exception-as-flow-control pattern. We choose the latter: `LoginResult`
  gains an optional `twoFactorToken` field, and `LoginService` returns it when 2FA is
  required instead of the JWT token.
- The existing `ChangePasswordService` checks `user.isTwoFactorEnabled()` and, if true,
  validates the TOTP code from the command.

**Layer 3 (adapters):**
- `adapter/out/persistence/entity/TotpSecretEntity`, `TwoFactorChallengeEntity`,
  `BackupCodeEntity`.
- `adapter/out/persistence/TotpSecretJpaRepository`, etc.
- `adapter/out/security/TotpCodeVerifierAdapter` (wraps a Java TOTP library, e.g.
  `com.j256.two-factor-auth` or `dev.samstevens.totp`).
- `adapter/out/security/AesSecretEncryptor` (AES-256-GCM encryption for TOTP secrets).
- New REST endpoints on `AccountResource` (2fa/setup, 2fa/verify-setup, 2fa/disable,
  2fa/backup-codes) and on `LoginResource` (login/2fa).
- New ExceptionMappers for the new domain exceptions.

### 9. First-time 2FA recommendation notification

The notification-service will add a new `NotificationType` enum value:
`SECURITY_RECOMMENDATION`. After successful email verification (or after the first
login, depending on implementation), the auth-service or a cross-service scheduler
creates an in-app notification recommending 2FA setup. Since notification-service
already has the infrastructure for creating notifications, the simplest approach is:

- Auth-service does NOT call notification-service directly (no outbound client).
- Instead, notification-service's existing scheduled infrastructure (or a new
  lightweight scheduler) queries auth-service's internal endpoint for recently
  registered users who do not have 2FA enabled and have not already received the
  recommendation, then creates the notification.
- Alternatively, the notification can be created synchronously during the registration
  flow by having auth-service expose an internal endpoint that notification-service
  polls, or by notification-service checking on the user's first notification-center
  access. The simplest option for v1: notification-service runs a periodic check
  (e.g., every 15 minutes) for users registered in the last 24 hours with
  `two_factor_enabled = false` and no existing SECURITY_RECOMMENDATION notification
  for that user.

For v1, we choose the scheduler approach in notification-service, reusing its existing
`@Scheduled` infrastructure and auth-service internal endpoint pattern.

### 10. Migration numbers

- **024-auth-2fa-totp.sql**: `auth.totp_secret`, `auth.two_factor_challenge`,
  `auth.totp_backup_code` tables, plus `ALTER TABLE auth.user ADD COLUMN
  two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE`.
- **047-notification-security-recommendation-type.sql**: no DDL needed (the
  `notification.notifications.type` column is VARCHAR(50), not an enum type, so
  adding a new type value requires no migration). This migration number is reserved
  but the file may be a no-op comment documenting the new type value. If notification
  preferences gain a `securityRecommendation` toggle, the schema change goes here.

## Consequences

- **Positive:** users gain TOTP-based second factor, significantly reducing account
  takeover risk from compromised passwords. Backup codes prevent lockout. The two-step
  login is backward-compatible for non-2FA users.
- **Positive:** encrypted-at-rest TOTP secrets protect against database-level breaches.
- **Negative / cost:** three new tables in the auth schema, a new encryption key to
  manage (`TOTP_ENCRYPTION_KEY` env var), and additional complexity in the login flow.
- **Negative / cost:** the login response shape becomes polymorphic (either full login
  or 2FA challenge). The UI must handle both cases.
- **Follow-ups:** the TOTP encryption key must be provisioned via `.env` /
  `TOTP_ENCRYPTION_KEY` for compose deployments. Key rotation strategy is out of scope
  for v1 but should be addressed in a future ADR. Rate limiting on `POST /auth/login/2fa`
  (brute-force protection for 6-digit codes) should be implemented.

## Alternatives considered

- **TOTP columns on auth.user** (encrypted_secret, totp_verified_at added directly to
  the user table): rejected because it couples TOTP lifecycle to the user row, bloats
  the frequently-read user table, and makes cleanup on 2FA disable messier.

- **JWT-based 2FA challenge token** (sign a short-lived JWT with a `2fa-pending` claim
  instead of storing a challenge in the DB): rejected because the token must be
  single-use (consumed after successful 2FA verification), and JWTs cannot be revoked
  or marked consumed without server-side state anyway. A simple DB row is more honest
  about the statefulness.

- **Email-based 2FA** (reuse the existing VerificationCode mechanism with a new action
  type): rejected because TOTP is device-bound and time-based, fundamentally different
  from email codes. Conflating them would complicate the VerificationCode domain entity
  and confuse the user experience. Email-based 2FA is also weaker (email account
  compromise defeats it).

- **WebAuthn / passkeys**: considered as a future enhancement but rejected for v1 due
  to higher implementation complexity and browser compatibility requirements. Can be
  added later as a parallel second-factor option.

- **Auth-service pushes notification directly** (auth-service gains an outbound REST
  client to notification-service): rejected because auth-service currently has no
  outbound clients, and adding one creates a circular dependency risk. The
  notification-service scheduler approach reuses existing infrastructure.
