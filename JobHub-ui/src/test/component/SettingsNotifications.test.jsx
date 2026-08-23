/**
 * Component tests for SettingsScreen -> Notifications section.
 * Story #78 / Ticket #87 baseline + Story #135 / Sub-issue #148 re-grouping.
 *
 * Cases retained from original file (adjusted for new layout):
 *  - AC-7 loading state (with updated copy matcher)
 *  - AC-8 optimistic toggle (inApp assertion dropped)
 *  - AC-9 GET/PUT error states (inApp assertion dropped)
 *  - TC-151..TC-156 interviewReminderEmail sub-toggle
 *  - TC-P1..TC-E3 SWR cache cases (inApp assertions dropped)
 *
 * New cases added for story #135 re-grouping (TC-01..TC-29):
 *  TC-01..TC-05  Layout + locked copy
 *  TC-06..TC-08  Master toggle derivation
 *  TC-09..TC-12  Master OFF behaviour
 *  TC-13         Master ON behaviour
 *  TC-14..TC-16  Independent nested saves
 *  TC-17..TC-18  Also-email-me disabled under Interview reminders OFF
 *  TC-19         Persistence / reload
 *  TC-20         All-off scenario
 *  TC-21..TC-23  Loading and error states
 *  TC-24..TC-25  PUT failure revert
 *  TC-26..TC-29  Accessibility assertions
 */
import React from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/notifications.js", () => ({
  getNotificationPreferences: vi.fn(),
  updateNotificationPreferences: vi.fn(),
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

import { getNotificationPreferences, updateNotificationPreferences } from "../../api/notifications.js";
import { ApiError } from "../../api/client.js";
import { SettingsScreen } from "../../screens/SavedSettings.jsx";

const ACCOUNT = { firstName: "Jo", lastName: "Smith", email: "jo@example.com" };

// Legacy DEFAULTS (original tests) kept for backward compat.
const DEFAULTS = {
  weeklyDigestEmail: true,
  inAppNotificationsEnabled: false,
  interviewReminders: true,
  ghostedAlert: true,
};

// PREFS_BASE from QAE spec for TC-01..TC-29.
const PREFS_BASE = {
  weeklyDigestEmail: true,
  inAppNotificationsEnabled: false,
  interviewReminders: true,
  interviewReminderEmail: true,
  ghostedAlert: false,
};

function renderSettings(props = {}) {
  return render(
    <SettingsScreen
      authed={true}
      account={ACCOUNT}
      onLogout={vi.fn()}
      onLogin={vi.fn()}
      openSearch={vi.fn()}
      {...props}
    />
  );
}

async function gotoNotifications(user) {
  await user.click(screen.getByText("Notifications"));
}

beforeEach(() => {
  vi.clearAllMocks();
});

// ---------------------------------------------------------------------------
// Original AC-7 tests (updated: remove inApp references, update copy matcher)
// ---------------------------------------------------------------------------

describe("AC-7: Settings loads preferences on mount", () => {
  it("calls getNotificationPreferences and shows a loading state before resolving", async () => {
    let resolvePrefs;
    getNotificationPreferences.mockImplementation(
      () => new Promise((resolve) => { resolvePrefs = resolve; })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    expect(getNotificationPreferences).toHaveBeenCalledTimes(1);

    // While loading: no switch with the new "Weekly news posts" name is shown
    expect(screen.queryByRole("switch", { name: /Weekly news posts/i })).not.toBeInTheDocument();
    expect(screen.getByTestId("notifications-loading")).toBeInTheDocument();

    resolvePrefs(DEFAULTS);
    await waitFor(() => expect(screen.queryByTestId("notifications-loading")).not.toBeInTheDocument());
  });

  // DELETED: "renders the four toggles set to the booleans returned by the API"
  // Reason: it queried toggle-inAppNotificationsEnabled which must not be rendered.

  // DELETED: "renders contract defaults for a first-time user (AC-1 mirrored)"
  // Reason: it queried toggle-inAppNotificationsEnabled which must not be rendered.
});

// ---------------------------------------------------------------------------
// Original AC-8 tests (updated: remove toggle-inAppNotificationsEnabled assertion)
// ---------------------------------------------------------------------------

describe("AC-8: toggle click persists with optimistic update", () => {
  it("flips the toggle immediately, calls PUT with only the changed field, and reconciles with the response", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: true,
      ghostedAlert: true,
    });
    updateNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: false,
      ghostedAlert: true,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const interview = await screen.findByTestId("toggle-interviewReminders");
    expect(interview).toHaveAttribute("aria-checked", "true");

    await user.click(interview);

    // optimistic flip happens immediately
    expect(interview).toHaveAttribute("aria-checked", "false");

    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));
    expect(updateNotificationPreferences).toHaveBeenCalledWith({ interviewReminders: false });

    // reconciled with 200 response (still false)
    await waitFor(() => expect(interview).toHaveAttribute("aria-checked", "false"));

    // other toggles unchanged (inApp row no longer rendered)
    expect(screen.getByTestId("toggle-weeklyDigestEmail")).toHaveAttribute("aria-checked", "true");
    expect(screen.getByTestId("toggle-ghostedAlert")).toHaveAttribute("aria-checked", "true");
  });
});

// ---------------------------------------------------------------------------
// Original AC-9 tests (updated: remove inApp assertions)
// ---------------------------------------------------------------------------

describe("AC-9 Scenario A: GET failure shows an error state", () => {
  it("shows an error/unavailable state instead of the toggles, and Account remains usable", async () => {
    getNotificationPreferences.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await waitFor(() => {
      expect(screen.getByTestId("notifications-error")).toBeInTheDocument();
    });

    // No toggle implying a saved value is rendered
    expect(screen.queryByTestId("toggle-weeklyDigestEmail")).not.toBeInTheDocument();
    expect(screen.queryByTestId("toggle-interviewReminders")).not.toBeInTheDocument();
    expect(screen.queryByTestId("toggle-ghostedAlert")).not.toBeInTheDocument();

    // Account section remains usable
    await user.click(screen.getByText("Account"));
    expect(screen.getByText(/your profile and how you sign in/i)).toBeInTheDocument();
  });
});

