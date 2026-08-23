/**
 * Component tests for the "All notifications" page (Story #184).
 *
 * Cases (QAE-style, authored alongside this ticket since no separate spec/test-case
 * docs were checked into the worktree - see PR notes):
 *  - TC-NOTIF-PAGE-01..04: initial load (loading / populated / empty / error states)
 *  - TC-NOTIF-PAGE-05..06: rows render both read and unread, newest-first as returned by the API
 *  - TC-NOTIF-PAGE-07..09: row presentation reuses the bell's icon/title/message/time-ago
 *  - TC-NOTIF-PAGE-10..15: numbered pager (prev/next disabled state, indicator, re-fetch + replace)
 *  - TC-NOTIF-PAGE-16..20: row click semantics (mark-read, navigate, flip-in-place, action-error,
 *    mark-read failure still navigates)
 *  - TC-NOTIF-PAGE-21..23: rows without applicationId never navigate (SYSTEM/SECURITY_RECOMMENDATION)
 *  - TC-NOTIF-PAGE-24..26: a read row is still clickable to navigate but never re-calls mark-read
 *
 * Strategy: mock ../../api/notifications.js, render <NotificationsScreen /> directly.
 */
import React from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/notifications.js", () => ({
  listNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  deleteNotification: vi.fn(),
}));

vi.mock("../../api/client.js", () => ({
  ApiError: class ApiError extends Error {
    constructor(status, message, body) {
      super(message);
      this.status = status;
      this.body = body;
    }
  },
}));

import {
  listNotifications,
  markNotificationRead,
  markAllNotificationsRead,
  deleteNotification,
} from "../../api/notifications.js";
import { ApiError } from "../../api/client.js";
import { NotificationsScreen } from "../../screens/Notifications.jsx";

// Story #439 / Ticket #535 (TC-439-41): the fixture default now carries an explicit
// `category: "APPLICATION"`, matching the default `type: "APPLICATION_UPDATE"`
// (BR-439-2: APPLICATION_UPDATE always derives to APPLICATION server-side). This is
// a REQUIRED CORRECTION, not new behaviour: once the category gate ships, a fixture
// with no `category` key resolves to effective category ACCOUNT (BR-439-8) and would
// silently stop showing the identity row every pre-existing test in this file that
// asserts one is present (NL-UI-01/02/03/06/07) expects. Defaulting it here keeps
// those assertions proving exactly what they always proved: an APPLICATION-category
// notification shows its identity row.
function notif(overrides = {}) {
  return {
    id: "n-1",
    type: "APPLICATION_UPDATE",
    category: "APPLICATION",
    title: "Application updated",
    message: "Your application moved to Interview.",
    read: false,
    createdAt: new Date().toISOString(),
    applicationId: null,
    ...overrides,
  };
}

function page(content, overrides = {}) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    ...overrides,
  };
}

function renderScreen(props = {}) {
  return render(<NotificationsScreen onOpenApplication={vi.fn()} {...props} />);
}

beforeEach(() => {
  vi.clearAllMocks();
  listNotifications.mockResolvedValue(page([]));
});

describe("TC-NOTIF-PAGE-01..04: initial load states", () => {
  it("TC-NOTIF-PAGE-01: renders the page root and requests page 0 with readStatus 'all'", async () => {
    renderScreen();
    expect(screen.getByTestId("notifications-page")).toBeInTheDocument();
    await waitFor(() =>
      expect(listNotifications).toHaveBeenCalledWith({ page: 0, size: 20, readStatus: "all" })
    );
  });

  it("TC-NOTIF-PAGE-02: shows a loading state while the first page is in flight", async () => {
    let resolveList;
    listNotifications.mockImplementation(() => new Promise((resolve) => { resolveList = resolve; }));
    renderScreen();

    expect(screen.getByTestId("notifications-loading")).toBeInTheDocument();

    resolveList(page([]));
    await waitFor(() => expect(screen.queryByTestId("notifications-loading")).not.toBeInTheDocument());
  });

  it("TC-NOTIF-PAGE-03: shows the exact empty-state copy when there are no notifications at all", async () => {
    listNotifications.mockResolvedValue(page([]));
    renderScreen();

    const empty = await screen.findByTestId("notifications-empty");
    expect(empty).toHaveTextContent("You're all caught up, no notifications yet.");
  });

  it("TC-NOTIF-PAGE-04: shows an error state (not empty) when the list fails to load", async () => {
    listNotifications.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );
    renderScreen();

    expect(await screen.findByTestId("notifications-list-error")).toHaveTextContent(
      "Couldn't load notifications, please try again later."
    );
    expect(screen.queryByTestId("notifications-empty")).not.toBeInTheDocument();
  });
});

describe("TC-NOTIF-PAGE-05..06: rows render read and unread, newest-first", () => {
  it("TC-NOTIF-PAGE-05: renders both an unread and a read row using distinct testids", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-1", read: false, title: "Unread one" }),
        notif({ id: "n-2", read: true, title: "Read one" }),
      ])
    );
    renderScreen();

    expect(await screen.findByTestId("notification-row-unread")).toBeInTheDocument();
    expect(screen.getByTestId("notification-row-read")).toBeInTheDocument();
    expect(screen.getByText("Unread one")).toBeInTheDocument();
    expect(screen.getByText("Read one")).toBeInTheDocument();
  });

  it("TC-NOTIF-PAGE-06: renders rows in the order returned by the API (newest-first is the API's contract)", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-new", read: false, title: "Newest" }),
        notif({ id: "n-old", read: true, title: "Oldest" }),
      ])
    );
    renderScreen();

    await screen.findByText("Newest");
    const titles = screen.getAllByText(/Newest|Oldest/).map((el) => el.textContent);
    expect(titles).toEqual(["Newest", "Oldest"]);
  });
});

