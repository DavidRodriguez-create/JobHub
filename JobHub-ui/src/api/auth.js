// auth-service (/auth) — registration, login, account management, verification.
// Mirrors api-contracts/openapi/auth-service.yaml.
import { request, setToken, clearToken } from "./client.js";

export async function register({ firstName, lastName, email, password }) {
  const { data } = await request("/auth/register", {
    method: "POST",
    body: { firstName, lastName, email, password },
  });
  return data; // AccountResponse
}

export async function login({ email, password }) {
  const { data } = await request("/auth/login", {
    method: "POST",
    body: { email, password },
  });
  // LoginResponse: { token, expiresIn, account }
  if (data && data.token) setToken(data.token);
  return data;
}

export function logout() {
  // JWT is stateless — there is no server logout; just drop the token.
  clearToken();
}

export async function currentUser() {
  const { data } = await request("/auth/account", { auth: true });
  return data; // AccountResponse
}

// Profile update — only firstName/lastName are editable here.
// Password changes go through changePassword (current password required).
export async function updateCurrentUser({ firstName, lastName }) {
  const body = {};
  if (firstName !== undefined) body.firstName = firstName;
  if (lastName !== undefined) body.lastName = lastName;
  const { data } = await request("/auth/account", { method: "PATCH", auth: true, body });
  return data; // AccountResponse
}

export async function changePassword({ currentPassword, newPassword, totpCode }) {
  const body = { currentPassword, newPassword };
  if (totpCode) body.totpCode = totpCode;
  await request("/auth/account/change-password", {
    method: "POST",
    auth: true,
    body,
  });
}

/* ── Two-factor authentication (ADR 0012) ── */

export async function setupTwoFactor() {
  const { data } = await request("/auth/account/2fa/setup", {
    method: "POST",
    auth: true,
  });
  return data; // { otpauthUri, setupKey }
}

export async function verifyTwoFactorSetup({ totpCode }) {
  const { data } = await request("/auth/account/2fa/verify-setup", {
    method: "POST",
    auth: true,
    body: { totpCode },
  });
  return data; // { backupCodes: string[] }
}

export async function disableTwoFactor({ totpCode }) {
  await request("/auth/account/2fa/disable", {
    method: "POST",
    auth: true,
    body: { totpCode },
  });
}

export async function loginTwoFactor({ twoFactorToken, totpCode }) {
  const { data } = await request("/auth/login/2fa", {
    method: "POST",
    body: { twoFactorToken, totpCode },
  });
  if (data && data.token) setToken(data.token);
  return data;
}

/* ── Email verification ── */

// Sends the 6-digit code issued to the user's email address.
// Contract: POST /auth/account/verify-email { email, code }
export async function verifyEmail({ email, code }) {
  await request("/auth/account/verify-email", { method: "POST", body: { email, code } });
}

export async function resendVerification(email) {
  await request("/auth/account/resend-verification", { method: "POST", body: { email } });
}

/* ── Apply profile answer bank (ADR 0022, story #336) ── */

// GET always returns 200, all-null when never saved. Never 404.
export async function getApplyProfile() {
  const { data } = await request("/auth/account/apply-profile", { auth: true });
  return data; // ApplyProfileResponse
}

// PUT is a full-replace upsert: omitted/null fields clear that answer.
export async function saveApplyProfile(body) {
  const { data } = await request("/auth/account/apply-profile", {
    method: "PUT",
    auth: true,
    body,
  });
  return data; // ApplyProfileResponse
}

/* ── Social login (OAuth, ADR 0027, story #459) ── */

// GET /oauth/{provider}/start — returns { authorizationUrl }, the provider's consent-screen
// URL the UI redirects the browser to. provider: "google" | "github".
export async function startOAuth(provider) {
  const { data } = await request(`/auth/oauth/${provider}/start`);
  return data; // OAuthAuthorizationResponse { authorizationUrl }
}

// POST /oauth/{provider}/callback — relays the code+state the provider returned to the UI's
// callback route. Returns the SAME LoginResponse shape as login()/loginTwoFactor() (may be a
// 2FA challenge: twoFactorRequired + twoFactorToken, token/account/expiresIn null).
export async function completeOAuthLogin({ provider, code, state }) {
  const { data } = await request(`/auth/oauth/${provider}/callback`, {
    method: "POST",
    body: { code, state },
  });
  if (data && data.token) setToken(data.token);
  return data;
}

/* ── OAuth provider availability (ADR 0028, story #506) ── */

// GET /oauth/providers — unauthenticated: it gates the login/signup screens, called
// before any token exists. Reports, per provider, whether this deployment holds usable
// credentials for it (configuration only, never a live health probe — ADR 0028 Decision
// 2). A provider reported unavailable must have its button hidden, never disabled.
export async function getOAuthProviders() {
  const { data } = await request("/auth/oauth/providers");
  return data; // OAuthProvidersResponse { providers: [{ provider, available }] }
}

/* ── Destructive-action verification (two-factor) ── */

// action: "delete-account" | "delete-all-applications". Returns { verificationId, expiresAt }.
export async function requestVerification(action) {
  const { data } = await request("/auth/account/verifications", {
    method: "POST",
    auth: true,
    body: { action },
  });
  return data;
}

// Permanently delete the account after a delete-account code is obtained above.
export async function deleteAccount({ verificationId, code }) {
  await request("/auth/account", {
    method: "DELETE",
    auth: true,
    body: { verificationId, code },
  });
  clearToken();
}
