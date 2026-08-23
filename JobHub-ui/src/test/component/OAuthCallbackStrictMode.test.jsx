/**
 * Story #522: a successful Google/GitHub sign-in must land the user in the app even
 * though App.jsx mounts under React.StrictMode (main.jsx), which double invokes
 * OAuthCallbackScreen's exchange effect in dev. Before the fix, run 1 completed the
 * exchange (and stored the token) but its onComplete was swallowed by the effect
 * cleanup's `cancelled` flag; run 2 replayed the same, now-consumed code+state and the
 * backend rejected it, so the "Sign-in didn't complete" error card rendered while the
 * user was actually logged in. Cases: TC-522-C1..C11 (docs/qa/522-oauth-callback-replay-test-cases.md).
 *
 * Mirrors the mocks/harness of ../component/OAuthCallback.test.jsx exactly, but wraps
 * <App/> in <React.StrictMode> to force the double effect-invoke that reproduces #522.
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
  getOAuthProviders: vi.fn(() => Promise.reject({ status: 0 })),
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
import { getToken, setToken, clearToken } from "../../api/client.js";
import App from "../../App.jsx";

function goToOAuthCallback(provider, params) {
  const qs = new URLSearchParams(params).toString();
  window.history.pushState({}, "", `/oauth/${provider}/callback${qs ? "?" + qs : ""}`);
}

function renderStrict() {
  return render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  getToken.mockReturnValue(null);
  window.history.pushState({}, "", "/");
});

// ── TC-522-C1: StrictMode double-invoke still exchanges the code exactly once ──────

describe("TC-522-C1: StrictMode double effect-invoke sends one exchange and still lands", () => {
  it("calls completeOAuthLogin exactly once and still completes login", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockImplementationOnce(() => {
      setToken("jwt-token");
      return Promise.resolve({
        token: "jwt-token", expiresIn: 3600,
        account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com" },
      });
    });

    renderStrict();

    await waitFor(() => {
      expect(screen.getByTestId("screen-search")).toBeInTheDocument();
    });

    expect(authApi.completeOAuthLogin).toHaveBeenCalledTimes(1);
    expect(authApi.completeOAuthLogin).toHaveBeenCalledWith({ provider: "google", code: "auth-code-1", state: "state-1" });
    expect(screen.queryByTestId("oauth-callback-error")).not.toBeInTheDocument();
    expect(screen.queryByTestId("oauth-callback-loading")).not.toBeInTheDocument();
    expect(setToken).toHaveBeenCalledTimes(1);
  });
});

// ── TC-522-C2: url is cleared and the app is fully landed, no error card ever shows ─

describe("TC-522-C2: the callback url is cleared and no error card ever appears", () => {
  it("lands on '/' and never shows the error card during the whole run", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockResolvedValueOnce({
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com" },
    });

    renderStrict();

    expect(screen.queryByTestId("oauth-callback-error")).not.toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByTestId("screen-search")).toBeInTheDocument();
    });

    expect(window.location.pathname).toBe("/");
    expect(screen.queryByTestId("oauth-callback-error")).not.toBeInTheDocument();
  });
});

// ── TC-522-C3: 2FA challenge under StrictMode ───────────────────────────────────────

describe("TC-522-C3: StrictMode double-invoke on a 2FA-challenge response", () => {
  it("exchanges once and still completes login after the TOTP step", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockResolvedValueOnce({
      token: null, expiresIn: null, account: null,
      twoFactorRequired: true, twoFactorToken: "challenge-abc",
    });

    renderStrict();

    await waitFor(() => {
      expect(screen.getByTestId("two-factor-login-step")).toBeInTheDocument();
    });
    expect(authApi.completeOAuthLogin).toHaveBeenCalledTimes(1);

    authApi.loginTwoFactor.mockResolvedValueOnce({
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com" },
    });
    await userEvent.type(screen.getByLabelText(/authentication code/i), "123456");
    await userEvent.click(screen.getByRole("button", { name: /verify/i }));

    await waitFor(() => {
      expect(authApi.loginTwoFactor).toHaveBeenCalledTimes(1);
    });
    await waitFor(() => {
      expect(screen.getByTestId("screen-search")).toBeInTheDocument();
    });
  });
});

// ── TC-522-C4: denied consent never calls the backend, even under StrictMode ───────

describe("TC-522-C4: denied consent under StrictMode never calls completeOAuthLogin", () => {
  it("shows 'Sign-in was cancelled' exactly once and never exchanges anything", async () => {
    goToOAuthCallback("google", { error: "access_denied" });

    renderStrict();

    await waitFor(() => {
      expect(screen.getAllByText(/sign-in was cancelled/i)).toHaveLength(1);
    });
    expect(authApi.completeOAuthLogin).not.toHaveBeenCalled();
  });
});

// ── TC-522-C5: 400 state-invalid rejection replayed once by StrictMode ─────────────

describe("TC-522-C5: StrictMode double-invoke on a 400 (stale/consumed state)", () => {
  it("exchanges once and shows the session-expired copy once, no partial session", async () => {
    goToOAuthCallback("github", { code: "auth-code-1", state: "stale-state" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({ status: 400, message: "Invalid or expired state." });

    renderStrict();

    await waitFor(() => {
      expect(screen.getAllByText(/session expired or is invalid/i)).toHaveLength(1);
    });
    expect(authApi.completeOAuthLogin).toHaveBeenCalledTimes(1);
    expect(setToken).not.toHaveBeenCalled();
    expect(clearToken).not.toHaveBeenCalled();
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();
    expect(screen.queryByTestId("screen-dashboard")).not.toBeInTheDocument();
  });
});

// ── TC-522-C6: 401 provider-authorization-failed replayed once by StrictMode ───────

describe("TC-522-C6: StrictMode double-invoke on a 401 provider-authorization-failed", () => {
  it("exchanges once and shows the generic sign-in-failed copy once", async () => {
    goToOAuthCallback("google", { code: "bad-code", state: "state-1" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({
      status: 401, body: { error: "Provider Authorization Failed" }, message: "Provider authorization failed.",
    });

    renderStrict();

    await waitFor(() => {
      expect(screen.getAllByText(/couldn't sign you in with google/i)).toHaveLength(1);
    });
    expect(authApi.completeOAuthLogin).toHaveBeenCalledTimes(1);
    expect(setToken).not.toHaveBeenCalled();
    expect(clearToken).not.toHaveBeenCalled();
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();
  });
});

// ── TC-522-C7: 401 account-linking-refused replayed once by StrictMode ─────────────

describe("TC-522-C7: StrictMode double-invoke on a 401 account-linking-refused", () => {
  it("exchanges once and shows the account-safety copy once, distinct from C6", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({
      status: 401, body: { error: "Account Linking Refused" }, message: "Unverified provider email collides with an existing account.",
    });

    renderStrict();

    await waitFor(() => {
      expect(screen.getAllByText(/sign in with your existing email and password/i)).toHaveLength(1);
    });
    expect(authApi.completeOAuthLogin).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(/couldn't sign you in with google\. please try again\./i)).not.toBeInTheDocument();
    expect(setToken).not.toHaveBeenCalled();
    expect(clearToken).not.toHaveBeenCalled();
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();
  });
});

// ── TC-522-C8: 502 provider outage replayed once by StrictMode ─────────────────────

describe("TC-522-C8: StrictMode double-invoke on a 502 provider outage", () => {
  it("exchanges once and shows the unavailable copy once", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({ status: 502, message: "Provider unavailable." });

    renderStrict();

    await waitFor(() => {
      expect(screen.getAllByText(/google is unavailable/i)).toHaveLength(1);
    });
    expect(authApi.completeOAuthLogin).toHaveBeenCalledTimes(1);
    expect(setToken).not.toHaveBeenCalled();
    expect(clearToken).not.toHaveBeenCalled();
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();
  });
});

// ── TC-522-C9: uncategorised 500 replayed once by StrictMode ───────────────────────

describe("TC-522-C9: StrictMode double-invoke on an uncategorised 500", () => {
  it("exchanges once and shows the generic 'something went wrong' copy once, distinct from C6/C7/C8", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockRejectedValueOnce({ status: 500 });

    renderStrict();

    await waitFor(() => {
      expect(screen.getAllByText(/something went wrong/i)).toHaveLength(1);
    });
    expect(authApi.completeOAuthLogin).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(/couldn't sign you in with google/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/sign in with your existing/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/google is unavailable/i)).not.toBeInTheDocument();
    expect(setToken).not.toHaveBeenCalled();
    expect(clearToken).not.toHaveBeenCalled();
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();
  });
});

// ── TC-522-C10 (edge): a genuinely new sign-in (different code+state) is not dedupe-blocked ─

describe("TC-522-C10: a second, genuinely new sign-in attempt is not swallowed by the first's dedupe entry", () => {
  it("exchanges the new code+state and lands normally", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    authApi.completeOAuthLogin.mockResolvedValueOnce({
      token: "jwt-token-1", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com" },
    });

    const first = renderStrict();
    await waitFor(() => {
      expect(screen.getByTestId("screen-search")).toBeInTheDocument();
    });
    expect(authApi.completeOAuthLogin).toHaveBeenCalledTimes(1);
    first.unmount();

    // A brand-new redirect round trip: different code+state (real or same provider).
    goToOAuthCallback("google", { code: "auth-code-2", state: "state-2" });
    authApi.completeOAuthLogin.mockResolvedValueOnce({
      token: "jwt-token-2", expiresIn: 3600,
      account: { id: "u2", firstName: "Ana", lastName: "Diaz", email: "ana@example.com" },
    });

    renderStrict();

    await waitFor(() => {
      expect(authApi.completeOAuthLogin).toHaveBeenCalledWith({ provider: "google", code: "auth-code-2", state: "state-2" });
    });
    await waitFor(() => {
      expect(screen.getByTestId("screen-search")).toBeInTheDocument();
    });
  });
});

// ── TC-522-C11 (edge): a further forced remount of the same still-consumed code+state
// does not replay it a 3rd time ─────────────────────────────────────────────────────

describe("TC-522-C11: a further forced remount of the same code+state does not replay it a 3rd time", () => {
  it("keeps completeOAuthLogin at exactly one call across a third mount pass", async () => {
    goToOAuthCallback("google", { code: "auth-code-1", state: "state-1" });
    // Left pending: the exchange never settles, so the callback url is never cleared and
    // the same code+state is still in place for the forced third mount below.
    authApi.completeOAuthLogin.mockImplementationOnce(() => new Promise(() => {}));

    const first = renderStrict();
    await waitFor(() => {
      expect(screen.getByTestId("oauth-callback-loading")).toBeInTheDocument();
    });
    expect(authApi.completeOAuthLogin).toHaveBeenCalledTimes(1);

    // Forced third render pass for the same {provider, code, state}: unmount and
    // immediately remount (no real time elapses, same synchronous window StrictMode's
    // own dev-only double-invoke already relies on) with the identical url still in place.
    first.unmount();
    renderStrict();

    await waitFor(() => {
      expect(screen.getByTestId("oauth-callback-loading")).toBeInTheDocument();
    });
    expect(authApi.completeOAuthLogin).toHaveBeenCalledTimes(1);
  });
});
