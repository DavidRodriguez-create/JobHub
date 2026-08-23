/**
 * App-level navigation tests for the command palette's settings/navigation index.
 * Story #304 (sub-issue #308). Strategy B cases from
 * JobHub-ui/docs/testing/304-settings-command-palette-cases.md.
 *
 * TC-304-13    AC-6  selecting "Notification preferences" lands on the notifications section
 * TC-304-14,15 AC-7  selecting Change password / Two-factor auth lands on the account section
 * TC-304-16    AC-8  selecting "Admin panel" (as admin) navigates to the Admin page
 * TC-304-19    AC-9  non-admin typing "trigger"/"crawl" never sees Admin panel, no admin nav
 * TC-304-26    AC-12 re-opening the palette from Settings after navigating still shows the index
 * TC-304-30    AC-14 selecting an entry while on a different section switches immediately
 * TC-304-32    edge  palette opened via the global ⌘K shortcut from Settings still shows the index
 *
 * Mirrors AdminAccess.test.jsx's mocking approach: api/*, data/mockData.js, and Icon.jsx are
 * mocked; SavedSettings.jsx and CommandPalette.jsx are the REAL modules (this is what we're
 * proving), as are the other screens (mocked to a minimal stub) so <App /> renders end-to-end.
 */
import React from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
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
  changePassword: vi.fn(),
  setupTwoFactor: vi.fn(),
  verifyTwoFactorSetup: vi.fn(),
  disableTwoFactor: vi.fn(),
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

vi.mock("../../api/notifications.js", () => ({
  getNotificationPreferences: vi.fn().mockResolvedValue({
    weeklyDigestEmail: true, interviewReminders: true, ghostedAlert: true, interviewReminderEmail: true,
  }),
  updateNotificationPreferences: vi.fn(),
  getUnreadCount: vi.fn().mockResolvedValue({ count: 0 }),
}));

vi.mock("../../data/mockData.js", () => ({
  default: {
    companies: {},
    jobs: [],
    applications: [],
    saved: [],
    byId: () => undefined,
    coOf: () => ({ name: "Acme", industry: "-", size: "-", hq: "-", url: "" }),
    appForJob: () => undefined,
    nextAppId: () => "APP-001",
  },
}));

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
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

// SavedSettings.jsx (SettingsScreen) and CommandPalette.jsx are NOT mocked: these tests
// prove the real deep-link/navigation wiring between them via <App />.

import * as authApi from "../../api/auth.js";
import { getToken } from "../../api/client.js";
import { getAdminTriggerStatus, triggerAdminPass } from "../../api/jobs.js";
import { getUnreadCount } from "../../api/notifications.js";
import App from "../../App.jsx";

async function bootAsAuthedUser(account) {
  getToken.mockReturnValue("a-valid-jwt");
  authApi.currentUser.mockResolvedValue(account);
  render(<App />);
  await screen.findByTestId("screen-search");
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  getAdminTriggerStatus.mockResolvedValue({
    triggerEnabled: true, codeRequired: false, crawl: null, enrichment: null,
  });
  getUnreadCount.mockResolvedValue({ count: 0 });
});

async function openPaletteFromSidebar(user) {
  // Navigate to Settings via the sidebar, then open the palette via the topbar search box.
  const settingsLink = await screen.findByText("Settings", { exact: true });
  await user.click(settingsLink);
  await screen.findByText("Search settings…");
  await user.click(screen.getByText("Search settings…"));
}

// The word "Settings"/"Account"/"Billing"/etc. also appears in the sidebar nav and page
// headings, so scope nav-link assertions to the Settings screen's own left nav
// (`.settings-nav`, per SavedSettings.jsx) to avoid ambiguous-match errors.
function settingsNavLink(label) {
  const nav = document.querySelector(".settings-nav");
  return within(nav).getByText(label, { exact: true });
}

// A settings-index entry's label (e.g. "Billing") can also match the Settings screen's
// own nav link once it's active, so scope the palette-row lookup to the palette's
// result rows (`data-testid="settings-result-row"`, per CommandPalette.jsx).
async function findPaletteRow(label) {
  const rows = await screen.findAllByTestId("settings-result-row");
  const row = rows.find((r) => within(r).queryByText(label));
  if (!row) throw new Error(`No settings-result-row found for "${label}"`);
  return row;
}

describe("AC-6 : selecting Notification preferences lands on the notifications section", () => {
  it("TC-304-13 : closes the palette and activates the notifications section with no extra click", async () => {
    const user = userEvent.setup();
    await bootAsAuthedUser({ isAdmin: false, email: "user@example.com" });

    await openPaletteFromSidebar(user);

    const row = await screen.findByText("Notification preferences");
    await user.click(row);

    // Palette closed
    expect(screen.queryByPlaceholderText("Search settings…")).not.toBeInTheDocument();

    // Notifications section active: nav link has "active" class + its content is rendered
    const notifLink = settingsNavLink("Notifications");
    expect(notifLink).toHaveClass("active");
    await waitFor(() => {
      expect(screen.getByTestId("notifications-section")).toBeInTheDocument();
    });
  });
});