describe("AC-9 Scenario B: PUT failure reverts the toggle and shows an error", () => {
  it("reverts 'Weekly news posts' to its prior state and shows an error indication on PUT failure", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE); // weeklyDigestEmail: true
    updateNotificationPreferences.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const weekly = await screen.findByTestId("toggle-weeklyDigestEmail");
    expect(weekly).toHaveAttribute("aria-checked", "true");

    await user.click(weekly);

    // ends up reverted back to "on" after the failed PUT
    await waitFor(() => expect(weekly).toHaveAttribute("aria-checked", "true"));

    // error indication shown
    expect(screen.getByTestId("notifications-toggle-error")).toBeInTheDocument();

    // other toggles unaffected (inApp row no longer rendered)
    expect(screen.getByTestId("toggle-interviewReminders")).toHaveAttribute("aria-checked", "true");
  });

  it("triggers the app's existing session-invalid handling (onLogout) on a 401 PUT failure", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE);
    updateNotificationPreferences.mockRejectedValue(
      new ApiError(401, "Unauthorized", { error: "Unauthorized", message: "token expired" })
    );
    const onLogout = vi.fn();

    const user = userEvent.setup();
    renderSettings({ onLogout });
    await gotoNotifications(user);

    const ghost = await screen.findByTestId("toggle-ghostedAlert");
    await user.click(ghost);

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));

    // toggle reverted
    await waitFor(() => expect(ghost).toHaveAttribute("aria-checked", "false"));
  });
});

// ---------------------------------------------------------------------------
// US4 Interview Reminders -- interviewReminderEmail sub-toggle (TC-151..TC-156)
// ---------------------------------------------------------------------------

const DEFAULTS_US4 = {
  weeklyDigestEmail: true,
  inAppNotificationsEnabled: false,
  interviewReminders: true,
  interviewReminderEmail: true,
  ghostedAlert: true,
};

describe("TC-151: renders interviewReminderEmail sub-toggle with API value", () => {
  it("shows toggle-interviewReminderEmail with aria-checked matching the API response", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...DEFAULTS_US4,
      interviewReminders: true,
      interviewReminderEmail: false,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const emailToggle = await screen.findByTestId("toggle-interviewReminderEmail");
    const masterToggle = screen.getByTestId("toggle-interviewReminders");

    expect(emailToggle).toHaveAttribute("aria-checked", "false");
    expect(masterToggle).toHaveAttribute("aria-checked", "true");
  });
});

describe("TC-152: interviewReminderEmail sub-toggle defaults to true for first-time user", () => {
  it("shows toggle-interviewReminderEmail as checked when API returns default true", async () => {
    getNotificationPreferences.mockResolvedValue(DEFAULTS_US4);

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const emailToggle = await screen.findByTestId("toggle-interviewReminderEmail");
    expect(emailToggle).toHaveAttribute("aria-checked", "true");
  });
});

describe("TC-153: toggling interviewReminderEmail persists with optimistic update", () => {
  it("flips immediately, calls PUT with only the changed field, and reconciles", async () => {
    getNotificationPreferences.mockResolvedValue(DEFAULTS_US4); // interviewReminderEmail: true
    updateNotificationPreferences.mockResolvedValue({
      ...DEFAULTS_US4,
      interviewReminderEmail: false,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const emailToggle = await screen.findByTestId("toggle-interviewReminderEmail");
    expect(emailToggle).toHaveAttribute("aria-checked", "true");

    await user.click(emailToggle);

    // optimistic flip
    expect(emailToggle).toHaveAttribute("aria-checked", "false");

    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));
    expect(updateNotificationPreferences).toHaveBeenCalledWith({ interviewReminderEmail: false });

    // reconciled with response (still false)
    await waitFor(() => expect(emailToggle).toHaveAttribute("aria-checked", "false"));

    // master toggle unaffected
    expect(screen.getByTestId("toggle-interviewReminders")).toHaveAttribute("aria-checked", "true");
    expect(screen.getByTestId("toggle-weeklyDigestEmail")).toHaveAttribute("aria-checked", "true");
  });
});

describe("TC-154: interviewReminderEmail sub-toggle is disabled/greyed when master is off", () => {
  it("shows toggle-interviewReminderEmail with aria-disabled when interviewReminders is false", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...DEFAULTS_US4,
      interviewReminders: false,
      interviewReminderEmail: true,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    // master is off
    const masterToggle = await screen.findByTestId("toggle-interviewReminders");
    expect(masterToggle).toHaveAttribute("aria-checked", "false");

    // sub-toggle is visible (not hidden) but disabled/inert
    const emailToggle = screen.getByTestId("toggle-interviewReminderEmail");
    expect(emailToggle).toBeInTheDocument();
    expect(emailToggle).toHaveAttribute("aria-disabled", "true");
  });
});

describe("TC-155: PUT failure on interviewReminderEmail toggle reverts and shows error", () => {
  it("reverts the toggle and shows the toggle error indicator on a 500 PUT failure", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...DEFAULTS_US4,
      interviewReminders: true,
      interviewReminderEmail: true,
    });
    updateNotificationPreferences.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const emailToggle = await screen.findByTestId("toggle-interviewReminderEmail");
    expect(emailToggle).toHaveAttribute("aria-checked", "true");

    await user.click(emailToggle);

    // reverted after failure
    await waitFor(() => expect(emailToggle).toHaveAttribute("aria-checked", "true"));

    // error banner shown
    expect(screen.getByTestId("notifications-toggle-error")).toBeInTheDocument();

    // other toggles unaffected
    expect(screen.getByTestId("toggle-interviewReminders")).toHaveAttribute("aria-checked", "true");
    expect(screen.getByTestId("toggle-weeklyDigestEmail")).toHaveAttribute("aria-checked", "true");
  });
});

