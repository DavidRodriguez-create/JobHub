/**
 * Unit tests for src/api/auth.js — email verification methods.
 * Cases: EV-FE-08..10
 *
 * EV-FE-08: verifyEmail({email, code}) sends POST /auth/account/verify-email
 *           with body { email, code } — NOT { token }.
 * EV-FE-09: resendVerification(email) sends POST /auth/account/resend-verification
 *           with body { email }.
 * EV-FE-10: register(...) returns the full response object including
 *           verificationRequired when the backend includes it.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("../../api/client.js", () => ({
  request: vi.fn(),
  setToken: vi.fn(),
  clearToken: vi.fn(),
  getToken: vi.fn(() => null),
}));

const { request } = await import("../../api/client.js");
const { verifyEmail, resendVerification, register, startOAuth, completeOAuthLogin } = await import("../../api/auth.js");

beforeEach(() => {
  request.mockResolvedValue({ data: null, total: null, status: 204 });
});

afterEach(() => {
  vi.clearAllMocks();
});

// ── EV-FE-08: verifyEmail sends {email, code} ─────────────────────────────────

describe("EV-FE-08: verifyEmail sends {email, code} to POST /auth/account/verify-email", () => {
  it("posts to /auth/account/verify-email with body {email, code}", async () => {
    await verifyEmail({ email: "user@example.com", code: "123456" });

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe("/auth/account/verify-email");
    expect(opts.method).toBe("POST");
    expect(opts.body).toEqual({ email: "user@example.com", code: "123456" });
  });

  it("does NOT send a 'token' field", async () => {
    await verifyEmail({ email: "user@example.com", code: "654321" });

    const [, opts] = request.mock.calls[0];
    expect(opts.body).not.toHaveProperty("token");
  });
});

// ── EV-FE-09: resendVerification sends {email} ────────────────────────────────

describe("EV-FE-09: resendVerification sends {email} to POST /auth/account/resend-verification", () => {
  it("posts to /auth/account/resend-verification with body {email}", async () => {
    await resendVerification("user@example.com");

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe("/auth/account/resend-verification");
    expect(opts.method).toBe("POST");
    expect(opts.body).toEqual({ email: "user@example.com" });
  });
});

// ── EV-FE-10: register returns full response including verificationRequired ────

describe("EV-FE-10: register returns the full response object including verificationRequired", () => {
  it("returns the data object from the response which may include verificationRequired", async () => {
    const mockResponse = {
      id: "uuid-1",
      firstName: "Jo",
      lastName: "Smith",
      email: "jo@example.com",
      emailVerified: false,
      verificationRequired: true,
    };
    request.mockResolvedValueOnce({ data: mockResponse, total: null, status: 201 });

    const result = await register({
      firstName: "Jo",
      lastName: "Smith",
      email: "jo@example.com",
      password: "password123",
    });

    expect(result).toEqual(mockResponse);
    expect(result.verificationRequired).toBe(true);
  });

  it("handles a response without verificationRequired (backward-compatible)", async () => {
    const mockResponse = {
      id: "uuid-2",
      firstName: "Jane",
      lastName: "Doe",
      email: "jane@example.com",
      emailVerified: false,
    };
    request.mockResolvedValueOnce({ data: mockResponse, total: null, status: 201 });

    const result = await register({
      firstName: "Jane",
      lastName: "Doe",
      email: "jane@example.com",
      password: "password123",
    });

    expect(result).toEqual(mockResponse);
    expect(result.verificationRequired).toBeUndefined();
  });
});

// ── Story #459 (ADR 0027): social login (OAuth) contract alignment ─────────────

describe("startOAuth(provider) sends GET /auth/oauth/{provider}/start", () => {
  it("returns the OAuthAuthorizationResponse { authorizationUrl }", async () => {
    const mockResponse = { authorizationUrl: "https://accounts.google.com/o/oauth2/v2/auth?client_id=abc&state=xyz" };
    request.mockResolvedValueOnce({ data: mockResponse, total: null, status: 200 });

    const result = await startOAuth("google");

    expect(request).toHaveBeenCalledWith("/auth/oauth/google/start");
    expect(result).toEqual(mockResponse);
  });
});

describe("completeOAuthLogin({provider, code, state}) sends POST /auth/oauth/{provider}/callback", () => {
  it("posts {code, state} in the body and returns the LoginResponse", async () => {
    const mockResponse = {
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com", emailVerified: true },
    };
    request.mockResolvedValueOnce({ data: mockResponse, total: null, status: 200 });

    const result = await completeOAuthLogin({ provider: "github", code: "auth-code-1", state: "state-1" });

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe("/auth/oauth/github/callback");
    expect(opts.method).toBe("POST");
    expect(opts.body).toEqual({ code: "auth-code-1", state: "state-1" });
    expect(result).toEqual(mockResponse);
  });

  it("calls setToken when the response resolves a completed login (non-2FA)", async () => {
    const mockResponse = {
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com", emailVerified: true },
    };
    request.mockResolvedValueOnce({ data: mockResponse, total: null, status: 200 });

    await completeOAuthLogin({ provider: "google", code: "auth-code-1", state: "state-1" });

    const { setToken } = await import("../../api/client.js");
    expect(setToken).toHaveBeenCalledWith("jwt-token");
  });

  it("does NOT call setToken on a 2FA-challenge response (token is null)", async () => {
    const mockResponse = {
      token: null, expiresIn: null, account: null,
      twoFactorRequired: true, twoFactorToken: "challenge-abc",
    };
    request.mockResolvedValueOnce({ data: mockResponse, total: null, status: 200 });

    const { setToken } = await import("../../api/client.js");
    setToken.mockClear();

    await completeOAuthLogin({ provider: "google", code: "auth-code-1", state: "state-1" });

    expect(setToken).not.toHaveBeenCalled();
  });
});
