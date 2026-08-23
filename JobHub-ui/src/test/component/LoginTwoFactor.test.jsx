/**
 * App-level tests for the two-step (2FA) login flow.
 * Cases: TC-FE-LOGIN-01..05 (docs/specs/0133-test-cases.md, section 5.3)
 *
 * TC-FE-LOGIN-01: 2FA challenge shows a TOTP code input step.
 * TC-FE-LOGIN-02: a valid code completes login.
 * TC-FE-LOGIN-03: a wrong code shows an error and allows retry.
 * TC-FE-LOGIN-04: an expired token shows a "restart login" message.
 * TC-FE-LOGIN-05: non-2FA login shows no TOTP step (regression).
 *
 * Strategy mirrors AdminAccess.test.jsx / LoginUnverifiedRedirect.test.jsx: mock the
 * API modules and heavy screens so <App /> renders a minimal shell, keeping the real
 * Auth screens so the login form + 2FA step are exercised for real.
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
  getApplyProfile: vi.fn(() => Promise.resolve(null)),
  saveApplyProfile: vi.fn(),
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
import { getToken } from "../../api/client.js";
import App from "../../App.jsx";

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  sessionStorage.setItem("jobhub_route", "login");
  getToken.mockReturnValue(null);
});

async function submitLoginForm(email = "jo@example.com", password = "password123") {
  const emailInput = await screen.findByPlaceholderText("you@email.com");
  await userEvent.type(emailInput, email);
  await userEvent.type(screen.getByPlaceholderText("••••••••"), password);
  await userEvent.click(screen.getByRole("button", { name: /Sign in/i }));
}

// ── TC-FE-LOGIN-01: 2FA challenge shows the TOTP code input step ─────────────

describe("TC-FE-LOGIN-01: a 2FA challenge response shows a TOTP code input step", () => {
  it("renders a code-entry step instead of navigating into the app", async () => {
    authApi.login.mockResolvedValueOnce({
      token: null, expiresIn: null, account: null,
      twoFactorRequired: true, twoFactorToken: "challenge-abc",
    });

    render(<App />);
    await submitLoginForm();

    await waitFor(() => {
      expect(screen.getByTestId("two-factor-login-step")).toBeInTheDocument();
    });
    expect(screen.getByLabelText(/authentication code/i)).toBeInTheDocument();
    // Did not navigate into the app
    expect(screen.queryByTestId("screen-search")).not.toBeInTheDocument();
  });
});

// ── TC-FE-LOGIN-02: valid code completes login ────────────────────────────────

describe("TC-FE-LOGIN-02: a valid code completes the login", () => {
  it("calls loginTwoFactor and navigates into the app on success", async () => {
    authApi.login.mockResolvedValueOnce({
      token: null, expiresIn: null, account: null,
      twoFactorRequired: true, twoFactorToken: "challenge-abc",
    });
    authApi.loginTwoFactor.mockResolvedValueOnce({
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com", twoFactorEnabled: true },
    });

    render(<App />);
    await submitLoginForm();

    const codeInput = await screen.findByLabelText(/authentication code/i);
    await userEvent.type(codeInput, "123456");
    await userEvent.click(screen.getByRole("button", { name: /verify/i }));

    await waitFor(() => {
      expect(authApi.loginTwoFactor).toHaveBeenCalledWith({
        twoFactorToken: "challenge-abc",
        totpCode: "123456",
      });
    });

    await waitFor(() => {
      expect(screen.getByTestId("screen-search")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("two-factor-login-step")).not.toBeInTheDocument();
  });
});

// ── TC-FE-LOGIN-03: wrong code shows error, allows retry ─────────────────────

describe("TC-FE-LOGIN-03: a wrong code shows an error and allows retry", () => {
  it("shows an error and keeps the same challenge token for a retry", async () => {
    authApi.login.mockResolvedValueOnce({
      token: null, expiresIn: null, account: null,
      twoFactorRequired: true, twoFactorToken: "challenge-abc",
    });
    authApi.loginTwoFactor.mockRejectedValueOnce({ status: 401, message: "Invalid code." });

    render(<App />);
    await submitLoginForm();

    const codeInput = await screen.findByLabelText(/authentication code/i);
    await userEvent.type(codeInput, "000000");
    await userEvent.click(screen.getByRole("button", { name: /verify/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(/invalid code/i);
    });

    // Still on the 2FA step, can retry
    expect(screen.getByTestId("two-factor-login-step")).toBeInTheDocument();

    authApi.loginTwoFactor.mockResolvedValueOnce({
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com", twoFactorEnabled: true },
    });

    const retryInput = screen.getByLabelText(/authentication code/i);
    await userEvent.type(retryInput, "654321");
    await userEvent.click(screen.getByRole("button", { name: /verify/i }));

    await waitFor(() => {
      expect(authApi.loginTwoFactor).toHaveBeenLastCalledWith({
        twoFactorToken: "challenge-abc",
        totpCode: "654321",
      });
    });
  });
});

// ── TC-FE-LOGIN-04: expired token shows "restart login" message ──────────────

describe("TC-FE-LOGIN-04: an expired challenge token shows a restart-login message", () => {
  it("shows a message directing the user to sign in again on a 400 response", async () => {
    authApi.login.mockResolvedValueOnce({
      token: null, expiresIn: null, account: null,
      twoFactorRequired: true, twoFactorToken: "challenge-abc",
    });
    authApi.loginTwoFactor.mockRejectedValueOnce({ status: 400, message: "Challenge token expired or invalid." });

    render(<App />);
    await submitLoginForm();

    const codeInput = await screen.findByLabelText(/authentication code/i);
    await userEvent.type(codeInput, "123456");
    await userEvent.click(screen.getByRole("button", { name: /verify/i }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(/session expired|sign in again/i);
    });

    // A button/link to restart login is offered
    const restartBtn = screen.getByRole("button", { name: /sign in again|back to sign in|restart/i });
    await userEvent.click(restartBtn);

    await waitFor(() => {
      expect(screen.getByPlaceholderText("you@email.com")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("two-factor-login-step")).not.toBeInTheDocument();
  });
});

// ── TC-FE-LOGIN-05: non-2FA login, no TOTP step (regression) ─────────────────

describe("TC-FE-LOGIN-05: non-2FA login never shows a TOTP step (regression)", () => {
  it("logs in directly without presenting the 2FA step", async () => {
    authApi.login.mockResolvedValueOnce({
      token: "jwt-token", expiresIn: 3600,
      account: { id: "u1", firstName: "Jo", lastName: "Smith", email: "jo@example.com", twoFactorEnabled: false },
    });

    render(<App />);
    await submitLoginForm();

    await waitFor(() => {
      expect(screen.getByTestId("screen-search")).toBeInTheDocument();
    });
    expect(screen.queryByTestId("two-factor-login-step")).not.toBeInTheDocument();
    expect(authApi.loginTwoFactor).not.toHaveBeenCalled();
  });
});