describe("TC-156: interviewReminderEmail sub-toggle label references email and is inside interview-reminders section", () => {
  it("renders a switch accessible by name containing 'email' inside data-testid=interview-reminders-section", async () => {
    getNotificationPreferences.mockResolvedValue(DEFAULTS_US4);

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    // The container for the master + sub toggle
    const section = await screen.findByTestId("interview-reminders-section");
    expect(section).toBeInTheDocument();

    // sub-toggle is inside the section
    const emailToggle = within(section).getByTestId("toggle-interviewReminderEmail");
    expect(emailToggle).toBeInTheDocument();

    // accessible name or adjacent label text references "email"
    const emailSwitch = within(section).getByRole("switch", { name: /email/i });
    expect(emailSwitch).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Story #136 SWR cache tests (updated: drop inApp references)
// ---------------------------------------------------------------------------

describe("TC-P1/TC-C1: warm tab-click serves cache without network call", () => {
  it("TC-P1/C1: second visit to Notifications tab reads from SWR cache; GET called only once", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE);

    const user = userEvent.setup();
    renderSettings();

    // First visit: click Notifications, data loads
    await gotoNotifications(user);
    await screen.findByTestId("toggle-weeklyDigestEmail");
    expect(getNotificationPreferences).toHaveBeenCalledTimes(1);

    // Navigate away to Account
    await user.click(screen.getByText("Account"));
    expect(screen.queryByTestId("toggle-weeklyDigestEmail")).not.toBeInTheDocument();

    // Second visit: toggles appear immediately from cache, no loading skeleton visible
    await gotoNotifications(user);

    // Cache hit: no loading state on second visit
    expect(screen.queryByTestId("notifications-loading")).not.toBeInTheDocument();

    // Toggles appear without waiting (synchronous from cache)
    expect(screen.getByTestId("toggle-weeklyDigestEmail")).toBeInTheDocument();
    expect(screen.getByTestId("toggle-interviewReminders")).toBeInTheDocument();
    expect(screen.getByTestId("toggle-ghostedAlert")).toBeInTheDocument();

    // GET was called exactly once (not twice)
    expect(getNotificationPreferences).toHaveBeenCalledTimes(1);
  });
});

describe("TC-P2: cold first-hit calls GET once and renders toggles after resolve", () => {
  it("TC-P2: cold path issues exactly one GET and renders all toggles after it resolves", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let resolvePrefs;
    getNotificationPreferences.mockImplementation(
      () => new Promise((resolve) => { resolvePrefs = resolve; })
    );

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime.bind(vi) });
    renderSettings();
    await gotoNotifications(user);

    expect(getNotificationPreferences).toHaveBeenCalledTimes(1);

    // Resolve after simulated 200ms delay
    await vi.advanceTimersByTimeAsync(200);
    resolvePrefs(PREFS_BASE);

    await waitFor(() => expect(screen.queryByTestId("notifications-loading")).not.toBeInTheDocument());

    // Use waitFor for nestedDisplay-driven toggles to allow the sync effect to propagate.
    await waitFor(() => expect(screen.getByTestId("toggle-weeklyDigestEmail")).toHaveAttribute("aria-checked", "true"));
    await waitFor(() => expect(screen.getByTestId("toggle-interviewReminders")).toHaveAttribute("aria-checked", "true"));
    expect(screen.getByTestId("toggle-ghostedAlert")).toHaveAttribute("aria-checked", "false");

    vi.useRealTimers();
  });
});

describe("TC-S1: loading skeleton visible on cold load; Account nav stays usable", () => {
  it("TC-S1: shows notifications-loading while fetch is in-flight; Account nav still reachable", async () => {
    getNotificationPreferences.mockImplementation(
      () => new Promise(() => {})
    );

    const user = userEvent.setup();
    renderSettings();

    await gotoNotifications(user);

    expect(screen.getByTestId("notifications-loading")).toBeInTheDocument();

    // None of the live toggles are present while loading
    expect(screen.queryByTestId("toggle-weeklyDigestEmail")).not.toBeInTheDocument();
    expect(screen.queryByTestId("toggle-interviewReminders")).not.toBeInTheDocument();
    expect(screen.queryByTestId("toggle-ghostedAlert")).not.toBeInTheDocument();

    // Account nav link remains present and clickable during in-flight period
    const accountLink = screen.getByText("Account");
    expect(accountLink).toBeInTheDocument();
    await user.click(accountLink);
    expect(screen.getByText(/your profile and how you sign in/i)).toBeInTheDocument();
  });
});

describe("TC-S2: no default-flash before server values arrive", () => {
  it("TC-S2: no contract-default flash; first visible toggle values are the server values", async () => {
    const nonDefaultPrefs = {
      weeklyDigestEmail: false,
      inAppNotificationsEnabled: false,
      interviewReminders: false,
      ghostedAlert: false,
    };

    let resolvePrefs;
    getNotificationPreferences.mockImplementation(
      () => new Promise((resolve) => { resolvePrefs = resolve; })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    expect(screen.getByTestId("notifications-loading")).toBeInTheDocument();

    await import("@testing-library/react").then(({ act }) =>
      act(() => { resolvePrefs(nonDefaultPrefs); })
    );

    await waitFor(() => expect(screen.queryByTestId("notifications-loading")).not.toBeInTheDocument());

    const weekly = screen.getByTestId("toggle-weeklyDigestEmail");
    expect(weekly).toHaveAttribute("aria-checked", "false");

    const interview = screen.getByTestId("toggle-interviewReminders");
    expect(interview).toHaveAttribute("aria-checked", "false");

    const ghosted = screen.getByTestId("toggle-ghostedAlert");
    expect(ghosted).toHaveAttribute("aria-checked", "false");
  });
});

describe("TC-C2: successful PUT updates the SWR cache; subsequent visit shows updated values", () => {
  it("TC-C2: after PUT succeeds, navigating away and back shows PUT response values without a GET", async () => {
    getNotificationPreferences.mockResolvedValue({ ...PREFS_BASE, ghostedAlert: true });
    updateNotificationPreferences.mockResolvedValue({ ...PREFS_BASE, ghostedAlert: false });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const ghost = await screen.findByTestId("toggle-ghostedAlert");
    expect(ghost).toHaveAttribute("aria-checked", "true");

    await user.click(ghost);
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(ghost).toHaveAttribute("aria-checked", "false"));

    await user.click(screen.getByText("Account"));

    await gotoNotifications(user);

    expect(getNotificationPreferences).toHaveBeenCalledTimes(1);

    const ghostAfter = screen.getByTestId("toggle-ghostedAlert");
    expect(ghostAfter).toHaveAttribute("aria-checked", "false");

    expect(screen.getByTestId("toggle-weeklyDigestEmail")).toHaveAttribute("aria-checked", "true");
    expect(screen.getByTestId("toggle-interviewReminders")).toHaveAttribute("aria-checked", "true");
  });
});