describe("TC-NOTIF-PAGE-07..09: row presentation mirrors the bell", () => {
  it("TC-NOTIF-PAGE-07: shows the type icon, title, message, and relative time", async () => {
    const createdAt = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    listNotifications.mockResolvedValue(
      page([
        notif({
          id: "n-1", type: "INTERVIEW_REMINDER", title: "Interview tomorrow",
          message: "Your interview with Acme is tomorrow at 10am.", read: false, createdAt,
        }),
      ])
    );
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).getByTestId("notification-icon-INTERVIEW_REMINDER")).toBeInTheDocument();
    expect(within(row).getByText("Interview tomorrow")).toBeInTheDocument();
    expect(within(row).getByText("Your interview with Acme is tomorrow at 10am.")).toBeInTheDocument();
    expect(within(row).getByText("5m ago")).toBeInTheDocument();
  });

  it("TC-NOTIF-PAGE-08: renders an unknown notification type with the default icon", async () => {
    listNotifications.mockResolvedValue(page([notif({ id: "n-1", type: "SOME_FUTURE_TYPE", read: false })]));
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).getByTestId("notification-icon-SOME_FUTURE_TYPE")).toBeInTheDocument();
  });

  it("TC-NOTIF-PAGE-09: a read row renders the same fields as an unread row", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", type: "GHOSTED_ALERT", title: "Ghosted", message: "Acme went quiet.", read: true })])
    );
    renderScreen();

    const row = await screen.findByTestId("notification-row-read");
    expect(within(row).getByTestId("notification-icon-GHOSTED_ALERT")).toBeInTheDocument();
    expect(within(row).getByText("Ghosted")).toBeInTheDocument();
    expect(within(row).getByText("Acme went quiet.")).toBeInTheDocument();
  });
});

describe("TC-NOTIF-PAGE-10..15: numbered pager", () => {
  it("TC-NOTIF-PAGE-10: shows the pager with a page indicator and disables Prev on the first page", async () => {
    listNotifications.mockResolvedValue(page([notif()], { page: 0, totalPages: 3, totalElements: 41 }));
    renderScreen();

    await screen.findByTestId("notifications-page-pager");
    expect(screen.getByTestId("notifications-page-pager-indicator")).toHaveTextContent("1");
    expect(screen.getByTestId("notifications-page-pager-prev")).toBeDisabled();
    expect(screen.getByTestId("notifications-page-pager-next")).toBeEnabled();
  });

  it("TC-NOTIF-PAGE-11: disables Next on the last page", async () => {
    listNotifications.mockResolvedValue(page([notif()], { page: 2, totalPages: 3, totalElements: 41 }));
    renderScreen({ initialPage: 2 });

    await screen.findByTestId("notifications-page-pager");
    expect(screen.getByTestId("notifications-page-pager-next")).toBeDisabled();
    expect(screen.getByTestId("notifications-page-pager-prev")).toBeEnabled();
  });

  it("TC-NOTIF-PAGE-12: clicking Next re-fetches the following page and replaces the rows", async () => {
    listNotifications.mockResolvedValueOnce(
      page([notif({ id: "n-page0", title: "Page 0 item" })], { page: 0, totalPages: 2, totalElements: 21 })
    );
    const user = userEvent.setup();
    renderScreen();

    await screen.findByText("Page 0 item");

    listNotifications.mockResolvedValueOnce(
      page([notif({ id: "n-page1", title: "Page 1 item" })], { page: 1, totalPages: 2, totalElements: 21 })
    );
    await user.click(screen.getByTestId("notifications-page-pager-next"));

    await waitFor(() =>
      expect(listNotifications).toHaveBeenLastCalledWith({ page: 1, size: 20, readStatus: "all" })
    );
    expect(await screen.findByText("Page 1 item")).toBeInTheDocument();
    expect(screen.queryByText("Page 0 item")).not.toBeInTheDocument();
  });

  it("TC-NOTIF-PAGE-13: clicking Prev re-fetches the previous page", async () => {
    listNotifications.mockResolvedValueOnce(
      page([notif({ id: "n-page1", title: "Page 1 item" })], { page: 1, totalPages: 2, totalElements: 21 })
    );
    const user = userEvent.setup();
    renderScreen({ initialPage: 1 });

    await screen.findByText("Page 1 item");

    listNotifications.mockResolvedValueOnce(
      page([notif({ id: "n-page0", title: "Page 0 item" })], { page: 0, totalPages: 2, totalElements: 21 })
    );
    await user.click(screen.getByTestId("notifications-page-pager-prev"));

    await waitFor(() =>
      expect(listNotifications).toHaveBeenLastCalledWith({ page: 0, size: 20, readStatus: "all" })
    );
    expect(await screen.findByText("Page 0 item")).toBeInTheDocument();
  });

  it("TC-NOTIF-PAGE-14: the pager indicator reflects the current/total page count", async () => {
    listNotifications.mockResolvedValue(page([notif()], { page: 1, totalPages: 5, totalElements: 90 }));
    renderScreen({ initialPage: 1 });

    const indicator = await screen.findByTestId("notifications-page-pager-indicator");
    expect(indicator).toHaveTextContent("2");
    expect(indicator).toHaveTextContent("5");
  });

  it("TC-NOTIF-PAGE-15: no pager is shown when there is only a single page", async () => {
    listNotifications.mockResolvedValue(page([notif()], { page: 0, totalPages: 1, totalElements: 1 }));
    renderScreen();

    await screen.findByTestId("notification-row-unread");
    expect(screen.queryByTestId("notifications-page-pager")).not.toBeInTheDocument();
  });

  it("AC-7 explicit boundary: 45 items at size 20 -> 3 pages, and the last page renders exactly 5 rows", async () => {
    listNotifications.mockResolvedValueOnce(
      page(
        Array.from({ length: 20 }, (_, i) => notif({ id: `n-p0-${i}`, title: `Page0 item ${i}` })),
        { page: 0, totalPages: 3, totalElements: 45 }
      )
    );
    const user = userEvent.setup();
    renderScreen();

    await screen.findByTestId("notifications-page-pager");
    expect(screen.getByTestId("notifications-page-pager-indicator")).toHaveTextContent("3");

    listNotifications.mockResolvedValueOnce(
      page(
        Array.from({ length: 20 }, (_, i) => notif({ id: `n-p1-${i}`, title: `Page1 item ${i}` })),
        { page: 1, totalPages: 3, totalElements: 45 }
      )
    );
    await user.click(screen.getByTestId("notifications-page-pager-next"));
    await waitFor(() =>
      expect(listNotifications).toHaveBeenLastCalledWith({ page: 1, size: 20, readStatus: "all" })
    );
    await screen.findByText("Page1 item 0");

    listNotifications.mockResolvedValueOnce(
      page(
        Array.from({ length: 5 }, (_, i) => notif({ id: `n-p2-${i}`, title: `Page2 item ${i}` })),
        { page: 2, totalPages: 3, totalElements: 45 }
      )
    );
    await user.click(screen.getByTestId("notifications-page-pager-next"));

    await waitFor(() =>
      expect(listNotifications).toHaveBeenLastCalledWith({ page: 2, size: 20, readStatus: "all" })
    );
    await screen.findByText("Page2 item 0");
    // Matches only the row containers (read/unread), not the per-row identity testids
    // (notification-row-co-logo/-job-title/-fallback-icon) added by story #207/#216.
    expect(screen.getAllByTestId(/^notification-row-(read|unread)$/)).toHaveLength(5);
    expect(screen.getByTestId("notifications-page-pager-next")).toBeDisabled();
  });
});

