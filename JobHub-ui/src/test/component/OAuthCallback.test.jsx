/**
 * Story #459 (ADR 0027): the OAuth callback screen, reached when the browser lands back
 * on the UI's /oauth/{provider}/callback route after the provider's consent screen.
 * Cases: TC-459-D7..D17 (docs/qa/459-social-login-test-cases.md, section D.2).
 *
 * Strategy mirrors LoginTwoFactor.test.jsx: mock the API modules and heavy screens so
 * <App /> renders a minimal shell, keeping the real Auth screens (including the new
 * OAuthCallbackScreen) so the callback flow is exercised for real. The provider redirect
 * is simulated with window.history.pushState (App.jsx reads window.location, it never
 * navigates for real), matching how a full-page round trip would leave the browser.
 *
 * TC-506-D27 (ROLLBACK-2, docs/qa/506-oauth-provider-availability-test-cases.md): on
 * every failed-callback path (D10..D13), explicitly assert no partial session is ever
 * established — no token-storage call (setToken/clearToken) and no flash of an
 * authenticated screen (screen-search/screen-dashboard) alongside the error copy. This
 * proves the reporter's original symptom (looking "signed in" after a failed Google
 * login, then unable to sign in properly) cannot recur.
 */
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/config.js", () => ({ USE_API: true }));

vi.mock("../../api/auth.js", () => ({
  login: vi.fn(),
  loginTwoFactor: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  currentUser: vi.fn(),
  verifyEmail: vi.fn(),
  resendVerification: vi.fn(),
  requestVerification: vi.fn(),
  startOAuth: vi.fn(),
  completeOAuthLogin: vi.fn(),
  // Availability isn't exercised on the callback route itself; other tests in this
  // file that fall through to the Login screen (e.g. TC-459-D9's "Back to sign in")
  // rely on fail-open once this rejects with an unmocked/no-op default.
  getOAuthProviders: vi.fn(() => Promise.reject({ status: 0 })),
  // Pre-existing gap this story's TC-506-D28 (a token-already-present boot) surfaces:
  // App.jsx's post-sign-in warm-up (prefetchApplyProfile -> getApplyProfile) needs a
  // resolvable mock here too, or the missing export throws synchronously and is
  // mistaken by App.jsx's boot effect for an invalid-session failure (a spurious
  // clearToken()), unrelated to the OAuth callback behaviour under test.
  getApplyProfile: vi.fn().mockResolvedValue(null),
}));

vi.mock("../../api/client.js", () => ({
  getToken: vi.fn(() => null),
  setToken: vi.fn(),
  clearToken: vi.fn(),
  ApiError: class ApiError extends Error {
    constructor(status, message) {
      super(message);
      this.status = status;
    }
  },
}));

vi.mock("../../api/jobs.js", () => ({
  searchJobs: vi.fn().mockResolvedValue({ items: [], total: 0, page: 0, totalPages: 0 }),
  listSavedJobs: vi.fn().mockResolvedValue({ items: [], total: 0 }),
  saveJob: vi.fn(),
  unsaveJob: vi.fn(),
  getJobFacets: vi.fn().mockResolvedValue({
    companies: [], locations: [], languages: [], employmentTypes: [],
    careerLevels: [], compensationMin: 0, compensationMax: 300000,
  }),
  listSavedFilters: vi.fn().mockResolvedValue([]),
  getAdminTriggerStatus: vi.fn().mockResolvedValue({
    triggerEnabled: true, codeRequired: false, crawl: null, enrichment: null,
  }),
  triggerAdminPass: vi.fn(),
}));

vi.mock("../../api/applications.js", () => ({
  listApplications: vi.fn().mockResolvedValue({ items: [], total: 0 }),
  applicationStats: vi.fn().mockResolvedValue(null),
  createApplication: vi.fn().mockResolvedValue({ id: "app-1" }),
}));

vi.mock("../../data/mockData.js", () => ({
  default: {
    companies: {},
    jobs: [],
    applications: [],
    saved: [],
    byId: () => undefined,
    coOf: () => ({ name: "Acme", industry: "—", size: "—", hq: "—", url: "" }),
    appForJob: () => undefined,
    nextAppId: () => "APP-001",
  },
}));

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

vi.mock("../../components/CommandPalette.jsx", () => ({
  CommandPalette: () => null,
}));

