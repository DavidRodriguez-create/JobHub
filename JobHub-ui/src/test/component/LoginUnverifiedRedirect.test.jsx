/**
 * App-level test: logging in with an account whose email was never verified
 * (e.g. the user closed the verify-code screen after registering) should send
 * a fresh verification code and route to the verify-email screen, instead of
 * just showing an inline login error.
 *
 * Strategy mirrors AdminAccess.test.jsx: mock the API modules and heavy screens
 * so <App /> renders a minimal shell, but keep the real Auth screens.
 */
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/config.js", () => ({ USE_API: true }));

vi.mock("../../api/auth.js", () => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  currentUser: vi.fn(),
  verifyEmail: vi.fn(),
  resendVerification: vi.fn(),
  requestVerification: vi.fn(),
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
});

describe("Login with an unverified account redirects to the verify-email screen", () => {
  it("sends a fresh code and shows the verify screen when login() rejects with 403", async () => {
    getToken.mockReturnValue(null);
    authApi.login.mockRejectedValueOnce({ status: 403, message: "Email not verified." });
    authApi.resendVerification.mockResolvedValueOnce(undefined);

    sessionStorage.setItem("jobhub_route", "login");
    render(<App />);

    // Logged out -> login screen
    const emailInput = await screen.findByPlaceholderText("you@email.com");
    await userEvent.type(emailInput, "jo@example.com");
    await userEvent.type(screen.getByPlaceholderText("••••••••"), "password123");
    await userEvent.click(screen.getByRole("button", { name: /Sign in/i }));

    // A fresh verification code is requested for this email
    await waitFor(() => {
      expect(authApi.resendVerification).toHaveBeenCalledWith("jo@example.com");
    });

    // The user lands on the verify-email screen, pre-filled with their email
    await waitFor(() => {
      expect(screen.getByTestId("verify-email-screen")).toBeInTheDocument();
    });
    expect(screen.getByText("jo@example.com")).toBeInTheDocument();
  });
});