describe("TC-NOTIF-PAGE-16..20: row click semantics", () => {
  it("TC-NOTIF-PAGE-16: clicking an unread row without an applicationId marks it read and flips it in place", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", read: false, title: "First", applicationId: null })])
    );
    markNotificationRead.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(row);

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    await waitFor(() => expect(screen.getByTestId("notification-row-read")).toBeInTheDocument());
    expect(screen.queryByTestId("notification-row-unread")).not.toBeInTheDocument();
    expect(screen.getByText("First")).toBeInTheDocument();
  });

  it("TC-NOTIF-PAGE-17: clicking an unread row with an applicationId marks it read AND navigates", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", read: false, title: "Deep link", applicationId: "app-123" })])
    );
    markNotificationRead.mockResolvedValue(undefined);
    const onOpenApplication = vi.fn();
    const user = userEvent.setup();
    renderScreen({ onOpenApplication });

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(row);

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    expect(onOpenApplication).toHaveBeenCalledWith("app-123");
    expect(onOpenApplication).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(screen.getByTestId("notification-row-read")).toBeInTheDocument());
  });

  it("TC-NOTIF-PAGE-18: never removes a row on read - it flips in place, total row count stays the same", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-1", read: false, title: "First" }),
        notif({ id: "n-2", read: false, title: "Second" }),
      ])
    );
    markNotificationRead.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderScreen();

    const rows = await screen.findAllByTestId("notification-row-unread");
    expect(rows).toHaveLength(2);
    await user.click(within(rows[0]).getByText("First"));

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    expect(screen.getByText("First")).toBeInTheDocument();
    expect(screen.getByText("Second")).toBeInTheDocument();
    expect(screen.getByTestId("notification-row-read")).toBeInTheDocument();
    expect(screen.getByTestId("notification-row-unread")).toBeInTheDocument();
  });

  it("TC-NOTIF-PAGE-19: a mark-read failure shows an action-error and leaves the row unread", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", read: false, title: "Failing one", applicationId: null })])
    );
    markNotificationRead.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );
    const user = userEvent.setup();
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(row);

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    expect(await screen.findByTestId("notifications-action-error")).toBeInTheDocument();
    expect(screen.getByTestId("notification-row-unread")).toBeInTheDocument();
    expect(screen.queryByTestId("notification-row-read")).not.toBeInTheDocument();
  });

  it("TC-NOTIF-PAGE-20: a mark-read failure on a row WITH applicationId still navigates", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", read: false, title: "Failing deep link", applicationId: "app-789" })])
    );
    markNotificationRead.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );
    const onOpenApplication = vi.fn();
    const user = userEvent.setup();
    renderScreen({ onOpenApplication });

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(row);

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    expect(await screen.findByTestId("notifications-action-error")).toBeInTheDocument();
    expect(onOpenApplication).toHaveBeenCalledWith("app-789");
  });
});

describe("TC-NOTIF-PAGE-21..23: rows without applicationId never navigate", () => {
  it("TC-NOTIF-PAGE-21: a SYSTEM notification without applicationId marks read but never navigates", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", type: "SYSTEM", read: false, title: "System notice", applicationId: null })])
    );
    markNotificationRead.mockResolvedValue(undefined);
    const onOpenApplication = vi.fn();
    const user = userEvent.setup();
    renderScreen({ onOpenApplication });

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(row);

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    expect(onOpenApplication).not.toHaveBeenCalled();
  });

  it("TC-NOTIF-PAGE-22: a SECURITY_RECOMMENDATION notification without applicationId never navigates", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", type: "SECURITY_RECOMMENDATION", read: false, title: "Security tip", applicationId: null })])
    );
    markNotificationRead.mockResolvedValue(undefined);
    const onOpenApplication = vi.fn();
    const user = userEvent.setup();
    renderScreen({ onOpenApplication });

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(row);

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    expect(onOpenApplication).not.toHaveBeenCalled();
  });

  it("TC-NOTIF-PAGE-23: a read row without an applicationId does not call markNotificationRead nor navigate on click", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", read: true, title: "Already read", applicationId: null })])
    );
    const onOpenApplication = vi.fn();
    const user = userEvent.setup();
    renderScreen({ onOpenApplication });

    const row = await screen.findByTestId("notification-row-read");
    await user.click(row);

    expect(markNotificationRead).not.toHaveBeenCalled();
    expect(onOpenApplication).not.toHaveBeenCalled();
  });
});

