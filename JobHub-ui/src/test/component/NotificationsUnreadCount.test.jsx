/**
 * App-level wiring tests for the top-nav "Notifications" unread-count badge
 * (story #206 follow-up, ticket #237).
 *
 * The bell (and its badge) was removed from the sidebar footer in story #206; this
 * ticket restores the unread count on the surviving top-nav "Notifications" item,
 * driven from App.jsx (poll + promptly-updated-on-action), with the Sidebar itself
 * only rendering whatever count it is given (covered by Sidebar.test.jsx).
 *
 * Cases:
 *  - TC-206-C-05: polls getUnreadCount() on mount (authenticated) and renders the
 *    result on the nav item.
 *  - TC-206-C-06: polls again after the ~60s interval elapses, updating the badge.
 *  - TC-206-C-07: does not poll at all while logged out.
 *  - TC-206-C-08: a 401 from the count fetch logs the user out (mirrors the bell's
 *    handleAuthError -> onLogout pattern) and stops polling.
 *  - TC-206-C-09: a successful "Mark all as read" on the notifications page zeroes
 *    the nav badge immediately, without waiting for the next poll tick.
 *  - TC-206-C-10: deleting an UNREAD notification on the page decrements the nav
 *    badge by one immediately.
 *  - TC-206-C-11: deleting a READ notification on the page leaves the nav badge
 *    unchanged.
 *  - TC-206-C-12: clicking an UNREAD row to mark it read decrements the nav badge
 *    promptly (no poll tick needed); clicking an already-read row leaves the badge
 *    unchanged; a mark-read failure does not decrement it either.
 *
 * Strategy mirrors NotificationsNav.test.jsx: mock the API modules and heavy screens
 * so <App /> renders a minimal shell, but keep the real Sidebar + Notifications
 * screen so the count wiring between them is exercised end-to-end.
 */
import React from "react";
import { render, screen, waitFor, act, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

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
  getNotificationPreferences: vi.fn(),
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
import { getToken } from "../../api/client.js";
import {
  listNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  deleteNotification,
} from "../../api/notifications.js";
import App from "../../App.jsx";

function getNavCount() {
  const navItem = screen.getByTestId("nav-item-notifications");
  return navItem.querySelector(".count");
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  listNotifications.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
  getUnreadCount.mockResolvedValue({ count: 0 });
});

afterEach(() => {
  vi.useRealTimers();
});

describe("TC-206-C-05: initial poll renders the unread count on the nav item", () => {
  it("calls getUnreadCount on mount and shows the result", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    getUnreadCount.mockResolvedValue({ count: 5 });

    render(<App />);

    await screen.findByTestId("screen-search");
    await waitFor(() => expect(getUnreadCount).toHaveBeenCalled());
    await waitFor(() => expect(getNavCount()).toHaveTextContent("5"));
  });

  it("caps the badge at '99+' for counts over 99", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    getUnreadCount.mockResolvedValue({ count: 150 });

    render(<App />);

    await screen.findByTestId("screen-search");
    await waitFor(() => expect(getNavCount()).toHaveTextContent("99+"));
  });

  it("shows no badge when the unread count is 0", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    getUnreadCount.mockResolvedValue({ count: 0 });

    render(<App />);

    await screen.findByTestId("screen-search");
    await waitFor(() => expect(getUnreadCount).toHaveBeenCalled());
    expect(getNavCount()).toBeNull();
  });
});

describe("TC-206-C-06: re-polls on the ~60s interval and updates the badge", () => {
  it("fetches again after 60s and reflects the new count", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    getUnreadCount.mockResolvedValue({ count: 2 });

    render(<App />);

    await screen.findByTestId("screen-search");
    await waitFor(() => expect(getUnreadCount).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(getNavCount()).toHaveTextContent("2"));

    getUnreadCount.mockResolvedValue({ count: 9 });
    await act(async () => {
      vi.advanceTimersByTime(60000);
    });

    await waitFor(() => expect(getUnreadCount).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(getNavCount()).toHaveTextContent("9"));
  });
});