describe("AC-7 : selecting Change password / Two-factor auth lands on the account section", () => {
  it("TC-304-14 : selecting Change password from a non-account section lands on account", async () => {
    const user = userEvent.setup();
    await bootAsAuthedUser({ isAdmin: false, email: "user@example.com" });

    // Navigate to a non-account section first (Billing).
    const settingsLink = await screen.findByText("Settings", { exact: true });
    await user.click(settingsLink);
    const billingLink = settingsNavLink("Billing");
    await user.click(billingLink);
    expect(billingLink).toHaveClass("active");

    await user.click(screen.getByText("Search settings…"));
    const row = await screen.findByText("Change password");
    await user.click(row);

    const accountLink = settingsNavLink("Account");
    expect(accountLink).toHaveClass("active");
    expect(screen.getByText("Password")).toBeInTheDocument();
  });

  it("TC-304-15 : selecting Two-factor auth from a non-account section lands on account", async () => {
    const user = userEvent.setup();
    await bootAsAuthedUser({ isAdmin: false, email: "user@example.com" });

    const settingsLink = await screen.findByText("Settings", { exact: true });
    await user.click(settingsLink);
    const billingLink = settingsNavLink("Billing");
    await user.click(billingLink);

    await user.click(screen.getByText("Search settings…"));
    const row = await screen.findByText("Two-factor auth");
    await user.click(row);

    const accountLink = settingsNavLink("Account");
    expect(accountLink).toHaveClass("active");
    expect(screen.getByText("Two-factor auth")).toBeInTheDocument();
  });
});

describe("AC-8 : selecting Admin panel (as admin) navigates to the Admin page", () => {
  it("TC-304-16 : closes the palette and renders the top-level Admin page, not a Settings section", async () => {
    const user = userEvent.setup();
    await bootAsAuthedUser({ isAdmin: true, email: "admin@example.com" });

    await openPaletteFromSidebar(user);
    const row = await screen.findByText("Admin panel");
    await user.click(row);

    await screen.findByTestId("admin-page");
    expect(screen.queryByPlaceholderText("Search settings…")).not.toBeInTheDocument();
  });
});

describe("AC-9 : Admin panel absent for non-admins (Strategy B)", () => {
  it("TC-304-19 : typing \"trigger\" or \"crawl\" as non-admin never shows Admin panel, no admin navigation", async () => {
    const user = userEvent.setup();
    await bootAsAuthedUser({ isAdmin: false, email: "user@example.com" });

    await openPaletteFromSidebar(user);
    const input = screen.getByPlaceholderText("Search settings…");

    await user.type(input, "trigger");
    expect(screen.queryByText("Admin panel")).not.toBeInTheDocument();

    await user.clear(input);
    await user.type(input, "crawl");
    expect(screen.queryByText("Admin panel")).not.toBeInTheDocument();

    expect(getAdminTriggerStatus).not.toHaveBeenCalled();
    expect(triggerAdminPass).not.toHaveBeenCalled();
  });
});

describe("AC-12 : re-opening the palette from Settings after navigating still shows the index", () => {
  it("TC-304-26 : after selecting Billing, re-opening the palette still shows the full catalogue and filters", async () => {
    const user = userEvent.setup();
    await bootAsAuthedUser({ isAdmin: false, email: "user@example.com" });

    await openPaletteFromSidebar(user);
    const billingRow = await findPaletteRow("Billing");
    await user.click(billingRow);

    expect(settingsNavLink("Billing")).toHaveClass("active");

    // Re-open the palette (still on the Settings screen).
    await user.click(screen.getByText("Search settings…"));
    await screen.findByText("Account settings");
    expect(screen.queryByText(/No jobs found/)).not.toBeInTheDocument();

    const input = screen.getByPlaceholderText("Search settings…");
    await user.type(input, "privacy");
    await findPaletteRow("Data & privacy");
  });
});

describe("AC-14 : selecting an entry while on a different section switches immediately", () => {
  it("TC-304-30 : selecting Sources & filters while Billing is active switches to sources immediately", async () => {
    const user = userEvent.setup();
    await bootAsAuthedUser({ isAdmin: false, email: "user@example.com" });

    const settingsLink = await screen.findByText("Settings", { exact: true });
    await user.click(settingsLink);
    const billingLink = settingsNavLink("Billing");
    await user.click(billingLink);
    expect(screen.getByText("JobHub is free while you're job hunting.")).toBeInTheDocument();

    await user.click(screen.getByText("Search settings…"));
    const row = await findPaletteRow("Sources & filters");
    await user.click(row);

    const sourcesLink = settingsNavLink("Sources & filters");
    expect(sourcesLink).toHaveClass("active");
    expect(screen.queryByText("JobHub is free while you're job hunting.")).not.toBeInTheDocument();
    expect(screen.getByText("Pick where JobHub looks for jobs.")).toBeInTheDocument();
  });
});

describe("Edge : palette opened via the global ⌘K shortcut while on the Settings screen", () => {
  it("TC-304-32 : ⌘K from Settings still shows the settings index, not job/application search", async () => {
    const user = userEvent.setup();
    await bootAsAuthedUser({ isAdmin: false, email: "user@example.com" });

    const settingsLink = await screen.findByText("Settings", { exact: true });
    await user.click(settingsLink);

    await user.keyboard("{Meta>}k{/Meta}");

    await screen.findByPlaceholderText("Search settings…");
    expect(screen.getByText("Account settings")).toBeInTheDocument();
    expect(screen.queryByText(/No jobs found/)).not.toBeInTheDocument();
  });
});