describe("TC-NOTIF-PAGE-24..26: read rows with applicationId remain navigable, idempotent mark-read", () => {
  it("TC-NOTIF-PAGE-24: clicking an already-read row with an applicationId navigates without calling markNotificationRead again", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", read: true, title: "Already read", applicationId: "app-456" })])
    );
    const onOpenApplication = vi.fn();
    const user = userEvent.setup();
    renderScreen({ onOpenApplication });

    const row = await screen.findByTestId("notification-row-read");
    await user.click(row);

    expect(markNotificationRead).not.toHaveBeenCalled();
    expect(onOpenApplication).toHaveBeenCalledWith("app-456");
  });

  it("TC-NOTIF-PAGE-25: a row stays read (does not flip back to unread) after a click that only navigates", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", read: true, title: "Already read", applicationId: "app-456" })])
    );
    const onOpenApplication = vi.fn();
    const user = userEvent.setup();
    renderScreen({ onOpenApplication });

    const row = await screen.findByTestId("notification-row-read");
    await user.click(row);

    expect(screen.getByTestId("notification-row-read")).toBeInTheDocument();
    expect(screen.queryByTestId("notification-row-unread")).not.toBeInTheDocument();
  });

  it("TC-NOTIF-PAGE-26: when onOpenApplication is not provided, clicking a row with applicationId does not throw", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", read: false, title: "No handler", applicationId: "app-999" })])
    );
    markNotificationRead.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<NotificationsScreen />);

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(row);

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    expect(await screen.findByTestId("notification-row-read")).toBeInTheDocument();
  });
});

// ─── Story #207 / Ticket #216: card identity (company + job title), NL-UI-01..07 ───
//
// Same identity-rendering rules as the bell (NB-UI-*), but on the all-notifications
// page rows render BOTH read and unread states; the read/unread visual distinction is
// unaffected by whether identity resolved (PDA spec section 3.2/3.3).
describe("NL-UI-01..07: card identity (company + job title)", () => {
  it("NL-UI-01: an unread and a read row both with resolved company/jobTitle show their chip + job title (AC-LIST-1)", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-1", read: false, title: "Unread one", company: "Acme Corp", jobTitle: "Backend Engineer" }),
        notif({ id: "n-2", read: true, title: "Read one", company: "Globex", jobTitle: "Frontend Engineer" }),
      ])
    );
    renderScreen();

    const unreadRow = await screen.findByTestId("notification-row-unread");
    const readRow = await screen.findByTestId("notification-row-read");

    expect(within(unreadRow).getByTestId("notification-row-co-logo")).toHaveAttribute("data-co", "Acme Corp");
    expect(within(unreadRow).getByTestId("notification-row-job-title")).toHaveTextContent("Backend Engineer");

    expect(within(readRow).getByTestId("notification-row-co-logo")).toHaveAttribute("data-co", "Globex");
    expect(within(readRow).getByTestId("notification-row-job-title")).toHaveTextContent("Frontend Engineer");
  });

  it("NL-UI-02: a read row with unresolved company/jobTitle shows the fallback, no list-level error (AC-LIST-2)", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", read: true, title: "Read, unresolved", company: null, jobTitle: null })])
    );
    renderScreen();

    const row = await screen.findByTestId("notification-row-read");
    expect(within(row).getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(within(row).getByTestId("notification-row-job-title")).toHaveTextContent(
      "Application no longer available"
    );
    expect(screen.queryByTestId("notifications-list-error")).not.toBeInTheDocument();
  });

  it("NL-UI-03: pagination renders each page's identity state independently and never throws/blanks (AC-LIST-3)", async () => {
    listNotifications.mockResolvedValueOnce(
      page(
        [notif({ id: "n-p0", title: "Page 0 item", company: "Acme Corp", jobTitle: "Backend Engineer" })],
        { page: 0, totalPages: 2, totalElements: 2 }
      )
    );
    const user = userEvent.setup();
    renderScreen();

    let row = await screen.findByTestId("notification-row-unread");
    expect(within(row).getByTestId("notification-row-co-logo")).toBeInTheDocument();

    listNotifications.mockResolvedValueOnce(
      page(
        [notif({ id: "n-p1", title: "Page 1 item", company: null, jobTitle: null })],
        { page: 1, totalPages: 2, totalElements: 2 }
      )
    );
    await user.click(screen.getByTestId("notifications-page-pager-next"));

    await waitFor(() => expect(screen.getByText("Page 1 item")).toBeInTheDocument());
    row = screen.getByTestId("notification-row-unread");
    expect(within(row).getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(screen.queryByTestId("notifications-list-error")).not.toBeInTheDocument();
  });

  it("NL-UI-04: the empty state renders no identity elements (AC-EMPTY-2)", async () => {
    listNotifications.mockResolvedValue(page([]));
    renderScreen();

    expect(await screen.findByTestId("notifications-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
    expect(screen.queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
  });

  it("NL-UI-05: a list-load failure shows the existing error and renders no pager/rows (AC-ERROR-2)", async () => {
    listNotifications.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );
    renderScreen();

    expect(await screen.findByTestId("notifications-list-error")).toBeInTheDocument();
    expect(screen.queryByTestId("notifications-page-pager")).not.toBeInTheDocument();
    expect(screen.queryByTestId("notifications-list")).not.toBeInTheDocument();
  });

  it("NL-UI-06: one unresolved row among resolved rows shows only that row's fallback, no list-level error (AC-ERROR-3, EC-1)", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-1", read: false, title: "Resolved", company: "Acme Corp", jobTitle: "Backend Engineer" }),
        notif({ id: "n-2", read: false, title: "Unresolved", company: null, jobTitle: null }),
      ])
    );
    renderScreen();

    await screen.findAllByTestId("notification-row-unread");
    expect(screen.queryByTestId("notifications-list-error")).not.toBeInTheDocument();
    expect(screen.getAllByTestId("notification-row-co-logo")).toHaveLength(1);
    expect(screen.getAllByTestId("notification-row-fallback-icon")).toHaveLength(1);
  });

  it("NL-UI-07: marking a fallback row as read updates its read state without affecting the identity fallback (EC-4)", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", read: false, title: "Unresolved", applicationId: null, company: null, jobTitle: null })])
    );
    markNotificationRead.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    await user.click(row);

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith("n-1"));
    const readRow = await screen.findByTestId("notification-row-read");
    expect(within(readRow).getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(within(readRow).getByTestId("notification-row-job-title")).toHaveTextContent(
      "Application no longer available"
    );
  });
});