describe("TC-T1: single-field optimistic save for ghostedAlert", () => {
  it("TC-T1: clicking ghostedAlert toggle optimistically flips it; PUT called with only that field", async () => {
    getNotificationPreferences.mockResolvedValue({ ...PREFS_BASE, ghostedAlert: true });
    updateNotificationPreferences.mockResolvedValue({ ...PREFS_BASE, ghostedAlert: false });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const ghost = await screen.findByTestId("toggle-ghostedAlert");
    expect(ghost).toHaveAttribute("aria-checked", "true");

    await user.click(ghost);

    expect(ghost).toHaveAttribute("aria-checked", "false");

    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));
    expect(updateNotificationPreferences).toHaveBeenCalledWith({ ghostedAlert: false });

    await waitFor(() => expect(ghost).toHaveAttribute("aria-checked", "false"));

    // Other toggles unchanged (inApp row no longer rendered)
    expect(screen.getByTestId("toggle-weeklyDigestEmail")).toHaveAttribute("aria-checked", "true");
    expect(screen.getByTestId("toggle-interviewReminders")).toHaveAttribute("aria-checked", "true");
  });
});

describe("TC-T2: server truth reconciliation on PUT success", () => {
  it("TC-T2: if PUT response changes other fields, all local states update to match server", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE);
    updateNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      ghostedAlert: true,
      weeklyDigestEmail: false,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const weekly = await screen.findByTestId("toggle-weeklyDigestEmail");
    expect(weekly).toHaveAttribute("aria-checked", "true");

    const ghost = screen.getByTestId("toggle-ghostedAlert");
    await user.click(ghost);

    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));

    await waitFor(() => expect(weekly).toHaveAttribute("aria-checked", "false"));
    await waitFor(() => expect(ghost).toHaveAttribute("aria-checked", "true"));
  });
});

describe("TC-T3: toggle reverts on PUT failure; TC-T3 / AC-9 Scenario B", () => {
  it("TC-T3: weeklyDigestEmail reverts to true after 500 PUT; error text matches spec wording", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE); // weeklyDigestEmail: true
    updateNotificationPreferences.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const weekly = await screen.findByTestId("toggle-weeklyDigestEmail");
    expect(weekly).toHaveAttribute("aria-checked", "true");

    await user.click(weekly);

    await waitFor(() => expect(weekly).toHaveAttribute("aria-checked", "true"));

    const errorEl = screen.getByTestId("notifications-toggle-error");
    expect(errorEl).toBeInTheDocument();
    expect(errorEl).toHaveAttribute("role", "alert");
    expect(errorEl.textContent).toMatch(/couldn't save your notification preference/i);

    // Other toggles remain at last-confirmed values (inApp row no longer rendered)
    expect(screen.getByTestId("toggle-interviewReminders")).toHaveAttribute("aria-checked", "true");
    expect(screen.getByTestId("toggle-ghostedAlert")).toHaveAttribute("aria-checked", "false");
  });
});

describe("TC-T4: 401 PUT triggers onLogout; toggle reverts", () => {
  it("TC-T4: 401 from PUT fires onLogout and reverts the toggled field", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE);
    updateNotificationPreferences.mockRejectedValue(
      new ApiError(401, "Unauthorized", { error: "Unauthorized", message: "token expired" })
    );
    const onLogout = vi.fn();

    const user = userEvent.setup();
    renderSettings({ onLogout });
    await gotoNotifications(user);

    const ghost = await screen.findByTestId("toggle-ghostedAlert");
    await user.click(ghost);

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(ghost).toHaveAttribute("aria-checked", "false"));
  });
});

describe("TC-T5a: sub-toggle disabled when master is off; clicking fires no PUT", () => {
  it("TC-T5a: toggle-interviewReminderEmail is aria-disabled when master is off; click fires no PUT", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...DEFAULTS_US4,
      interviewReminders: false,
      interviewReminderEmail: true,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const subToggle = await screen.findByTestId("toggle-interviewReminderEmail");
    expect(subToggle).toHaveAttribute("aria-disabled", "true");

    await user.click(subToggle);
    expect(updateNotificationPreferences).not.toHaveBeenCalled();
  });
});

describe("TC-T5b: sub-toggle becomes enabled when master is flipped on via PUT", () => {
  it("TC-T5b: after master toggles on via PUT, sub-toggle aria-disabled transitions to false", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...DEFAULTS_US4,
      interviewReminders: false,
      interviewReminderEmail: true,
    });
    updateNotificationPreferences.mockResolvedValue({
      ...DEFAULTS_US4,
      interviewReminders: true,
      interviewReminderEmail: true,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const master = await screen.findByTestId("toggle-interviewReminders");
    const subToggle = screen.getByTestId("toggle-interviewReminderEmail");

    expect(subToggle).toHaveAttribute("aria-disabled", "true");

    await user.click(master);
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));

    await waitFor(() => expect(subToggle).not.toHaveAttribute("aria-disabled", "true"));
  });
});

describe("TC-E1: GET 5xx shows error banner with correct attributes and text", () => {
  it("TC-E1: error banner has role=alert and spec error text; no toggles rendered; Account nav usable", async () => {
    getNotificationPreferences.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const errorEl = await screen.findByTestId("notifications-error");
    expect(errorEl).toBeInTheDocument();
    expect(errorEl).toHaveAttribute("role", "alert");
    expect(errorEl.textContent).toMatch(
      /couldn't load your notification preferences\. please try again later\./i
    );

    expect(screen.queryByTestId("toggle-weeklyDigestEmail")).not.toBeInTheDocument();
    expect(screen.queryByTestId("toggle-interviewReminders")).not.toBeInTheDocument();
    expect(screen.queryByTestId("toggle-ghostedAlert")).not.toBeInTheDocument();

    await user.click(screen.getByText("Account"));
    expect(screen.getByText(/your profile and how you sign in/i)).toBeInTheDocument();
  });
});

describe("TC-E2: failed GET not cached; retry on return issues fresh GET", () => {
  it("TC-E2: error response is not cached; navigating back triggers a new GET that can succeed", async () => {
    getNotificationPreferences
      .mockRejectedValueOnce(
        new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
      )
      .mockResolvedValueOnce(PREFS_BASE);

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await screen.findByTestId("notifications-error");

    await user.click(screen.getByText("Account"));

    await gotoNotifications(user);

    await waitFor(() => expect(screen.queryByTestId("notifications-error")).not.toBeInTheDocument());
    await waitFor(() => expect(screen.getByTestId("toggle-weeklyDigestEmail")).toBeInTheDocument());

    expect(getNotificationPreferences).toHaveBeenCalledTimes(2);
  });
});

