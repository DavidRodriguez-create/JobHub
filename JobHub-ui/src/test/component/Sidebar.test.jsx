/**
 * Component tests for Sidebar — story #206 / ticket #234 (AC-1: remove the redundant
 * sidebar bell) and follow-up ticket #237 (restore the unread count on the surviving
 * top-nav "Notifications" item).
 *
 * Cases (QAE spec, docs/qa/206-test-cases.md, Part B.1):
 *  - TC-206-F-04: no bell/dropdown/badge testid in the sidebar footer, authenticated.
 *  - TC-206-F-05: nav-item-notifications still present, ordered between Applications
 *    and Dashboard, clicking it calls onNav("notifications").
 *  - TC-206-F-07: logged-out sidebar shows no bell, nav item dimmed but still clickable.
 *  - TC-206-F-08: the bell never reappears regardless of any legacy prop being passed.
 *
 * Follow-up ticket #237 (AC-C1/C2/C7): the badge itself is rendered from a
 * `unreadCount` prop the App shell passes down (polling/refresh live in App.jsx,
 * covered by NotificationsNav.test.jsx). These cases only check the Sidebar's pure
 * rendering: N>0 shows the count (capped "99+"), 0/null/undefined hides it, and a
 * logged-out user never sees a badge regardless of the prop value.
 *
 * Strategy: render <Sidebar/> directly with minimal props (no App tree needed; AC-1.2's
 * full navigation round trip is already proven by TC-NOTIF-NAV-01 in NotificationsNav.test.jsx).
 */
import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";

import { Sidebar } from "../../components/ui.jsx";

const APP_COUNTS = { total: 0, interview: 0, applied: 0 };

function renderSidebar(props = {}) {
  return render(
    <Sidebar
      current="search"
      onNav={vi.fn()}
      appCounts={APP_COUNTS}
      savedCount={0}
      authed={false}
      account={null}
      mobileOpen={false}
      onClose={vi.fn()}
      isAdmin={false}
      onLogout={vi.fn()}
      onOpenApplication={vi.fn()}
      {...props}
    />
  );
}

describe("TC-206-F-04: no bell/dropdown/badge in the sidebar footer, authenticated", () => {
  it("renders no notification-bell, notification-dropdown, or notification-badge testid", () => {
    renderSidebar({ authed: true });

    expect(screen.queryByTestId("notification-bell")).not.toBeInTheDocument();
    expect(screen.queryByTestId("notification-dropdown")).not.toBeInTheDocument();
    expect(screen.queryByTestId("notification-badge")).not.toBeInTheDocument();
  });
});

describe("TC-206-F-05: the top-nav Notifications item remains, ordered and clickable", () => {
  it("is present, labelled 'Notifications', positioned between Applications and Dashboard, and calls onNav on click", async () => {
    const onNav = vi.fn();
    renderSidebar({ authed: true, current: "search", onNav });

    const navItem = screen.getByTestId("nav-item-notifications");
    expect(navItem).toHaveTextContent("Notifications");

    const allNavTestIds = Array.from(document.querySelectorAll('[data-testid^="nav-item-"]')).map((el) =>
      el.getAttribute("data-testid")
    );
    const appsIdx = allNavTestIds.indexOf("nav-item-applications");
    const notifIdx = allNavTestIds.indexOf("nav-item-notifications");
    const dashIdx = allNavTestIds.indexOf("nav-item-dashboard");
    expect(appsIdx).toBeGreaterThanOrEqual(0);
    expect(notifIdx).toBeGreaterThan(appsIdx);
    expect(dashIdx).toBeGreaterThan(notifIdx);

    const user = userEvent.setup();
    await user.click(navItem);
    expect(onNav).toHaveBeenCalledWith("notifications");
  });
});

describe("TC-206-F-07: logged-out sidebar shows no bell, nav item dimmed but still clickable", () => {
  it("renders no bell testids and a dimmed nav-item-notifications that still calls onNav", async () => {
    const onNav = vi.fn();
    renderSidebar({ authed: false, onNav });

    expect(screen.queryByTestId("notification-bell")).not.toBeInTheDocument();
    expect(screen.queryByTestId("notification-dropdown")).not.toBeInTheDocument();
    expect(screen.queryByTestId("notification-badge")).not.toBeInTheDocument();

    const navItem = screen.getByTestId("nav-item-notifications");
    expect(navItem).toBeInTheDocument();
    expect(navItem.style.opacity).toBe("0.5");

    const user = userEvent.setup();
    await user.click(navItem);
    expect(onNav).toHaveBeenCalledWith("notifications");
  });
});

describe("TC-206-F-08: the bell never reappears, removal is unconditional", () => {
  it("renders no bell testids for an authenticated user, and clicking the nav item still calls onNav", async () => {
    const onNav = vi.fn();
    renderSidebar({ authed: true, onNav });

    expect(screen.queryByTestId("notification-bell")).not.toBeInTheDocument();
    expect(screen.queryByTestId("notification-dropdown")).not.toBeInTheDocument();
    expect(screen.queryByTestId("notification-badge")).not.toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByTestId("nav-item-notifications"));
    expect(onNav).toHaveBeenCalledWith("notifications");
  });
});

// ─── Ticket #237: unread count badge on the top-nav Notifications item ───
describe("TC-206-C-01: N>0 unread shows a count badge equal to N", () => {
  it("renders the count span inside nav-item-notifications", () => {
    renderSidebar({ authed: true, unreadCount: 5 });

    const navItem = screen.getByTestId("nav-item-notifications");
    const count = navItem.querySelector(".count");
    expect(count).not.toBeNull();
    expect(count).toHaveTextContent("5");
  });
});

describe("TC-206-C-02: unread count over 99 is capped to the literal '99+'", () => {
  it("shows '99+' for 100 and for 250", () => {
    const { unmount } = renderSidebar({ authed: true, unreadCount: 100 });
    expect(screen.getByTestId("nav-item-notifications").querySelector(".count")).toHaveTextContent("99+");
    unmount();

    renderSidebar({ authed: true, unreadCount: 250 });
    expect(screen.getByTestId("nav-item-notifications").querySelector(".count")).toHaveTextContent("99+");
  });

  it("shows the exact number at the boundary of 99 (not capped)", () => {
    renderSidebar({ authed: true, unreadCount: 99 });
    expect(screen.getByTestId("nav-item-notifications").querySelector(".count")).toHaveTextContent("99");
  });
});

describe("TC-206-C-03: 0 unread hides the badge entirely (not '0')", () => {
  it("renders no .count span when unreadCount is 0", () => {
    renderSidebar({ authed: true, unreadCount: 0 });
    expect(screen.getByTestId("nav-item-notifications").querySelector(".count")).toBeNull();
  });

  it("renders no .count span when unreadCount is null/undefined (default)", () => {
    renderSidebar({ authed: true });
    expect(screen.getByTestId("nav-item-notifications").querySelector(".count")).toBeNull();
  });
});

describe("TC-206-C-04: logged-out users never see a count badge, regardless of the prop", () => {
  it("renders no .count span on the Notifications item when authed=false even if unreadCount>0", () => {
    renderSidebar({ authed: false, unreadCount: 7 });
    expect(screen.getByTestId("nav-item-notifications").querySelector(".count")).toBeNull();
  });
});