// ─── Story #206 / Ticket #234: "Mark all as read" (TC-206-F-09..15, AC-2) ───
describe("TC-206-F-09..15: mark all as read", () => {
  it("TC-206-F-09: the control is visible and enabled once the list has loaded with at least one unread row", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-1", read: false, title: "First" }),
        notif({ id: "n-2", read: true, title: "Second" }),
      ])
    );
    renderScreen();

    const btn = await screen.findByTestId("notifications-mark-all-read");
    expect(btn).toBeEnabled();
  });

  it("TC-206-F-10: a successful mark-all-read flips every unread row to read in place, no row removed", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-1", read: false, title: "First" }),
        notif({ id: "n-2", read: false, title: "Second" }),
        notif({ id: "n-3", read: true, title: "Third" }),
      ])
    );
    markAllNotificationsRead.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderScreen();

    const before = await screen.findAllByTestId(/^notification-row-(read|unread)$/);
    expect(before).toHaveLength(3);

    await user.click(screen.getByTestId("notifications-mark-all-read"));

    await waitFor(() => expect(markAllNotificationsRead).toHaveBeenCalledTimes(1));
    expect(markAllNotificationsRead).toHaveBeenCalledWith();
    await waitFor(() => expect(screen.queryByTestId("notification-row-unread")).not.toBeInTheDocument());
    expect(screen.getAllByTestId(/^notification-row-(read|unread)$/)).toHaveLength(3);
    expect(screen.queryByTestId("notifications-action-error")).not.toBeInTheDocument();
  });

  it("TC-206-F-11: a failed mark-all-read leaves every row unchanged and shows an action-error", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-1", read: false, title: "First" }),
        notif({ id: "n-2", read: true, title: "Second" }),
      ])
    );
    markAllNotificationsRead.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );
    const user = userEvent.setup();
    renderScreen();

    await screen.findByTestId("notification-row-unread");
    await user.click(screen.getByTestId("notifications-mark-all-read"));

    await waitFor(() => expect(markAllNotificationsRead).toHaveBeenCalledTimes(1));
    expect(screen.getByTestId("notification-row-unread")).toBeInTheDocument();
    expect(screen.getByTestId("notification-row-read")).toBeInTheDocument();
    expect(await screen.findByTestId("notifications-action-error")).toBeInTheDocument();
    expect(screen.queryByTestId("notifications-list-error")).not.toBeInTheDocument();
  });

  it("TC-206-F-12: a 401 during mark-all-read logs the user out instead of showing an action-error", async () => {
    listNotifications.mockResolvedValue(page([notif({ id: "n-1", read: false, title: "First" })]));
    markAllNotificationsRead.mockRejectedValue(
      new ApiError(401, "Unauthorized", { error: "Unauthorized", message: "expired" })
    );
    const onLogout = vi.fn();
    const user = userEvent.setup();
    renderScreen({ onLogout });

    await screen.findByTestId("notification-row-unread");
    await user.click(screen.getByTestId("notifications-mark-all-read"));

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId("notifications-action-error")).not.toBeInTheDocument();
  });

  it("TC-206-F-13: the control is not rendered on the empty state", async () => {
    listNotifications.mockResolvedValue(page([]));
    renderScreen();

    await screen.findByTestId("notifications-empty");
    expect(screen.queryByTestId("notifications-mark-all-read")).not.toBeInTheDocument();
  });

  it("TC-206-F-14: the control is disabled when every rendered row is already read", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-1", read: true, title: "First" }),
        notif({ id: "n-2", read: true, title: "Second" }),
      ])
    );
    const user = userEvent.setup();
    renderScreen();

    const btn = await screen.findByTestId("notifications-mark-all-read");
    expect(btn).toBeDisabled();

    await user.click(btn);
    expect(markAllNotificationsRead).not.toHaveBeenCalled();
  });

  it("TC-206-F-15: a successful mark-all-read does not trigger a list re-fetch", async () => {
    listNotifications.mockResolvedValue(page([notif({ id: "n-1", read: false, title: "First" })]));
    markAllNotificationsRead.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderScreen();

    await screen.findByTestId("notification-row-unread");
    const callsBefore = listNotifications.mock.calls.length;

    await user.click(screen.getByTestId("notifications-mark-all-read"));

    await waitFor(() => expect(markAllNotificationsRead).toHaveBeenCalledTimes(1));
    expect(listNotifications.mock.calls.length).toBe(callsBefore);
  });
});