describe("TC-E3: unauthenticated user never reaches preferences fetch", () => {
  it("TC-E3: when authed=false, navigating to Notifications does not call getNotificationPreferences", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE);

    const user = userEvent.setup();
    render(
      <SettingsScreen
        authed={false}
        account={null}
        onLogout={vi.fn()}
        onLogin={vi.fn()}
        openSearch={vi.fn()}
      />
    );

    await user.click(screen.getByText("Notifications"));

    await waitFor(() => expect(getNotificationPreferences).not.toHaveBeenCalled());
  });
});

// ---------------------------------------------------------------------------
// TC-01..TC-29: Story #135 re-grouping tests
// ---------------------------------------------------------------------------

describe("TC-01 | AC-01: Layout: exactly two top-level groups with locked headings", () => {
  it("renders Notifications heading, sub-heading, Weekly news posts switch, and master Alerts and reminders switch", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE);

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await screen.findByRole("switch", { name: /Weekly news posts/i });

    expect(screen.getByRole("heading", { name: /^Notifications$/i })).toBeInTheDocument();
    expect(screen.getByText(/What we tell you, and how\./i)).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /Weekly news posts/i })).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /Alerts and reminders/i })).toBeInTheDocument();
    expect(screen.queryByText(/Weekly digest email/i)).not.toBeInTheDocument();
  });
});

describe("TC-02 | AC-01: Layout: locked helper text for 'Weekly news posts'", () => {
  it("renders the exact locked helper copy for Weekly news posts", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE);

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await screen.findByRole("switch", { name: /Weekly news posts/i });

    expect(
      screen.getByText(/A Monday summary of new jobs that match your saved filters\./i)
    ).toBeInTheDocument();
  });
});

describe("TC-03 | AC-01: Layout: 'Alerts and reminders' master helper text present", () => {
  it("renders the exact locked helper copy for Alerts and reminders master toggle", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE);

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await screen.findByRole("switch", { name: /Alerts and reminders/i });

    expect(
      screen.getByText(/Turn off to silence all alerts and reminders\./i)
    ).toBeInTheDocument();
  });
});

describe("TC-04 | AC-01: Layout: three nested toggles with locked copy + helper text", () => {
  it("renders all three nested toggles with exact locked labels and helper text", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE);

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await screen.findByRole("switch", { name: "Interview reminders" });

    expect(screen.getByRole("switch", { name: "Interview reminders" })).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /Ghosted alert/i })).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /Also email me for interview reminders/i })).toBeInTheDocument();
    expect(
      screen.getByText(/A notification 24 hours and 1 hour before each scheduled event\./i)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/A nudge when an application has had no activity for 14 days\./i)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Send a reminder email before interviews\. Does not affect ghosted-alert emails/i)
    ).toBeInTheDocument();
  });
});

describe("TC-05 | AC-01: 'Browser notifications' row is absent", () => {
  it("does not render inAppNotificationsEnabled toggle or Browser notifications text", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE);

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await screen.findByRole("switch", { name: /Weekly news posts/i });

    expect(screen.queryByRole("switch", { name: /Browser notifications/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/Browser notifications/i)).not.toBeInTheDocument();
    expect(
      document.querySelector("[data-testid='toggle-inAppNotificationsEnabled']")
    ).not.toBeInTheDocument();
  });
});

describe("TC-06 | AC-02: Master derives ON when interviewReminders=true, ghostedAlert=false", () => {
  it("master Alerts and reminders is aria-checked=true when interviewReminders=true and ghostedAlert=false", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: true,
      ghostedAlert: false,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const master = await screen.findByRole("switch", { name: /Alerts and reminders/i });
    expect(master).toHaveAttribute("aria-checked", "true");
  });
});

describe("TC-07 | AC-02: Master derives OFF when interviewReminders=false, ghostedAlert=false", () => {
  it("master Alerts and reminders is aria-checked=false when both nested flags are false", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: false,
      ghostedAlert: false,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const master = await screen.findByRole("switch", { name: /Alerts and reminders/i });
    expect(master).toHaveAttribute("aria-checked", "false");
  });
});

describe("TC-08 | AC-02: Master derives ON when interviewReminders=false, ghostedAlert=true", () => {
  it("master Alerts and reminders is aria-checked=true when ghostedAlert=true and interviewReminders=false", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: false,
      ghostedAlert: true,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const master = await screen.findByRole("switch", { name: /Alerts and reminders/i });
    expect(master).toHaveAttribute("aria-checked", "true");
  });
});

describe("TC-09 | AC-03: Master OFF: PUT body contains both nested flags false", () => {
  it("clicking master OFF sends PUT with interviewReminders: false and ghostedAlert: false only", async () => {
    getNotificationPreferences.mockResolvedValue({
      weeklyDigestEmail: true,
      interviewReminders: true,
      ghostedAlert: true,
      interviewReminderEmail: true,
      inAppNotificationsEnabled: false,
    });
    updateNotificationPreferences.mockResolvedValue({
      weeklyDigestEmail: true,
      interviewReminders: false,
      ghostedAlert: false,
      interviewReminderEmail: true,
      inAppNotificationsEnabled: false,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const master = await screen.findByRole("switch", { name: /Alerts and reminders/i });
    await user.click(master);

    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));

    const callArg = updateNotificationPreferences.mock.calls[0][0];
    expect(callArg).toMatchObject({ interviewReminders: false, ghostedAlert: false });
    expect(callArg).not.toHaveProperty("weeklyDigestEmail");
    expect(callArg).not.toHaveProperty("inAppNotificationsEnabled");
  });
});

describe("TC-10 | AC-03: Master OFF: nested toggles become aria-disabled", () => {
  it("after master is switched OFF all three nested toggles have aria-disabled=true", async () => {
    getNotificationPreferences.mockResolvedValue({
      weeklyDigestEmail: true,
      interviewReminders: true,
      ghostedAlert: true,
      interviewReminderEmail: true,
      inAppNotificationsEnabled: false,
    });
    updateNotificationPreferences.mockResolvedValue({
      weeklyDigestEmail: true,
      interviewReminders: false,
      ghostedAlert: false,
      interviewReminderEmail: true,
      inAppNotificationsEnabled: false,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const master = await screen.findByRole("switch", { name: /Alerts and reminders/i });
    await user.click(master);
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));

    await waitFor(() => {
      expect(screen.getByRole("switch", { name: "Interview reminders" })).toHaveAttribute("aria-disabled", "true");
    });
    expect(screen.getByRole("switch", { name: /Ghosted alert/i })).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("switch", { name: /Also email me for interview reminders/i })).toHaveAttribute("aria-disabled", "true");
  });
});