vi.mock("../../components/AddApplication.jsx", () => ({
  AddApplicationModal: () => null,
}));

vi.mock("../../screens/JobSearch.jsx", () => ({
  JobSearchScreen: () => <div data-testid="screen-search">Search</div>,
  JobDetailDrawer: () => null,
}));

vi.mock("../../screens/Applications.jsx", () => ({
  ApplicationsScreen: () => <div data-testid="screen-applications">Applications</div>,
  ApplicationDetailScreen: () => null,
}));

vi.mock("../../screens/Dashboard.jsx", () => ({
  DashboardScreen: () => <div data-testid="screen-dashboard">Dashboard</div>,
}));

vi.mock("../../screens/SavedSettings.jsx", () => ({
  SavedScreen: () => <div data-testid="screen-saved">Saved</div>,
  SettingsScreen: () => <div data-testid="screen-settings">Settings</div>,
}));

import * as authApi from "../../api/auth.js";
import * as applicationsApi from "../../api/applications.js";
import { getToken, setToken, clearToken } from "../../api/client.js";
import App from "../../App.jsx";

const OAUTH_CONTEXT_KEY = "jobhub_oauth_context";

function goToOAuthCallback(provider, params) {
  const qs = new URLSearchParams(params).toString();
  window.history.pushState({}, "", `/oauth/${provider}/callback${qs ? "?" + qs : ""}`);
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  getToken.mockReturnValue(null);
  window.history.pushState({}, "", "/");
});

// ── TC-459-D7: happy path completes login exactly like password login ──────────

describe.each(["google", "github"])("TC-459-D7: %s callback happy path completes login", (provider) => {
  it("relays code+state exactly once and lands on the app", async () => {
    goToOAuthCallback(provider, { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockResolvedValueOnce({
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com" },
    });

    render(<App />);

    await waitFor(() => {
      expect(authApi.completeOAuthLogin).toHaveBeenCalledTimes(1);
    });
    expect(authApi.completeOAuthLogin).toHaveBeenCalledWith({ provider, code: "auth-code-1", state: "state-1" });

    await waitFor(() => {
      expect(screen.getByTestId("screen-search")).toBeInTheDocument();
    });
  });
});

// ── TC-459-D8: 2FA challenge reuses the existing TwoFactorLoginStep ─────────────

describe("TC-459-D8: a 2FA-challenge response shows the same TwoFactorLoginStep", () => {
  it("renders the TOTP code-entry step, not a completed login", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockResolvedValueOnce({
      token: null, expiresIn: null, account: null,
      twoFactorRequired: true, twoFactorToken: "challenge-abc",
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId("two-factor-login-step")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();

    // TC-459-D8/OAUTH-2FA-3: submitting the TOTP code completes login exactly like the
    // password-login 2FA second step.
    authApi.loginTwoFactor.mockResolvedValueOnce({
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com" },
    });
    const codeInput = screen.getByLabelText(/authentication code/i);
    await userEvent.type(codeInput, "123456");
    await userEvent.click(screen.getByRole("button", { name: /verify/i }));

    await waitFor(() => {
      expect(authApi.loginTwoFactor).toHaveBeenCalledWith({ twoFactorToken: "challenge-abc", totpCode: "123456" });
    });
    await waitFor(() => {
      expect(screen.getByTestId("screen-search")).toBeInTheDocument();
    });
  });
});

// ── TC-459-D9: denied consent never calls completeOAuthLogin ────────────────────

describe("TC-459-D9: denied consent shows a cancelled message and never calls the backend", () => {
  it("shows 'Sign-in was cancelled' and routes back to the Login screen", async () => {
    goToOAuthCallback("google", { error: "access_denied" });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/sign-in was cancelled/i)).toBeInTheDocument();
    });
    expect(authApi.completeOAuthLogin).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: /back to sign in/i }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText("you@email.com")).toBeInTheDocument();
    });
  });
});

// ── TC-459-D10: invalid/expired code (generic 401) ──────────────────────────────
// Also covers TC-506-D27 (ROLLBACK-2): a failed callback must never invoke onComplete,
// never touch token storage, and never flash an authenticated screen — the frontend half
// of proving no partial session survives a failed OAuth callback.