// ─── Story #206 / Ticket #234: per-row delete (TC-206-F-16..26, AC-3) ───
describe("TC-206-F-16..26: per-row delete", () => {
  it("TC-206-F-16: every row, read or unread, exposes a delete control", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-1", read: false, title: "Unread one" }),
        notif({ id: "n-2", read: true, title: "Read one" }),
      ])
    );
    renderScreen();

    const unreadRow = await screen.findByTestId("notification-row-unread");
    const readRow = await screen.findByTestId("notification-row-read");
    expect(within(unreadRow).getByTestId("notification-row-delete")).toBeInTheDocument();
    expect(within(readRow).getByTestId("notification-row-delete")).toBeInTheDocument();
  });

  it("TC-206-F-17: clicking delete opens an inline confirm step without calling the API", async () => {
    listNotifications.mockResolvedValue(page([notif({ id: "n-1", read: false, title: "First" })]));
    const user = userEvent.setup();
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(within(row).getByTestId("notification-row-delete"));

    expect(within(row).getByTestId("notification-row-delete-confirm")).toBeInTheDocument();
    expect(within(row).getByTestId("notification-row-delete-cancel")).toBeInTheDocument();
    expect(within(row).getByTestId("notification-row-delete-confirm-button")).toBeInTheDocument();
    expect(deleteNotification).not.toHaveBeenCalled();
  });

  it("TC-206-F-18: confirming delete on success removes the row, decreasing total row count by one", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-1", read: false, title: "First" }),
        notif({ id: "n-2", read: true, title: "Second" }),
      ])
    );
    deleteNotification.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(within(row).getByTestId("notification-row-delete"));
    await user.click(within(row).getByTestId("notification-row-delete-confirm-button"));

    await waitFor(() => expect(deleteNotification).toHaveBeenCalledWith("n-1"));
    await waitFor(() => expect(screen.queryByText("First")).not.toBeInTheDocument());
    expect(screen.getAllByTestId(/^notification-row-(read|unread)$/)).toHaveLength(1);
  });

  it("TC-206-F-19: cancelling the confirm step makes no API call and leaves the row untouched", async () => {
    listNotifications.mockResolvedValue(page([notif({ id: "n-1", read: false, title: "First" })]));
    const user = userEvent.setup();
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(within(row).getByTestId("notification-row-delete"));
    await user.click(within(row).getByTestId("notification-row-delete-cancel"));

    expect(deleteNotification).not.toHaveBeenCalled();
    expect(screen.queryByTestId("notification-row-delete-confirm")).not.toBeInTheDocument();
    expect(screen.getByTestId("notification-row-unread")).toBeInTheDocument();
    expect(screen.getByText("First")).toBeInTheDocument();
  });

  it("TC-206-F-20: deleting an unread row never calls markNotificationRead", async () => {
    listNotifications.mockResolvedValue(page([notif({ id: "n-1", read: false, title: "First" })]));
    deleteNotification.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(within(row).getByTestId("notification-row-delete"));
    await user.click(within(row).getByTestId("notification-row-delete-confirm-button"));

    await waitFor(() => expect(deleteNotification).toHaveBeenCalledWith("n-1"));
    expect(markNotificationRead).not.toHaveBeenCalled();
  });

  it("TC-206-F-21: clicking delete on an unread row with an applicationId never triggers mark-read or navigation", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", read: false, title: "First", applicationId: "app-123" })])
    );
    const onOpenApplication = vi.fn();
    const user = userEvent.setup();
    renderScreen({ onOpenApplication });

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(within(row).getByTestId("notification-row-delete"));

    expect(within(row).getByTestId("notification-row-delete-confirm")).toBeInTheDocument();
    expect(markNotificationRead).not.toHaveBeenCalled();
    expect(onOpenApplication).not.toHaveBeenCalled();
  });

  it("TC-206-F-22: a failed delete (500) leaves the row in place and shows an action-error", async () => {
    listNotifications.mockResolvedValue(page([notif({ id: "n-1", read: false, title: "First" })]));
    deleteNotification.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );
    const user = userEvent.setup();
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(within(row).getByTestId("notification-row-delete"));
    await user.click(within(row).getByTestId("notification-row-delete-confirm-button"));

    await waitFor(() => expect(deleteNotification).toHaveBeenCalledWith("n-1"));
    expect(screen.getByTestId("notification-row-unread")).toBeInTheDocument();
    expect(await screen.findByTestId("notifications-action-error")).toBeInTheDocument();
    expect(screen.queryByTestId("notifications-list-error")).not.toBeInTheDocument();
  });

  it("TC-206-F-23: a failed delete (404) shows the identical action-error presentation as a 500", async () => {
    listNotifications.mockResolvedValue(page([notif({ id: "n-1", read: false, title: "First" })]));
    deleteNotification.mockRejectedValue(
      new ApiError(404, "Not Found", { error: "Not Found", message: "no such notification" })
    );
    const user = userEvent.setup();
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(within(row).getByTestId("notification-row-delete"));
    await user.click(within(row).getByTestId("notification-row-delete-confirm-button"));

    await waitFor(() => expect(deleteNotification).toHaveBeenCalledWith("n-1"));
    expect(screen.getByTestId("notification-row-unread")).toBeInTheDocument();
    expect(await screen.findByTestId("notifications-action-error")).toBeInTheDocument();
    expect(screen.queryByTestId("notifications-list-error")).not.toBeInTheDocument();
  });

  it("TC-206-F-24: a 401 during delete logs the user out instead of showing an action-error", async () => {
    listNotifications.mockResolvedValue(page([notif({ id: "n-1", read: false, title: "First" })]));
    deleteNotification.mockRejectedValue(
      new ApiError(401, "Unauthorized", { error: "Unauthorized", message: "expired" })
    );
    const onLogout = vi.fn();
    const user = userEvent.setup();
    renderScreen({ onLogout });

    const row = await screen.findByTestId("notification-row-unread");
    await user.click(within(row).getByTestId("notification-row-delete"));
    await user.click(within(row).getByTestId("notification-row-delete-confirm-button"));

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId("notifications-action-error")).not.toBeInTheDocument();
  });

  it("TC-206-F-25: opening one row's confirm does not affect another row's independent confirm state", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({ id: "n-1", read: false, title: "First" }),
        notif({ id: "n-2", read: false, title: "Second" }),
      ])
    );
    const user = userEvent.setup();
    renderScreen();

    const rows = await screen.findAllByTestId("notification-row-unread");
    const [rowA, rowB] = rows;

    await user.click(within(rowA).getByTestId("notification-row-delete"));
    expect(within(rowA).getByTestId("notification-row-delete-confirm")).toBeInTheDocument();

    await user.click(within(rowB).getByTestId("notification-row-delete"));
    expect(within(rowB).getByTestId("notification-row-delete-confirm")).toBeInTheDocument();
    expect(within(rowA).getByTestId("notification-row-delete-confirm")).toBeInTheDocument();

    expect(deleteNotification).not.toHaveBeenCalled();
  });

  it("TC-206-F-26: a successful delete does not trigger a list re-fetch", async () => {
    listNotifications.mockResolvedValue(page([notif({ id: "n-1", read: false, title: "First" })]));
    deleteNotification.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    const callsBefore = listNotifications.mock.calls.length;

    await user.click(within(row).getByTestId("notification-row-delete"));
    await user.click(within(row).getByTestId("notification-row-delete-confirm-button"));

    await waitFor(() => expect(deleteNotification).toHaveBeenCalledWith("n-1"));
    expect(listNotifications.mock.calls.length).toBe(callsBefore);
  });
});