describe("TC-11 | AC-03: Master OFF: nested toggle values preserved visually", () => {
  it("after master-OFF PUT resolves, nested toggles show pre-OFF values (not reset to false)", async () => {
    // Initial: interviewReminders=true, ghostedAlert=false
    getNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: true,
      ghostedAlert: false,
      interviewReminderEmail: true,
    });
    // Master-OFF PUT response: server writes both to false, leaves interviewReminderEmail untouched
    updateNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: false,
      ghostedAlert: false,
      interviewReminderEmail: true,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const master = await screen.findByRole("switch", { name: /Alerts and reminders/i });
    await user.click(master);
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));

    // Interview reminders pre-OFF value was true; display should still show true (disabled)
    await waitFor(() => {
      expect(screen.getByRole("switch", { name: "Interview reminders" })).toHaveAttribute("aria-checked", "true");
    });
    // Ghosted alert pre-OFF value was false; display should still show false (disabled)
    expect(screen.getByRole("switch", { name: /Ghosted alert/i })).toHaveAttribute("aria-checked", "false");
  });
});

describe("TC-12 | AC-03: Master OFF: clicking a disabled nested toggle has no effect", () => {
  it("clicking Interview reminders when master is OFF does not call PUT", async () => {
    getNotificationPreferences.mockResolvedValue({
      weeklyDigestEmail: true,
      interviewReminders: true,
      ghostedAlert: true,
      interviewReminderEmail: true,
      inAppNotificationsEnabled: false,
    });
    updateNotificationPreferences.mockResolvedValue({
      weeklyDigestEmail: true,
      interviewReminders: false,
      ghostedAlert: false,
      interviewReminderEmail: true,
      inAppNotificationsEnabled: false,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const master = await screen.findByRole("switch", { name: /Alerts and reminders/i });
    await user.click(master);
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));

    const interviewToggle = screen.getByRole("switch", { name: "Interview reminders" });
    const prevChecked = interviewToggle.getAttribute("aria-checked");

    await user.click(interviewToggle);

    // Still only 1 PUT call total
    expect(updateNotificationPreferences).toHaveBeenCalledTimes(1);
    // aria-checked unchanged
    expect(interviewToggle).toHaveAttribute("aria-checked", prevChecked);
  });
});

describe("TC-13 | AC-04: Master ON: PUT body sets both flags true; nested become interactive", () => {
  it("clicking master OFF then ON sends PUT with both true; nested lose aria-disabled", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: true,
      ghostedAlert: false,
      interviewReminderEmail: true,
    });
    updateNotificationPreferences
      .mockResolvedValueOnce({
        ...PREFS_BASE,
        interviewReminders: false,
        ghostedAlert: false,
        interviewReminderEmail: true,
      })
      .mockResolvedValueOnce({
        ...PREFS_BASE,
        interviewReminders: true,
        ghostedAlert: true,
        interviewReminderEmail: true,
      });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const master = await screen.findByRole("switch", { name: /Alerts and reminders/i });

    // First click: master OFF
    await user.click(master);
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));

    // Second click: master ON
    await user.click(master);
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(2));

    const secondCallArg = updateNotificationPreferences.mock.calls[1][0];
    expect(secondCallArg).toMatchObject({ interviewReminders: true, ghostedAlert: true });

    await waitFor(() => {
      expect(screen.getByRole("switch", { name: "Interview reminders" })).toHaveAttribute("aria-checked", "true");
    });
    expect(screen.getByRole("switch", { name: /Ghosted alert/i })).toHaveAttribute("aria-checked", "true");

    // Nested no longer disabled
    expect(screen.getByRole("switch", { name: "Interview reminders" })).not.toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("switch", { name: /Ghosted alert/i })).not.toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("switch", { name: /Also email me for interview reminders/i })).not.toHaveAttribute("aria-disabled", "true");
  });
});

describe("TC-14 | AC-05: Nested 'Interview reminders' saves independently when master is ON", () => {
  it("clicking Interview reminders sends PUT with only interviewReminders; others unchanged", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: true,
      ghostedAlert: true,
      interviewReminderEmail: true,
    });
    updateNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: false,
      ghostedAlert: true,
      interviewReminderEmail: true,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const interviewToggle = await screen.findByRole("switch", { name: "Interview reminders" });
    await user.click(interviewToggle);

    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));
    expect(updateNotificationPreferences).toHaveBeenCalledWith({ interviewReminders: false });

    await waitFor(() => expect(interviewToggle).toHaveAttribute("aria-checked", "false"));
    expect(screen.getByRole("switch", { name: /Ghosted alert/i })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("switch", { name: /Also email me for interview reminders/i })).toHaveAttribute("aria-checked", "true");
  });
});

describe("TC-15 | AC-05: Nested 'Ghosted alert' saves independently when master is ON", () => {
  it("clicking Ghosted alert sends PUT with only ghostedAlert; others unchanged", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: true,
      ghostedAlert: false,
      interviewReminderEmail: true,
    });
    updateNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: true,
      ghostedAlert: true,
      interviewReminderEmail: true,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const ghostToggle = await screen.findByRole("switch", { name: /Ghosted alert/i });
    await user.click(ghostToggle);

    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));
    expect(updateNotificationPreferences).toHaveBeenCalledWith({ ghostedAlert: true });

    await waitFor(() => expect(ghostToggle).toHaveAttribute("aria-checked", "true"));
    expect(screen.getByRole("switch", { name: "Interview reminders" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("switch", { name: /Also email me for interview reminders/i })).toHaveAttribute("aria-checked", "true");
  });
});

describe("TC-16 | AC-05: 'Also email me' saves independently when master AND Interview reminders are ON", () => {
  it("clicking Also email me sends PUT with only interviewReminderEmail; others unchanged", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: true,
      ghostedAlert: true,
      interviewReminderEmail: true,
    });
    updateNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: true,
      ghostedAlert: true,
      interviewReminderEmail: false,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const alsoEmail = await screen.findByRole("switch", { name: /Also email me for interview reminders/i });
    await user.click(alsoEmail);

    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));
    expect(updateNotificationPreferences).toHaveBeenCalledWith({ interviewReminderEmail: false });

    await waitFor(() => expect(alsoEmail).toHaveAttribute("aria-checked", "false"));
    expect(screen.getByRole("switch", { name: "Interview reminders" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("switch", { name: /Ghosted alert/i })).toHaveAttribute("aria-checked", "true");
  });
});