describe("TC-459-D10: an invalid/expired code shows a generic sign-in-failed message", () => {
  it("shows a message naming the provider, distinct from the other error copy", async () => {
    goToOAuthCallback("google", { code: "bad-code", state: "state-1" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({
      status: 401, body: { error: "Provider Authorization Failed" }, message: "Provider authorization failed.",
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/couldn't sign you in with google/i)).toBeInTheDocument();
    });
    expect(screen.queryByText(/session expired or is invalid/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/sign in with your existing/i)).not.toBeInTheDocument();

    // TC-506-D27 (ROLLBACK-2): no partial session is ever established on this path.
    expect(setToken).not.toHaveBeenCalled();
    expect(clearToken).not.toHaveBeenCalled();
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();
    expect(screen.queryByTestId("screen-dashboard")).not.toBeInTheDocument();
  });
});

// ── TC-459-D11: state mismatch / expired session (400) ──────────────────────────

describe("TC-459-D11: a 400 (state mismatch/expired) shows a session-expired message", () => {
  it("shows the session-expired copy and restarts at the Login screen", async () => {
    goToOAuthCallback("github", { code: "auth-code-1", state: "stale-state" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({ status: 400, message: "Invalid or expired state." });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/session expired or is invalid/i)).toBeInTheDocument();
    });

    // TC-506-D27 (ROLLBACK-2): no partial session is ever established on this path.
    expect(setToken).not.toHaveBeenCalled();
    expect(clearToken).not.toHaveBeenCalled();
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();
    expect(screen.queryByTestId("screen-dashboard")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /back to sign in/i }));
    await waitFor(() => {
      expect(screen.getByPlaceholderText("you@email.com")).toBeInTheDocument();
    });
  });
});

// ── TC-459-D12: provider outage (502) ────────────────────────────────────────────

describe("TC-459-D12: a 502 provider outage shows a distinct unavailable message", () => {
  it("names the provider and suggests email/password, distinct from D10/D13", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({ status: 502, message: "Provider unavailable." });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/google is unavailable/i)).toBeInTheDocument();
    });
    expect(screen.queryByText(/couldn't sign you in with google/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/sign in with your existing/i)).not.toBeInTheDocument();

    // TC-506-D27 (ROLLBACK-2): no partial session is ever established on this path.
    expect(setToken).not.toHaveBeenCalled();
    expect(clearToken).not.toHaveBeenCalled();
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();
    expect(screen.queryByTestId("screen-dashboard")).not.toBeInTheDocument();
  });
});

// ── TC-459-D13: account-linking refusal (401, distinct title) ──────────────────

describe("TC-459-D13: a linking-refused 401 shows an account-safety message", () => {
  it("directs the user to sign in with their existing method, distinct from D10's generic 401", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({
      status: 401, body: { error: "Account Linking Refused" }, message: "Unverified provider email collides with an existing account.",
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/sign in with your existing email and password/i)).toBeInTheDocument();
    });
    expect(screen.queryByText(/couldn't sign you in with google\. please try again\./i)).not.toBeInTheDocument();

    // TC-506-D27 (ROLLBACK-2): no partial session is ever established on this path.
    expect(setToken).not.toHaveBeenCalled();
    expect(clearToken).not.toHaveBeenCalled();
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();
    expect(screen.queryByTestId("screen-dashboard")).not.toBeInTheDocument();
  });
});

// ── TC-459-D14: default destination (OAUTH-UI-3) ────────────────────────────────

describe("TC-459-D14: no pending context lands on the default destination", () => {
  it("routes to the job search screen, same as password login", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockResolvedValueOnce({
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com" },
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId("screen-search")).toBeInTheDocument();
    });
  });
});

// ── TC-459-D15: apply-job context survives the redirect (OAUTH-UI-CTX-1) ───────

describe("TC-459-D15: pending apply-job context survives the redirect", () => {
  it("resumes the apply flow for the same job after a successful login", async () => {
    sessionStorage.setItem(OAUTH_CONTEXT_KEY, JSON.stringify({
      loginModalJob: { id: "job-42", co: "acme", title: "Senior Engineer" },
      authPromptReason: null,
    }));
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockResolvedValueOnce({
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com" },
    });

    render(<App />);

    await waitFor(() => {
      expect(applicationsApi.createApplication).toHaveBeenCalledWith({ jobPostId: "job-42" });
    }, { timeout: 2000 });
  });
});

