/**
 * App-level admin page visibility / access-gating tests.
 * Cases: UI-01, UI-02, UI-03 (Story #7 — admin trigger crawl & enrichment)
 *
 * Strategy:
 * - Mock api/auth.js (currentUser/login/etc.), api/client.js (getToken), api/jobs.js
 *   (including getAdminTriggerStatus/triggerAdminPass), api/applications.js, mockData.js,
 *   and the heavy screen modules so <App /> renders a minimal shell.
 * - Drive isAdmin via the resolved value of currentUser() (mirrors GET /auth/account).
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
  listAdminCompanies: vi.fn().mockResolvedValue({ items: [], total: 0 }),
  getAdminCompany: vi.fn(),
  updateAdminCompany: vi.fn(),
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
import { getAdminTriggerStatus, triggerAdminPass, listAdminCompanies } from "../../api/jobs.js";
import App from "../../App.jsx";

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  getAdminTriggerStatus.mockResolvedValue({
    triggerEnabled: true, codeRequired: false, crawl: null, enrichment: null,
  });
});

describe("UI-01 — admin nav entry and page are visible when isAdmin=true", () => {
  it("shows the Admin nav entry, and navigating to it renders the trigger status panel + fetches status", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: true, email: "admin@example.com", groups: ["user", "admin"] });

    render(<App />);

    // Wait for the booting/session-restore effect to settle and the admin nav to appear.
    const adminLink = await screen.findByText("Admin", { exact: true });
    expect(adminLink).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(adminLink);

    // Admin page renders the trigger status panel
    await screen.findByTestId("admin-page");
    await waitFor(() => {
      expect(screen.getByTestId("trigger-status-panel")).toBeInTheDocument();
    });

    // A request to GET /jobs/admin/triggers/status is made
    await waitFor(() => expect(getAdminTriggerStatus).toHaveBeenCalled());
  });
});

describe("Story #430 : Companies nav entry and page are visible when isAdmin=true", () => {
  it("shows the Companies nav entry alongside Admin, and navigating to it renders the browse screen + fetches the list", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: true, email: "admin@example.com", groups: ["user", "admin"] });

    render(<App />);

    await screen.findByText("Admin", { exact: true });
    const companiesLink = await screen.findByText("Companies", { exact: true });
    expect(companiesLink).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(companiesLink);

    await screen.findByTestId("admin-companies-page");
    await waitFor(() => expect(listAdminCompanies).toHaveBeenCalled());
  });
});

describe("Story #430 : Companies nav entry absent and route not rendered when isAdmin=false", () => {
  it("does not show a Companies nav entry and never calls the admin company endpoints", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });

    render(<App />);

    await screen.findByTestId("screen-search");

    expect(screen.queryByText("Companies", { exact: true })).not.toBeInTheDocument();
    expect(listAdminCompanies).not.toHaveBeenCalled();
  });
});

describe("UI-02 — admin nav entry absent and page not rendered when isAdmin=false", () => {
  it("does not show an Admin nav entry and never calls the admin endpoints", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });

    render(<App />);

    // Wait for the session-restore effect to settle (search screen renders).
    await screen.findByTestId("screen-search");

    // No "Admin" nav entry in the document
    expect(screen.queryByText("Admin", { exact: true })).not.toBeInTheDocument();

    // No request to the admin endpoints
    expect(getAdminTriggerStatus).not.toHaveBeenCalled();
    expect(triggerAdminPass).not.toHaveBeenCalled();
  });
});

describe("UI-03 — unauthenticated user cannot access the admin page", () => {
  it("renders a login screen instead of the admin page when navigating while logged out", async () => {
    getToken.mockReturnValue(null);

    render(<App />);

    // No session restore attempted (no token) — search screen renders for logged-out users.
    await screen.findByTestId("screen-search");

    expect(screen.queryByTestId("admin-page")).not.toBeInTheDocument();
    expect(getAdminTriggerStatus).not.toHaveBeenCalled();

    // Sidebar admin entry must not be present either (isAdmin is false when logged out)
    expect(screen.queryByText("Admin", { exact: true })).not.toBeInTheDocument();
  });

  it("a direct navigation to the admin route while logged out renders the login screen, not the admin page", async () => {
    getToken.mockReturnValue(null);
    // Simulate landing directly on the admin route (e.g. restored from a previous session/URL).
    sessionStorage.setItem("jobhub_route", "admin");

    render(<App />);

    // The protected-route gate redirects logged-out users to the login screen.
    await waitFor(() => {
      expect(screen.getByPlaceholderText("you@email.com")).toBeInTheDocument();
    });

    expect(screen.queryByTestId("admin-page")).not.toBeInTheDocument();
    expect(getAdminTriggerStatus).not.toHaveBeenCalled();
  });
});