describe("TC-17 | AC-06: 'Also email me' is aria-disabled when Interview reminders is OFF (master ON)", () => {
  it("Also email me is disabled when Interview reminders=false even though master is ON", async () => {
    // ghostedAlert=true makes master ON
    getNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: false,
      ghostedAlert: true,
      interviewReminderEmail: true,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const master = await screen.findByRole("switch", { name: /Alerts and reminders/i });
    expect(master).toHaveAttribute("aria-checked", "true");

    const interviewToggle = screen.getByRole("switch", { name: "Interview reminders" });
    expect(interviewToggle).toHaveAttribute("aria-checked", "false");

    const alsoEmail = screen.getByRole("switch", { name: /Also email me for interview reminders/i });
    expect(alsoEmail).toHaveAttribute("aria-disabled", "true");

    // Clicking the disabled toggle must not call PUT
    await user.click(alsoEmail);
    expect(updateNotificationPreferences).not.toHaveBeenCalled();
  });
});

describe("TC-18 | AC-06: 'Also email me' becomes interactive when Interview reminders is switched ON", () => {
  it("Also email me loses aria-disabled after Interview reminders is switched ON", async () => {
    getNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: false,
      ghostedAlert: true,
      interviewReminderEmail: true,
    });
    updateNotificationPreferences.mockResolvedValue({
      ...PREFS_BASE,
      interviewReminders: true,
      ghostedAlert: true,
      interviewReminderEmail: true,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await screen.findByRole("switch", { name: /Alerts and reminders/i });
    const alsoEmail = screen.getByRole("switch", { name: /Also email me for interview reminders/i });
    expect(alsoEmail).toHaveAttribute("aria-disabled", "true");

    const interviewToggle = screen.getByRole("switch", { name: "Interview reminders" });
    await user.click(interviewToggle);
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));

    await waitFor(() => {
      expect(alsoEmail).not.toHaveAttribute("aria-disabled", "true");
    });
  });
});

describe("TC-19 | AC-07: Persistence: reload shows same state", () => {
  it("re-mounting and navigating back to Notifications shows same server values", async () => {
    const serverPrefs = {
      weeklyDigestEmail: true,
      interviewReminders: false,
      ghostedAlert: true,
      interviewReminderEmail: false,
      inAppNotificationsEnabled: false,
    };
    getNotificationPreferences.mockResolvedValue(serverPrefs);

    const user = userEvent.setup();
    const { unmount } = renderSettings();
    await gotoNotifications(user);
    await screen.findByRole("switch", { name: /Weekly news posts/i });

    // Simulate navigate away + remount
    unmount();
    vi.clearAllMocks();
    getNotificationPreferences.mockResolvedValue(serverPrefs);

    renderSettings();
    await gotoNotifications(user);
    await screen.findByRole("switch", { name: /Weekly news posts/i });

    expect(screen.getByRole("switch", { name: /Weekly news posts/i })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("switch", { name: /Alerts and reminders/i })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("switch", { name: "Interview reminders" })).toHaveAttribute("aria-checked", "false");
    expect(screen.getByRole("switch", { name: /Ghosted alert/i })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("switch", { name: /Also email me for interview reminders/i })).toHaveAttribute("aria-checked", "false");
  });
});

describe("TC-20 | AC-08: All-off: no extra PUT triggered by disabled-toggle click", () => {
  it("clicking disabled Also email me after master-OFF does not trigger extra PUT; total calls = 2", async () => {
    getNotificationPreferences.mockResolvedValue({
      weeklyDigestEmail: true,
      interviewReminders: true,
      ghostedAlert: true,
      interviewReminderEmail: true,
      inAppNotificationsEnabled: false,
    });
    updateNotificationPreferences
      .mockResolvedValueOnce({
        weeklyDigestEmail: false,
        interviewReminders: true,
        ghostedAlert: true,
        interviewReminderEmail: true,
        inAppNotificationsEnabled: false,
      })
      .mockResolvedValueOnce({
        weeklyDigestEmail: false,
        interviewReminders: false,
        ghostedAlert: false,
        interviewReminderEmail: true,
        inAppNotificationsEnabled: false,
      });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    // Click Weekly news posts (turns false)
    const weeklySwitch = await screen.findByRole("switch", { name: /Weekly news posts/i });
    await user.click(weeklySwitch);
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(1));
    expect(updateNotificationPreferences.mock.calls[0][0]).toMatchObject({ weeklyDigestEmail: false });

    // Click master Alerts and reminders (turns OFF)
    const master = screen.getByRole("switch", { name: /Alerts and reminders/i });
    await user.click(master);
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledTimes(2));
    expect(updateNotificationPreferences.mock.calls[1][0]).toMatchObject({ interviewReminders: false, ghostedAlert: false });

    // Click Also email me (disabled at this point - must have no effect)
    const alsoEmail = screen.getByRole("switch", { name: /Also email me for interview reminders/i });
    await user.click(alsoEmail);

    // Still only 2 PUTs
    expect(updateNotificationPreferences).toHaveBeenCalledTimes(2);

    // All four UI switches show off
    await waitFor(() => expect(weeklySwitch).toHaveAttribute("aria-checked", "false"));
    expect(master).toHaveAttribute("aria-checked", "false");
  });
});

describe("TC-21 | AC-09: Loading state: all toggles disabled during GET in-flight", () => {
  it("shows notifications-loading skeleton and no live switches during GET in-flight", async () => {
    getNotificationPreferences.mockImplementation(() => new Promise(() => {}));

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    expect(screen.getByTestId("notifications-loading")).toBeInTheDocument();

    // All switches shown during loading skeleton must be disabled
    const loadingSwitches = screen.queryAllByRole("switch");
    loadingSwitches.forEach((sw) => {
      expect(sw).toHaveAttribute("aria-disabled", "true");
    });
  });
});