// ─── Story #439 / Ticket #535 (ADR 0031, docs/product/439-notification-categories
// -acceptance.md AC-439-10..20): the application identity row renders only on a
// positive APPLICATION category signal. Closes the defect where a SECURITY_
// RECOMMENDATION (account-level) row rendered "Application no longer available".
//
// `notifWithoutCategory` builds a fixture and then deletes the `category` key
// entirely (rather than setting it to undefined), to literally mirror EC-439-1: an
// older API response that omits the field, not merely a falsy value.
function notifWithoutCategory(overrides = {}) {
  const n = notif(overrides);
  delete n.category;
  return n;
}

describe("TC-439-29..41: notification category gate (ADR 0031 / AC-439-10..20)", () => {
  it("TC-439-29 · AC-439-13, THE DEFECT REGRESSION: a SECURITY_RECOMMENDATION (category ACCOUNT) row shows no identity row and no fallback label", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({
          id: "n-1",
          type: "SECURITY_RECOMMENDATION",
          category: "ACCOUNT",
          title: "Enable Two-Factor Authentication",
          message: "Protect your account with 2FA.",
          read: false,
          applicationId: null,
        }),
      ])
    );
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-co-logo-image")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-job-title")).not.toBeInTheDocument();
    expect(within(row).queryByText("Application no longer available")).not.toBeInTheDocument();
    expect(within(row).getByTestId("notification-icon-SECURITY_RECOMMENDATION")).toBeInTheDocument();
    expect(within(row).getByText("Enable Two-Factor Authentication")).toBeInTheDocument();
    expect(within(row).getByText("Protect your account with 2FA.")).toBeInTheDocument();
  });

  it("TC-439-30 · AC-439-14: a SYSTEM (category ACCOUNT) row shows no identity row and no fallback label, same as TC-439-29", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({
          id: "n-1",
          type: "SYSTEM",
          category: "ACCOUNT",
          title: "Platform maintenance",
          message: "We'll be down for maintenance tonight.",
          read: false,
          applicationId: null,
        }),
      ])
    );
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-job-title")).not.toBeInTheDocument();
    expect(within(row).queryByText("Application no longer available")).not.toBeInTheDocument();
    expect(within(row).getByText("Platform maintenance")).toBeInTheDocument();
  });

  it("TC-439-31 · AC-439-10 (regression guard): category APPLICATION with a resolved application renders the identity row unchanged", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({
          id: "n-1",
          type: "INTERVIEW_REMINDER",
          category: "APPLICATION",
          company: "Acme Corp",
          jobTitle: "Senior Backend Engineer",
        }),
      ])
    );
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).getByTestId("notification-row-co-logo")).toHaveAttribute("data-co", "Acme Corp");
    expect(within(row).getByTestId("notification-row-job-title")).toHaveTextContent("Senior Backend Engineer");
  });

  it("TC-439-32 · AC-439-11: category APPLICATION with an unresolvable application still shows the legitimate fallback", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({
          id: "n-1",
          type: "GHOSTED_ALERT",
          category: "APPLICATION",
          company: null,
          jobTitle: null,
        }),
      ])
    );
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(within(row).getByTestId("notification-row-job-title")).toHaveTextContent(
      "Application no longer available"
    );
  });

  it("TC-439-33 · AC-439-12: category APPLICATION with a null applicationId still attempts the identity row, falls back sensibly, throws no error, and does not affect a sibling row", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({
          id: "n-1",
          type: "CUSTOM_REMINDER",
          category: "APPLICATION",
          applicationId: null,
          company: null,
          jobTitle: null,
        }),
        notif({
          id: "n-2",
          type: "INTERVIEW_REMINDER",
          category: "APPLICATION",
          company: "Globex",
          jobTitle: "Staff Engineer",
        }),
      ])
    );
    renderScreen();

    const rows = await screen.findAllByTestId(/^notification-row-(read|unread)$/);
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(within(rows[0]).getByTestId("notification-row-job-title")).toHaveTextContent(
      "Application no longer available"
    );
    expect(within(rows[1]).getByTestId("notification-row-co-logo")).toHaveAttribute("data-co", "Globex");
    expect(within(rows[1]).getByTestId("notification-row-job-title")).toHaveTextContent("Staff Engineer");
  });

  it("TC-439-34 · AC-439-15: category ACCOUNT with an unexpectedly non-null applicationId still shows no identity row (gate keyed on category, not applicationId)", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({
          id: "n-1",
          type: "SECURITY_RECOMMENDATION",
          category: "ACCOUNT",
          applicationId: "app-should-be-ignored",
        }),
      ])
    );
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-job-title")).not.toBeInTheDocument();
    expect(within(row).queryByText("Application no longer available")).not.toBeInTheDocument();
  });

  it("TC-439-35 · AC-439-16: an unrecognised category value on an otherwise APPLICATION-shaped type falls back to ACCOUNT rendering (no identity row)", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({
          id: "n-1",
          type: "INTERVIEW_REMINDER",
          category: "SOME_FUTURE_VALUE",
          company: "Acme Corp",
          jobTitle: "Senior Backend Engineer",
        }),
      ])
    );
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-job-title")).not.toBeInTheDocument();
  });

  it("TC-439-36 · AC-439-17 (BR-439-10 documented interim state): a response that omits `category` entirely falls back to ACCOUNT rendering, even for an APPLICATION-shaped type", async () => {
    listNotifications.mockResolvedValue(
      page([
        notifWithoutCategory({
          id: "n-1",
          type: "INTERVIEW_REMINDER",
          company: "Acme Corp",
          jobTitle: "Senior Backend Engineer",
        }),
      ])
    );
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-job-title")).not.toBeInTheDocument();
  });

  it("TC-439-37 · AC-439-18 (EC-439-7, synthetic fixture only, JOB_POST is unreachable today): a JOB_POST category row renders identically to ACCOUNT, no identity row, no throw, no job-post-specific data required", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({
          id: "n-1",
          type: "APPLICATION_UPDATE",
          category: "JOB_POST",
          title: "New matching job post",
        }),
      ])
    );
    expect(() => renderScreen()).not.toThrow();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
    expect(within(row).queryByTestId("notification-row-job-title")).not.toBeInTheDocument();
    expect(within(row).getByText("New matching job post")).toBeInTheDocument();
  });

  it("TC-439-38 · AC-439-19: a mixed-category page renders each row per its own effective category, independently", async () => {
    listNotifications.mockResolvedValue(
      page([
        notif({
          id: "n-app",
          type: "GHOSTED_ALERT",
          category: "APPLICATION",
          title: "Application row",
          company: "Acme Corp",
          jobTitle: "Backend Engineer",
        }),
        notif({
          id: "n-account",
          type: "SECURITY_RECOMMENDATION",
          category: "ACCOUNT",
          title: "Account row",
          applicationId: null,
        }),
        notif({
          id: "n-jobpost",
          type: "APPLICATION_UPDATE",
          category: "JOB_POST",
          title: "Job post row",
          applicationId: null,
        }),
      ])
    );
    renderScreen();

    const rows = await screen.findAllByTestId(/^notification-row-(read|unread)$/);
    expect(rows).toHaveLength(3);

    const appRow = screen.getByText("Application row").closest('[data-testid^="notification-row-"]');
    const accountRow = screen.getByText("Account row").closest('[data-testid^="notification-row-"]');
    const jobPostRow = screen.getByText("Job post row").closest('[data-testid^="notification-row-"]');

    expect(within(appRow).getByTestId("notification-row-co-logo")).toBeInTheDocument();
    expect(within(accountRow).queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
    expect(within(accountRow).queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
    expect(within(jobPostRow).queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
    expect(within(jobPostRow).queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
  });

  it("TC-439-39 · AC-439-20 (list-wide defect closure): 'Application no longer available' never appears on an ACCOUNT-category row across pagination, but may still appear on an APPLICATION-category row", async () => {
    listNotifications.mockResolvedValueOnce(
      page(
        [
          notif({
            id: "n-p0-account",
            type: "SECURITY_RECOMMENDATION",
            category: "ACCOUNT",
            title: "Page 0 account row",
            applicationId: null,
          }),
        ],
        { page: 0, totalPages: 2, totalElements: 2 }
      )
    );
    const user = userEvent.setup();
    renderScreen();

    let row = await screen.findByTestId("notification-row-unread");
    expect(within(row).queryByText("Application no longer available")).not.toBeInTheDocument();

    listNotifications.mockResolvedValueOnce(
      page(
        [
          notif({
            id: "n-p1-app",
            type: "GHOSTED_ALERT",
            category: "APPLICATION",
            title: "Page 1 application row",
            company: null,
            jobTitle: null,
          }),
        ],
        { page: 1, totalPages: 2, totalElements: 2 }
      )
    );
    await user.click(screen.getByTestId("notifications-page-pager-next"));

    await waitFor(() => expect(screen.getByText("Page 1 application row")).toBeInTheDocument());
    row = screen.getByTestId("notification-row-unread");
    expect(within(row).getByText("Application no longer available")).toBeInTheDocument();
  });

  it("TC-439-40: a sparse notification (no title/message, unknown category) does not throw and does not crash the rest of the list", async () => {
    listNotifications.mockResolvedValue(
      page([
        { id: "n-sparse", type: "SOME_FUTURE_TYPE", read: false, createdAt: new Date().toISOString() },
        notif({ id: "n-normal", title: "Normal row" }),
      ])
    );
    expect(() => renderScreen()).not.toThrow();

    await screen.findByText("Normal row");
    const rows = screen.getAllByTestId(/^notification-row-(read|unread)$/);
    expect(rows).toHaveLength(2);
  });

  it("TC-439-41 · required fixture correction (not new behaviour): the shared notif() default now carries category: 'APPLICATION', so pre-existing identity-row assertions (NL-UI-01/02/03/06/07) keep proving the same thing they always proved", async () => {
    listNotifications.mockResolvedValue(
      page([notif({ id: "n-1", title: "Default fixture", company: "Acme Corp", jobTitle: "Backend Engineer" })])
    );
    renderScreen();

    const row = await screen.findByTestId("notification-row-unread");
    expect(within(row).getByTestId("notification-row-co-logo")).toHaveAttribute("data-co", "Acme Corp");
    expect(within(row).getByTestId("notification-row-job-title")).toHaveTextContent("Backend Engineer");
  });
});
