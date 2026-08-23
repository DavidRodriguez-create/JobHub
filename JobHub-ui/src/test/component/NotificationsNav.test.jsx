/**
 * App-level wiring tests for the "All notifications" page (Story #184).
 *
 * Cases:
 *  - TC-NOTIF-NAV-01: the sidebar shows an auth-gated "Notifications" nav item, and
 *    clicking it navigates to the notifications page.
 *  - TC-NOTIF-NAV-02: the notifications route is protected - navigating to it while
 *    logged out shows the login screen, not the notifications page.
 *
 * Story #206 / Ticket #234 removed the redundant sidebar-footer bell (the only path
 * to the notifications page is now the sidebar nav item), so the bell-specific
 * TC-NOTIF-NAV-03/04 and TC-NOTIF-PAGE-26 cases were retired here; their navigation
 * coverage is superseded by TC-NOTIF-NAV-01 and TC-NOTIF-PAGE-18 below.
 *
 * Strategy mirrors AdminAccess.test.jsx: mock the API modules and heavy screens so
 * <App /> renders a minimal shell, but keep the real Sidebar + Notifications screen
 * so the nav wiring is exercised.
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
  loginTwoFactor: vi.fn(),
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
    constructor(status, message, body) {
      super(message);
      this.name = "ApiError";
      this.status = status;
      this.body = body;
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
  getApplication: vi.fn(),
  createApplication: vi.fn(),
  updateApplication: vi.fn(),
  updateApplicationJob: vi.fn(),
  updateApplicationStatus: vi.fn(),
  deleteApplication: vi.fn(),
}));

vi.mock("../../api/notifications.js", () => ({
  getNotificationPreferences: vi.fn().mockResolvedValue({
    weeklyDigestEmail: true, inAppNotificationsEnabled: true,
    interviewReminders: true, ghostedAlert: true,
  }),
  updateNotificationPreferences: vi.fn(),
  listNotifications: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 }),
  getUnreadCount: vi.fn().mockResolvedValue({ count: 0 }),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  deleteNotification: vi.fn(),
}));

vi.mock("../../data/mockData.js", () => ({
  default: {
    companies: {},
    jobs: [],
    applications: [],
    saved: [],
    byId: () => undefined,
    coOf: () => ({ name: "Acme", industry: "n/a", size: "n/a", hq: "n/a", url: "" }),
    appForJob: () => undefined,
    nextAppId: () => "APP-001",
  },
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
import { getToken, ApiError } from "../../api/client.js";
import {
  listNotifications,
  getNotificationPreferences,
  getUnreadCount,
} from "../../api/notifications.js";
import { getApplication } from "../../api/applications.js";
import App from "../../App.jsx";

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  listNotifications.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
  getNotificationPreferences.mockResolvedValue({
    weeklyDigestEmail: true, inAppNotificationsEnabled: true,
    interviewReminders: true, ghostedAlert: true,
  });
  getUnreadCount.mockResolvedValue({ count: 0 });
});

describe("TC-NOTIF-NAV-01: sidebar Notifications nav item", () => {
  it("shows the nav item when authenticated and navigates to the notifications page on click", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });

    render(<App />);

    await screen.findByTestId("screen-search");
    const navItem = await screen.findByTestId("nav-item-notifications");
    expect(navItem).toHaveTextContent("Notifications");

    const user = userEvent.setup();
    await user.click(navItem);

    expect(await screen.findByTestId("notifications-page")).toBeInTheDocument();
    await waitFor(() =>
      expect(listNotifications).toHaveBeenCalledWith({ page: 0, size: 20, readStatus: "all" })
    );

    // AC-16 / TC-NOTIF-PAGE-22 (gate finding): the sidebar item highlights as active
    // once the notifications route is current.
    expect(navItem.className).toContain("active");
  });

  it("renders the nav item dimmed (auth-gated) when logged out, between Applications and Dashboard", async () => {
    getToken.mockReturnValue(null);

    render(<App />);

    await screen.findByTestId("screen-search");
    const navItem = screen.getByTestId("nav-item-notifications");
    expect(navItem).toBeInTheDocument();
    expect(navItem).toHaveTextContent("Notifications");

    // Ordering: Applications, Notifications, Dashboard (per spec).
    const allNavTestIds = Array.from(document.querySelectorAll('[data-testid^="nav-item-"]'))
      .map((el) => el.getAttribute("data-testid"));
    const appsIdx = allNavTestIds.indexOf("nav-item-applications");
    const notifIdx = allNavTestIds.indexOf("nav-item-notifications");
    const dashIdx = allNavTestIds.indexOf("nav-item-dashboard");
    expect(appsIdx).toBeGreaterThanOrEqual(0);
    expect(notifIdx).toBeGreaterThan(appsIdx);
    expect(dashIdx).toBeGreaterThan(notifIdx);
  });
});

describe("TC-NOTIF-NAV-02: notifications route is protected", () => {
  it("shows the login screen, not the notifications page, when navigating while logged out", async () => {
    getToken.mockReturnValue(null);
    sessionStorage.setItem("jobhub_route", "notifications");

    render(<App />);

    await waitFor(() => {
      expect(screen.getByPlaceholderText("you@email.com")).toBeInTheDocument();
    });

    expect(screen.queryByTestId("notifications-page")).not.toBeInTheDocument();
    expect(listNotifications).not.toHaveBeenCalled();
  });
});

describe("TC-NOTIF-PAGE-25: page reachable regardless of the in-app notifications preference", () => {
  it("keeps the sidebar Notifications item present and clickable when in-app notifications are disabled", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });

    getNotificationPreferences.mockResolvedValue({
      weeklyDigestEmail: true, inAppNotificationsEnabled: false,
      interviewReminders: true, ghostedAlert: true,
    });

    render(<App />);

    await screen.findByTestId("screen-search");

    // The sidebar nav item is unaffected by the in-app-notifications preference: the
    // app shell no longer fetches that preference at all now that story #206 removed
    // the bell that used to gate on it.
    const navItem = screen.getByTestId("nav-item-notifications");
    expect(navItem).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(navItem);

    expect(await screen.findByTestId("notifications-page")).toBeInTheDocument();
  });
});

describe("TC-NOTIF-PAGE-18: deep-link from the Notifications page to a deleted application", () => {
  it("clicking a page row whose applicationId 404s on detail fetch reaches the existing not-found screen", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    listNotifications.mockResolvedValue({
      content: [
        {
          id: "n-1", type: "GHOSTED_ALERT", title: "Ghosted",
          message: "Acme Corp went quiet.", read: false,
          createdAt: new Date().toISOString(), applicationId: "deleted-app-1",
        },
      ],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    getApplication.mockRejectedValue(
      new ApiError(404, "Not Found", { error: "Not Found", message: "no such application" })
    );

    render(<App />);

    await screen.findByTestId("screen-search");
    const navItem = await screen.findByTestId("nav-item-notifications");
    const user = userEvent.setup();
    await user.click(navItem);

    expect(await screen.findByTestId("notifications-page")).toBeInTheDocument();

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(row);

    await waitFor(() => expect(getApplication).toHaveBeenCalledWith("deleted-app-1"));
    expect(await screen.findByTestId("application-not-found")).toBeInTheDocument();
    expect(screen.queryByTestId("notifications-page")).not.toBeInTheDocument();
  });
});