describe("TC-22 | AC-10: GET 5xx: error banner, no toggle rows rendered", () => {
  it("shows error banner with role=alert; no Weekly news posts, Alerts and reminders, or Interview reminders switches", async () => {
    getNotificationPreferences.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const errorEl = await screen.findByTestId("notifications-error");
    expect(errorEl).toHaveAttribute("role", "alert");
    expect(errorEl.textContent).toMatch(/Couldn't load your notification preferences/i);

    expect(screen.queryByRole("switch", { name: /Weekly news posts/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("switch", { name: /Alerts and reminders/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("switch", { name: "Interview reminders" })).not.toBeInTheDocument();
  });
});

describe("TC-23 | AC-10: GET 401: error banner + onLogout", () => {
  it("shows error banner and calls onLogout when GET returns 401", async () => {
    getNotificationPreferences.mockRejectedValue(
      new ApiError(401, "Unauthorized", { error: "Unauthorized", message: "token expired" })
    );
    const onLogout = vi.fn();

    const user = userEvent.setup();
    renderSettings({ onLogout });
    await gotoNotifications(user);

    await screen.findByTestId("notifications-error");
    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
  });
});

describe("TC-24 | AC-11: PUT 5xx: toggle reverts; error shown", () => {
  it("Weekly news posts reverts to pre-click value after 5xx PUT; toggle-error shown with role=alert", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE); // weeklyDigestEmail: true
    updateNotificationPreferences.mockRejectedValue(
      new ApiError(500, "Internal Server Error", { error: "Internal Server Error", message: "boom" })
    );

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    const weeklySwitch = await screen.findByRole("switch", { name: /Weekly news posts/i });
    expect(weeklySwitch).toHaveAttribute("aria-checked", "true");

    await user.click(weeklySwitch);

    // After rejection, reverts to original true value
    await waitFor(() => expect(weeklySwitch).toHaveAttribute("aria-checked", "true"));

    const errorEl = screen.getByTestId("notifications-toggle-error");
    expect(errorEl).toHaveAttribute("role", "alert");
  });
});

describe("TC-25 | AC-11: PUT 401: toggle reverts; onLogout called", () => {
  it("Ghosted alert reverts to pre-click value and onLogout is called on 401 PUT", async () => {
    getNotificationPreferences.mockResolvedValue({ ...PREFS_BASE, ghostedAlert: false });
    updateNotificationPreferences.mockRejectedValue(
      new ApiError(401, "Unauthorized", { error: "Unauthorized", message: "token expired" })
    );
    const onLogout = vi.fn();

    const user = userEvent.setup();
    renderSettings({ onLogout });
    await gotoNotifications(user);

    const ghostSwitch = await screen.findByRole("switch", { name: /Ghosted alert/i });
    expect(ghostSwitch).toHaveAttribute("aria-checked", "false");

    await user.click(ghostSwitch);

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(ghostSwitch).toHaveAttribute("aria-checked", "false"));
  });
});

describe("TC-26 | AC-12: A11y: every visible toggle has role=switch + accessible name matching locked copy", () => {
  it("exactly 5 switches with correct accessible names exist in the Notifications section", async () => {
    getNotificationPreferences.mockResolvedValue({
      interviewReminders: true,
      ghostedAlert: true,
      interviewReminderEmail: true,
      weeklyDigestEmail: true,
      inAppNotificationsEnabled: false,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await screen.findByRole("switch", { name: "Weekly news posts" });

    expect(screen.getByRole("switch", { name: "Weekly news posts" })).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "Alerts and reminders" })).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "Interview reminders" })).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "Ghosted alert" })).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "Also email me for interview reminders" })).toBeInTheDocument();

    // Exactly 5 switches in the notifications section
    const notifSection = screen.getByTestId("notifications-section");
    expect(within(notifSection).getAllByRole("switch")).toHaveLength(5);
  });
});

describe("TC-27 | AC-12: A11y: aria-checked reflects actual toggle state for all switches", () => {
  it("aria-checked on each switch matches the server values (with master derived from OR)", async () => {
    getNotificationPreferences.mockResolvedValue({
      weeklyDigestEmail: false,
      interviewReminders: true,
      ghostedAlert: true,
      interviewReminderEmail: false,
      inAppNotificationsEnabled: true,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await screen.findByRole("switch", { name: /Weekly news posts/i });

    expect(screen.getByRole("switch", { name: /Weekly news posts/i })).toHaveAttribute("aria-checked", "false");
    expect(screen.getByRole("switch", { name: /Alerts and reminders/i })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("switch", { name: "Interview reminders" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("switch", { name: /Ghosted alert/i })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("switch", { name: /Also email me for interview reminders/i })).toHaveAttribute("aria-checked", "false");
  });
});

describe("TC-28 | AC-12: A11y: disabled nested toggles have aria-disabled=true", () => {
  it("when master is OFF all nested toggles carry aria-disabled=true; Weekly news posts does not", async () => {
    getNotificationPreferences.mockResolvedValue({
      interviewReminders: false,
      ghostedAlert: false,
      interviewReminderEmail: true,
      weeklyDigestEmail: true,
      inAppNotificationsEnabled: false,
    });

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await screen.findByRole("switch", { name: /Alerts and reminders/i });

    expect(screen.getByRole("switch", { name: "Interview reminders" })).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("switch", { name: /Ghosted alert/i })).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("switch", { name: /Also email me for interview reminders/i })).toHaveAttribute("aria-disabled", "true");

    const weeklySwitch = screen.getByRole("switch", { name: /Weekly news posts/i });
    expect(weeklySwitch).not.toHaveAttribute("aria-disabled", "true");
  });
});

describe("TC-29 | AC-12: A11y: nested group is wrapped in role=group labelled 'Alerts and reminders'", () => {
  it("there is a role=group element named Alerts and reminders containing the three nested toggles", async () => {
    getNotificationPreferences.mockResolvedValue(PREFS_BASE);

    const user = userEvent.setup();
    renderSettings();
    await gotoNotifications(user);

    await screen.findByRole("switch", { name: /Alerts and reminders/i });

    const group = screen.getByRole("group", { name: /Alerts and reminders/i });
    expect(group).toBeInTheDocument();

    expect(within(group).getByRole("switch", { name: "Interview reminders" })).toBeInTheDocument();
    expect(within(group).getByRole("switch", { name: /Ghosted alert/i })).toBeInTheDocument();
    expect(within(group).getByRole("switch", { name: /Also email me for interview reminders/i })).toBeInTheDocument();
  });
});