describe("TC-206-C-07: never polls while logged out", () => {
  it("does not call getUnreadCount and shows no badge", async () => {
    getToken.mockReturnValue(null);

    render(<App />);

    await screen.findByTestId("screen-search");
    expect(getUnreadCount).not.toHaveBeenCalled();
    expect(getNavCount()).toBeNull();
  });
});

describe("TC-206-C-08: a 401 from the count fetch logs the user out", () => {
  it("calls handleLogout (routes back to search, drops auth) instead of leaving a stale badge", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    const { ApiError } = await import("../../api/client.js");
    getUnreadCount.mockRejectedValue(new ApiError(401, "Unauthorized", { error: "Unauthorized", message: "expired" }));

    render(<App />);

    await screen.findByTestId("screen-search");
    await waitFor(() => expect(getUnreadCount).toHaveBeenCalled());

    // Logged out: the Sidebar's "Sign in" pill replaces the authenticated user pill.
    await waitFor(() => expect(screen.getByText("Sign in")).toBeInTheDocument());
    expect(getNavCount()).toBeNull();
  });
});

describe("TC-206-C-09: Mark all as read zeroes the nav badge promptly", () => {
  it("updates the badge to hidden right after a successful mark-all-read, without a poll tick", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    getUnreadCount.mockResolvedValue({ count: 3 });
    listNotifications.mockResolvedValue({
      content: [
        { id: "n-1", type: "SYSTEM", title: "First", message: "msg", read: false, createdAt: new Date().toISOString(), applicationId: null },
      ],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    markAllNotificationsRead.mockResolvedValue(undefined);

    render(<App />);
    await screen.findByTestId("screen-search");
    await waitFor(() => expect(getNavCount()).toHaveTextContent("3"));

    const user = userEvent.setup();
    await user.click(screen.getByTestId("nav-item-notifications"));
    await screen.findByTestId("notifications-page");
    await screen.findByTestId("notification-row-unread");

    await user.click(screen.getByTestId("notifications-mark-all-read"));

    await waitFor(() => expect(markAllNotificationsRead).toHaveBeenCalledTimes(1));
    // The badge must be hidden right away (no '0' rendered), not on the next 60s poll.
    await waitFor(() => expect(getNavCount()).toBeNull());
    expect(getUnreadCount).toHaveBeenCalledTimes(1); // only the initial poll, no extra fetch needed
  });
});

describe("TC-206-C-10/11: deleting a notification decrements only when it was unread", () => {
  it("TC-206-C-10: deleting an UNREAD row decrements the nav badge by one", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    getUnreadCount.mockResolvedValue({ count: 4 });
    listNotifications.mockResolvedValue({
      content: [
        { id: "n-1", type: "SYSTEM", title: "Unread one", message: "msg", read: false, createdAt: new Date().toISOString(), applicationId: null },
      ],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    deleteNotification.mockResolvedValue(undefined);

    render(<App />);
    await screen.findByTestId("screen-search");
    await waitFor(() => expect(getNavCount()).toHaveTextContent("4"));

    const user = userEvent.setup();
    await user.click(screen.getByTestId("nav-item-notifications"));
    await screen.findByTestId("notifications-page");
    const row = await screen.findByTestId("notification-row-unread");

    await user.click(within(row).getByTestId("notification-row-delete"));
    await user.click(within(row).getByTestId("notification-row-delete-confirm-button"));

    await waitFor(() => expect(deleteNotification).toHaveBeenCalledWith("n-1"));
    await waitFor(() => expect(getNavCount()).toHaveTextContent("3"));
  });

  it("TC-206-C-11: deleting a READ row leaves the nav badge unchanged", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    getUnreadCount.mockResolvedValue({ count: 4 });
    listNotifications.mockResolvedValue({
      content: [
        { id: "n-1", type: "SYSTEM", title: "Read one", message: "msg", read: true, createdAt: new Date().toISOString(), applicationId: null },
      ],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    deleteNotification.mockResolvedValue(undefined);

    render(<App />);
    await screen.findByTestId("screen-search");
    await waitFor(() => expect(getNavCount()).toHaveTextContent("4"));

    const user = userEvent.setup();
    await user.click(screen.getByTestId("nav-item-notifications"));
    await screen.findByTestId("notifications-page");
    const row = await screen.findByTestId("notification-row-read");

    await user.click(within(row).getByTestId("notification-row-delete"));
    await user.click(within(row).getByTestId("notification-row-delete-confirm-button"));

    await waitFor(() => expect(deleteNotification).toHaveBeenCalledWith("n-1"));
    expect(getNavCount()).toHaveTextContent("4");
  });
});

describe("TC-206-C-12: marking a row read on the page decrements the nav badge", () => {
  it("clicking an UNREAD row decrements the nav badge by one promptly", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    getUnreadCount.mockResolvedValue({ count: 4 });
    listNotifications.mockResolvedValue({
      content: [
        { id: "n-1", type: "SYSTEM", title: "Unread one", message: "msg", read: false, createdAt: new Date().toISOString(), applicationId: null },
      ],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    markNotificationRead.mockResolvedValue(undefined);

    render(<App />);
    await screen.findByTestId("screen-search");
    await waitFor(() => expect(getNavCount()).toHaveTextContent("4"));

    const user = userEvent.setup();
    await user.click(screen.getByTestId("nav-item-notifications"));
    await screen.findByTestId("notifications-page");
    const row = await screen.findByTestId("notification-row-unread");

    await user.click(row);

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    await waitFor(() => expect(getNavCount()).toHaveTextContent("3"));
    expect(getUnreadCount).toHaveBeenCalledTimes(1); // only the initial poll, no extra fetch needed
  });

  it("clicking an already-read row does not decrement the nav badge", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    getUnreadCount.mockResolvedValue({ count: 4 });
    listNotifications.mockResolvedValue({
      content: [
        { id: "n-1", type: "SYSTEM", title: "Read one", message: "msg", read: true, createdAt: new Date().toISOString(), applicationId: null },
      ],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });

    render(<App />);
    await screen.findByTestId("screen-search");
    await waitFor(() => expect(getNavCount()).toHaveTextContent("4"));

    const user = userEvent.setup();
    await user.click(screen.getByTestId("nav-item-notifications"));
    await screen.findByTestId("notifications-page");
    const row = await screen.findByTestId("notification-row-read");

    await user.click(row);

    expect(markNotificationRead).not.toHaveBeenCalled();
    expect(getNavCount()).toHaveTextContent("4");
  });

  it("a mark-read failure does not decrement the nav badge", async () => {
    getToken.mockReturnValue("a-valid-jwt");
    authApi.currentUser.mockResolvedValue({ isAdmin: false, email: "user@example.com", groups: ["user"] });
    getUnreadCount.mockResolvedValue({ count: 4 });
    listNotifications.mockResolvedValue({
      content: [
        { id: "n-1", type: "SYSTEM", title: "Failing one", message: "msg", read: false, createdAt: new Date().toISOString(), applicationId: null },
      ],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    const { ApiError } = await import("../../api/client.js");
    markNotificationRead.mockRejectedValue(new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" }));

    render(<App />);
    await screen.findByTestId("screen-search");
    await waitFor(() => expect(getNavCount()).toHaveTextContent("4"));

    const user = userEvent.setup();
    await user.click(screen.getByTestId("nav-item-notifications"));
    await screen.findByTestId("notifications-page");
    const row = await screen.findByTestId("notification-row-unread");

    await user.click(row);

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    expect(await screen.findByTestId("notifications-action-error")).toBeInTheDocument();
    expect(getNavCount()).toHaveTextContent("4");
  });
});