// ── TC-459-D16: generic auth-prompt reason survives the redirect (OAUTH-UI-CTX-2) ─

describe("TC-459-D16: a pending auth-prompt reason survives the redirect", () => {
  it("routes to the original destination instead of the default", async () => {
    sessionStorage.setItem(OAUTH_CONTEXT_KEY, JSON.stringify({
      loginModalJob: null,
      authPromptReason: "saved",
    }));
    goToOAuthCallback("github", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockResolvedValueOnce({
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com" },
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId("screen-saved")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();
  });
});

// ── TC-459-D17: no persisted context falls back to the default (regression guard) ─

describe("TC-459-D17: nothing persisted before the redirect falls back to the default", () => {
  it("does not invent a stale destination when no context was ever saved", async () => {
    // No sessionStorage.setItem(OAUTH_CONTEXT_KEY, ...) call at all.
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockResolvedValueOnce({
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com" },
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId("screen-search")).toBeInTheDocument();
    });
    expect(applicationsApi.createApplication).not.toHaveBeenCalled();
  });
});

// ── TC-506-D29 (GH-EXCHANGE-1, regression): GitHub's 401 resolves through the same
// classifyOAuthError path 459 already built (backend previously answered 502 here) ──

describe("TC-506-D29: a GitHub 401 (provider-authorization-failed family) shows the generic sign-in-failed message", () => {
  it("shows the GitHub-specific copy, not the account-linking-refused or provider-outage copy", async () => {
    goToOAuthCallback("github", { code: "bad-code", state: "state-1" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({
      status: 401, body: { error: "Provider Authorization Failed" }, message: "Provider authorization failed.",
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/couldn't sign you in with github/i)).toBeInTheDocument();
    });
    expect(screen.queryByText(/sign in with your existing/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/github is unavailable/i)).not.toBeInTheDocument();
  });
});

// ── TC-506-D25 (LOGO-5): the callback screen's logo is explicitly not wired ────────

describe("TC-506-D25: the OAuth callback screen's logo performs no navigation", () => {
  it("has a logo image present but not inside any clickable/navigable element, on both the loading and error states", async () => {
    // Loading state: completeOAuthLogin left pending so the screen stays on "Signing you in…".
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockImplementationOnce(() => new Promise(() => {}));

    const { unmount, container } = render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId("oauth-callback-loading")).toBeInTheDocument();
    });
    expect(screen.queryByRole("button", { name: /jobhub home/i })).not.toBeInTheDocument();
    // The logo <img> is present (decorative alt="", so it isn't exposed via role="img")
    // but not wrapped in any button/link — clicking it can't navigate anywhere.
    const loadingLogo = container.querySelector("img");
    expect(loadingLogo).toBeTruthy();
    expect(loadingLogo.closest("button, a")).toBeNull();
    unmount();

    // Error state: same assertion, plus "Back to sign in" remains the only real exit.
    goToOAuthCallback("google", { code: "bad-code", state: "state-1" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({ status: 400, message: "Invalid or expired state." });

    const { container: errorContainer } = render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId("oauth-callback-error")).toBeInTheDocument();
    });
    expect(screen.queryByRole("button", { name: /jobhub home/i })).not.toBeInTheDocument();
    const errorLogo = errorContainer.querySelector("img");
    expect(errorLogo).toBeTruthy();
    expect(errorLogo.closest("button, a")).toBeNull();
    expect(screen.getByRole("button", { name: /back to sign in/i })).toBeInTheDocument();
  });
});

// ── TC-506-D28 (ROLLBACK-3): a failed callback never touches a pre-existing session ─

describe("TC-506-D28: a failed callback leaves a pre-existing token untouched", () => {
  it("keeps the same token value before and after the failed attempt", async () => {
    const EXISTING_TOKEN = "pre-existing-session-token";
    getToken.mockReturnValue(EXISTING_TOKEN);

    goToOAuthCallback("google", { code: "bad-code", state: "state-1" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({ status: 401, message: "Provider authorization failed." });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId("oauth-callback-error")).toBeInTheDocument();
    });

    const { setToken, clearToken } = await import("../../api/client.js");
    expect(setToken).not.toHaveBeenCalled();
    expect(clearToken).not.toHaveBeenCalled();
    expect(getToken()).toBe(EXISTING_TOKEN);
  });
});
