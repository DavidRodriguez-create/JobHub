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

export async function changePassword({ currentPassword, newPassword }) {
  await request("/auth/account/change-password", {
    method: "POST",
    auth: true,
    body: { currentPassword, newPassword },
  });
}

/* ── Email verification ── */

export async function verifyEmail(token) {
  await request("/auth/account/verify-email", { method: "POST", body: { token } });
}

export async function resendVerification(email) {
  await request("/auth/account/resend-verification", { method: "POST", body: { email } });
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
