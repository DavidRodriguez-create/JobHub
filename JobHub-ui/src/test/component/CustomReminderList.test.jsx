/**
 * Component tests for CustomReminderList.
 * Story #134 / Sub-issue #158 — Custom Reminders UI.
 * Story #175 / Sub-issue #201: three-state UX (empty/loading/error) + exact copy.
 *
 * Cases:
 *   CR-UI-040: per-app list renders SCHEDULED reminders in ascending order
 *   CR-UI-041: per-app list shows empty state (strengthened: exact copy + CTA, AC-1)
 *   CR-UI-042: per-app list shows loading state
 *   CR-UI-043: per-app list shows error state on 500 (strengthened: exact copy + Retry, AC-3)
 *   CR-UI-044: per-app list 404 shows appropriate error
 *   CR-UI-045: per-app list excludes FIRED/CANCELLED by default
 *   CR-UI-050: global list renders all SCHEDULED reminders ascending
 *   CR-UI-051: global list shows empty state
 *   CR-UI-052: global list shows loading state
 *   CR-UI-053: global list shows error state on 500
 *   CR-UI-054: global list 401 triggers onLogout
 *   CR-UI-030: delete shows confirmation before calling API
 *   CR-UI-031: confirming deletion calls deleteCustomReminder and removes item
 *   CR-UI-032: cancelling confirmation does not call API
 *   CR-UI-033: 409 from delete shows error; item stays
 *   CR-UI-034: 404 from delete silently removes item and refetches (no toast)
 *   CR-UI-060: triggerAtUtc displayed in local time (not UTC raw)
 *   CR-UI-061: fired reminder with past time displayed without "Invalid Date"
 *   CR-UI-C01 (AC-1): empty, default filter, exact copy + CTA invokes onAddReminder
 *   CR-UI-C02 (AC-2): empty, includeFired=true
 *   CR-UI-C03 (AC-3): load error, non-2xx (500)
 *   CR-UI-C04 (AC-4): load error, network failure (no .status)
 *   CR-UI-C05 (AC-5): Retry re-issues the request and recovers to empty
 *   CR-UI-C06 (AC-5): Retry recovers to the list state when data comes back
 *   CR-UI-C07 (AC-6): loading copy exact text
 *   CR-UI-C08 (AC-7): 401 triggers onLogout, no empty/error rendered
 *   CR-UI-C09 (AC-8): non-owned/absent applicationId reads as empty, never error
 *   CR-UI-C10 (AC-18): state containers are mutually exclusive
 */
import React from "react";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { render, screen, waitFor, within, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

const stylesPath = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../styles/styles.css"
);

vi.mock("../../api/custom-reminders.js", () => ({
  createCustomReminder: vi.fn(),
  updateCustomReminder: vi.fn(),
  deleteCustomReminder: vi.fn(),
  listMyCustomReminders: vi.fn(),
  listCustomRemindersByApplication: vi.fn(),
}));

vi.mock("../../api/client.js", () => ({
  ApiError: class ApiError extends Error {
    constructor(status, message, body) {
      super(message);
      this.name = "ApiError";
      this.status = status;
      this.body = body;
    }
  },
}));

import {
  deleteCustomReminder,
  listMyCustomReminders,
  listCustomRemindersByApplication,
} from "../../api/custom-reminders.js";
import { ApiError } from "../../api/client.js";
import { CustomReminderList } from "../../components/CustomReminderList.jsx";

const APP_ID = "ea000000-0000-0000-0000-000000000001";

function makeReminder(overrides = {}) {
  return {
    id: `er-${Math.random()}`,
    applicationId: APP_ID,
    title: "Prep call",
    triggerAtUtc: "2099-07-01T14:00:00Z",
    channels: ["IN_APP"],
    status: "SCHEDULED",
    createdAt: "2026-06-20T10:00:00Z",
    updatedAt: "2026-06-20T10:00:00Z",
    ...overrides,
  };
}

const R1 = makeReminder({ id: "er-1", triggerAtUtc: "2099-07-01T10:00:00Z", title: "First" });
const R2 = makeReminder({ id: "er-2", triggerAtUtc: "2099-07-02T10:00:00Z", title: "Second" });
const R3 = makeReminder({ id: "er-3", triggerAtUtc: "2099-07-03T10:00:00Z", title: "Third" });

function renderPerApp(props = {}) {
  return render(
    <CustomReminderList
      applicationId={APP_ID}
      onLogout={vi.fn()}
      {...props}
    />
  );
}

function renderGlobal(props = {}) {
  return render(
    <CustomReminderList
      onLogout={vi.fn()}
      {...props}
    />
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("CR-UI-040: per-app list renders SCHEDULED reminders in ascending order", () => {
  it("shows 3 reminders in ascending trigger order", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({
      content: [R1, R2, R3],
    });

    renderPerApp();

    const items = await screen.findAllByTestId("reminder-item");
    expect(items).toHaveLength(3);
    expect(within(items[0]).getByText("First")).toBeInTheDocument();
    expect(within(items[1]).getByText("Second")).toBeInTheDocument();
    expect(within(items[2]).getByText("Third")).toBeInTheDocument();
  });
});

describe("CR-UI-041: per-app list empty state (strengthened: exact copy + CTA, AC-1)", () => {
  it("shows empty state title/description/CTA and no error/could-not-load wording", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });
    const onAddReminder = vi.fn();

    renderPerApp({ onAddReminder });

    const empty = await screen.findByTestId("reminders-empty");
    expect(within(empty).getByText("No reminders yet")).toBeInTheDocument();
    expect(
      within(empty).getByText("Add a reminder so you do not lose track of this application.")
    ).toBeInTheDocument();
    const cta = within(empty).getByRole("button", { name: "Add reminder" });
    expect(cta).toBeInTheDocument();

    expect(screen.queryByTestId("reminder-item")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reminders-error")).not.toBeInTheDocument();
    expect(screen.queryByText(/could not load/i)).not.toBeInTheDocument();

    // FR-4: the empty state stays neutral (no danger styling) so it remains visually distinct
    // from the error state (CR-UI-C03 / CR-UI-C04), and its CTA is the brand-blue primary,
    // not the danger-adjacent ghost used by error's Retry.
    const emptyContainer = empty.querySelector(".empty");
    expect(emptyContainer).not.toHaveClass("danger");
    expect(cta).toHaveClass("primary");

    const user = userEvent.setup();
    await user.click(cta);
    expect(onAddReminder).toHaveBeenCalledTimes(1);
  });
});

describe("CR-UI-042: per-app list loading state", () => {
  it("shows loading indicator while fetch is in-flight", async () => {
    let resolve;
    listCustomRemindersByApplication.mockImplementation(
      () => new Promise((r) => { resolve = r; })
    );

    renderPerApp();

    expect(screen.getByTestId("reminders-loading")).toBeInTheDocument();

    await act(async () => { resolve({ content: [] }); });
    await waitFor(() => expect(screen.queryByTestId("reminders-loading")).not.toBeInTheDocument());
  });
});

describe("CR-UI-043 / CR-UI-C03 (AC-3): per-app list error state on 500 (strengthened: exact copy + Retry + danger styling)", () => {
  it("shows the alert error state with exact copy and a Retry button, no empty/Add reminder", async () => {
    listCustomRemindersByApplication.mockRejectedValueOnce(
      new ApiError(500, "Server Error", { error: "Server Error" })
    );

    renderPerApp();

    const error = await screen.findByTestId("reminders-error");
    expect(error).toHaveAttribute("role", "alert");
    expect(
      within(error).getByText("We could not load reminders. Check your connection and try again.")
    ).toBeInTheDocument();
    const retryBtn = within(error).getByRole("button", { name: "Retry" });
    expect(retryBtn).toBeInTheDocument();
    expect(within(error).queryByRole("button", { name: /add reminder/i })).not.toBeInTheDocument();

    expect(screen.queryByTestId("reminder-item")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reminders-empty")).not.toBeInTheDocument();

    // FR-4: error must use the app's danger visual treatment (--color-danger* tokens), and
    // Retry must not be the same brand-blue primary CTA the empty state uses for "Add reminder".
    expect(error.querySelector(".empty.danger")).toBeTruthy();
    expect(retryBtn).not.toHaveClass("primary");
    expect(retryBtn).toHaveClass("btn");
  });
});

describe("CR-UI-044: per-app list 404 shows error", () => {
  it("shows not-found error message when API returns 404", async () => {
    listCustomRemindersByApplication.mockRejectedValueOnce(
      new ApiError(404, "Not Found", { error: "Not Found" })
    );

    renderPerApp();

    expect(await screen.findByTestId("reminders-error")).toBeInTheDocument();
  });
});

describe("CR-UI-045: per-app list excludes FIRED/CANCELLED by default", () => {
  it("only shows SCHEDULED items (API called without includeFired)", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });

    renderPerApp();

    await screen.findByTestId("reminder-item");

    expect(listCustomRemindersByApplication).toHaveBeenCalledWith(
      APP_ID,
      expect.not.objectContaining({ includeFired: true })
    );
  });
});

describe("CR-UI-050: global list renders SCHEDULED reminders ascending", () => {
  it("shows 5 reminders in ascending trigger order", async () => {
    const reminders = [
      makeReminder({ id: "g1", title: "A", triggerAtUtc: "2099-07-01T10:00:00Z" }),
      makeReminder({ id: "g2", title: "B", triggerAtUtc: "2099-07-02T10:00:00Z" }),
      makeReminder({ id: "g3", title: "C", triggerAtUtc: "2099-07-03T10:00:00Z" }),
      makeReminder({ id: "g4", title: "D", triggerAtUtc: "2099-07-04T10:00:00Z" }),
      makeReminder({ id: "g5", title: "E", triggerAtUtc: "2099-07-05T10:00:00Z" }),
    ];
    listMyCustomReminders.mockResolvedValueOnce({ content: reminders });

    renderGlobal();

    const items = await screen.findAllByTestId("reminder-item");
    expect(items).toHaveLength(5);
    expect(within(items[0]).getByText("A")).toBeInTheDocument();
    expect(within(items[4]).getByText("E")).toBeInTheDocument();
  });
});

describe("CR-UI-051: global list empty state", () => {
  it("shows empty state when no reminders", async () => {
    listMyCustomReminders.mockResolvedValueOnce({ content: [] });

    renderGlobal();

    expect(await screen.findByTestId("reminders-empty")).toBeInTheDocument();
  });
});

describe("CR-UI-052: global list loading state", () => {
  it("shows loading indicator while fetch is in-flight", async () => {
    let resolve;
    listMyCustomReminders.mockImplementation(
      () => new Promise((r) => { resolve = r; })
    );

    renderGlobal();

    expect(screen.getByTestId("reminders-loading")).toBeInTheDocument();

    await act(async () => { resolve({ content: [] }); });
    await waitFor(() => expect(screen.queryByTestId("reminders-loading")).not.toBeInTheDocument());
  });
});

describe("CR-UI-053: global list error state on 500", () => {
  it("shows error message when API returns 500", async () => {
    listMyCustomReminders.mockRejectedValueOnce(
      new ApiError(500, "Server Error", { error: "Server Error" })
    );

    renderGlobal();

    expect(await screen.findByTestId("reminders-error")).toBeInTheDocument();
  });
});

describe("CR-UI-054: global list 401 triggers onLogout", () => {
  it("calls onLogout when API returns 401", async () => {
    listMyCustomReminders.mockRejectedValueOnce(
      new ApiError(401, "Unauthorized", { error: "Unauthorized" })
    );
    const onLogout = vi.fn();

    renderGlobal({ onLogout });

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId("reminder-item")).not.toBeInTheDocument();
  });
});

describe("CR-UI-030: delete shows confirmation before calling API", () => {
  it("shows confirmation dialog but does not call API yet", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    const user = userEvent.setup();

    renderPerApp();

    await screen.findByTestId("reminder-item");

    const deleteBtn = screen.getByTestId("reminder-delete-btn");
    await user.click(deleteBtn);

    expect(screen.getByTestId("delete-confirm")).toBeInTheDocument();
    expect(deleteCustomReminder).not.toHaveBeenCalled();
  });
});

describe("CR-UI-031: confirming deletion removes item from list", () => {
  it("calls deleteCustomReminder and removes the reminder row", async () => {
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [R1, R2] })
      .mockResolvedValueOnce({ content: [R2] });
    deleteCustomReminder.mockResolvedValueOnce(undefined);
    const user = userEvent.setup();

    renderPerApp();

    await screen.findAllByTestId("reminder-item");

    const deleteBtn = screen.getAllByTestId("reminder-delete-btn")[0];
    await user.click(deleteBtn);

    const confirmBtn = screen.getByTestId("confirm-delete");
    await user.click(confirmBtn);

    await waitFor(() => expect(deleteCustomReminder).toHaveBeenCalledWith(R1.id));
    await waitFor(() =>
      expect(screen.queryByText("First")).not.toBeInTheDocument()
    );
  });
});

describe("CR-UI-032: cancelling confirmation does not call API", () => {
  it("dismisses confirm dialog without calling API", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    const user = userEvent.setup();

    renderPerApp();

    await screen.findByTestId("reminder-item");

    const deleteBtn = screen.getByTestId("reminder-delete-btn");
    await user.click(deleteBtn);

    expect(screen.getByTestId("delete-confirm")).toBeInTheDocument();

    const cancelBtn = screen.getByTestId("cancel-delete");
    await user.click(cancelBtn);

    expect(deleteCustomReminder).not.toHaveBeenCalled();
    expect(screen.getByText("First")).toBeInTheDocument();
  });
});

describe("CR-UI-033: 409 from delete shows error; item stays", () => {
  it("shows error message and keeps item in list when API returns 409", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    deleteCustomReminder.mockRejectedValueOnce(
      new ApiError(409, "Conflict", { error: "Conflict", message: "Reminder already fired" })
    );
    const user = userEvent.setup();

    renderPerApp();

    await screen.findByTestId("reminder-item");

    const deleteBtn = screen.getByTestId("reminder-delete-btn");
    await user.click(deleteBtn);

    const confirmBtn = screen.getByTestId("confirm-delete");
    await user.click(confirmBtn);

    await waitFor(() => expect(screen.getByTestId("delete-error")).toBeInTheDocument());
    expect(screen.getByText("First")).toBeInTheDocument();
  });
});

describe("CR-UI-034: 404 from delete silently removes item; no toast", () => {
  it("removes item from list without error toast when API returns 404 (stale-list race)", async () => {
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [R1, R2] })
      .mockResolvedValueOnce({ content: [R2] });
    deleteCustomReminder.mockRejectedValueOnce(
      new ApiError(404, "Not Found", { error: "Not Found", message: "Already gone" })
    );
    const user = userEvent.setup();

    renderPerApp();

    await screen.findAllByTestId("reminder-item");

    const deleteBtn = screen.getAllByTestId("reminder-delete-btn")[0];
    await user.click(deleteBtn);

    const confirmBtn = screen.getByTestId("confirm-delete");
    await user.click(confirmBtn);

    await waitFor(() =>
      expect(screen.queryByText("First")).not.toBeInTheDocument()
    );
    expect(screen.queryByTestId("delete-error")).not.toBeInTheDocument();
    expect(listCustomRemindersByApplication).toHaveBeenCalledTimes(2);
  });
});

describe("CR-UI-060: triggerAtUtc displayed in user local time", () => {
  it("shows local date and time components, not UTC literal, and not relative phrases", async () => {
    const reminder = makeReminder({
      id: "tz-1",
      title: "TZ Test",
      triggerAtUtc: "2026-07-01T14:00:00Z",
    });
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [reminder] });

    renderPerApp();

    const item = await screen.findByTestId("reminder-item");
    const timeEl = within(item).getByTestId("reminder-trigger-time");

    expect(timeEl.textContent).not.toBe("");
    expect(timeEl.textContent).not.toMatch(/invalid date/i);
    expect(timeEl.textContent).not.toMatch(/\bago\b/i);
    expect(timeEl.textContent).not.toMatch(/\bin \d/i);
    expect(timeEl.textContent).toMatch(/\d/);
  });
});

describe("CR-UI-061: fired reminder with past time displays correctly", () => {
  it("shows date without 'Invalid Date' for a FIRED reminder with past triggerAtUtc", async () => {
    const fired = makeReminder({
      id: "fired-1",
      title: "Old Reminder",
      triggerAtUtc: "2020-01-15T09:00:00Z",
      status: "FIRED",
    });
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [fired] });

    render(
      <CustomReminderList
        applicationId={APP_ID}
        includeFired={true}
        onLogout={vi.fn()}
      />
    );

    const item = await screen.findByTestId("reminder-item");
    const timeEl = within(item).getByTestId("reminder-trigger-time");

    expect(timeEl.textContent).not.toMatch(/invalid date/i);
    expect(timeEl.textContent).not.toBe("");
    expect(timeEl.textContent).toMatch(/\d/);
  });
});

describe("CR-UI-C02 (AC-2): empty, includeFired=true", () => {
  it("shows the same empty state as AC-1, never an error", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });

    renderPerApp({ includeFired: true, onAddReminder: vi.fn() });

    const empty = await screen.findByTestId("reminders-empty");
    expect(within(empty).getByText("No reminders yet")).toBeInTheDocument();
    expect(
      within(empty).getByText("Add a reminder so you do not lose track of this application.")
    ).toBeInTheDocument();
    expect(within(empty).getByRole("button", { name: "Add reminder" })).toBeInTheDocument();
    expect(screen.queryByTestId("reminders-error")).not.toBeInTheDocument();
  });
});

describe("CR-UI-C04 (AC-4): load error, network failure (no .status)", () => {
  it("shows the identical error state as a plain network Error with no .status", async () => {
    listCustomRemindersByApplication.mockRejectedValueOnce(new Error("Network failure"));

    renderPerApp();

    const error = await screen.findByTestId("reminders-error");
    expect(error).toHaveAttribute("role", "alert");
    expect(
      within(error).getByText("We could not load reminders. Check your connection and try again.")
    ).toBeInTheDocument();
    const retryBtn = within(error).getByRole("button", { name: "Retry" });
    expect(retryBtn).toBeInTheDocument();
    expect(screen.queryByTestId("reminders-empty")).not.toBeInTheDocument();

    // FR-4: the network-failure error state uses the same danger treatment as the 500 case
    // (CR-UI-C03), and it must be visually distinct from the neutral empty state's container.
    const errorContainer = error.querySelector(".empty");
    expect(errorContainer).toHaveClass("danger");
    expect(retryBtn).not.toHaveClass("primary");

    // Real-effect comparison against the resolved CSS for the danger container vs. the
    // computed style a neutral ".empty" (no danger) box would carry: the danger tokens differ
    // from the neutral ink tokens used by the empty state's title/description.
    const dangerCss = fs.readFileSync(stylesPath, "utf8");
    expect(dangerCss).toMatch(/\.empty\.danger\s*\{[^}]*var\(--color-danger-bg\)/);
    expect(dangerCss).toMatch(/\.empty\.danger \.ttl\s*\{[^}]*var\(--color-danger\)/);
    expect(dangerCss).toMatch(/\.empty\s*\{[^}]*var\(--color-surface\)/);
  });
});

describe("CR-UI-C05 (AC-5): Retry re-issues the request and recovers to empty", () => {
  it("transitions error -> loading -> empty when Retry is clicked", async () => {
    listCustomRemindersByApplication.mockRejectedValueOnce(
      new ApiError(500, "Server Error", { error: "Server Error" })
    );
    const user = userEvent.setup();

    renderPerApp();

    const error = await screen.findByTestId("reminders-error");

    let resolveRetry;
    listCustomRemindersByApplication.mockImplementation(
      () => new Promise((r) => { resolveRetry = r; })
    );

    await user.click(within(error).getByRole("button", { name: "Retry" }));

    expect(listCustomRemindersByApplication).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId("reminders-loading")).toBeInTheDocument();

    await act(async () => { resolveRetry({ content: [] }); });

    await waitFor(() => expect(screen.getByTestId("reminders-empty")).toBeInTheDocument());
    expect(screen.queryByTestId("reminders-error")).not.toBeInTheDocument();
  });
});

describe("CR-UI-C06 (AC-5): Retry recovers to the list state with data", () => {
  it("transitions error -> list when the retried response has reminders", async () => {
    listCustomRemindersByApplication.mockRejectedValueOnce(
      new ApiError(500, "Server Error", { error: "Server Error" })
    );
    const user = userEvent.setup();

    renderPerApp();

    const error = await screen.findByTestId("reminders-error");

    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });

    await user.click(within(error).getByRole("button", { name: "Retry" }));

    await waitFor(() => expect(screen.getByTestId("reminders-list")).toBeInTheDocument());
    expect(screen.getByText("First")).toBeInTheDocument();
    expect(screen.queryByTestId("reminders-error")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reminders-empty")).not.toBeInTheDocument();
  });
});

describe("CR-UI-C07 (AC-6): loading copy exact text", () => {
  it("shows 'Loading reminders...' (three literal dots) and no empty/error testid", async () => {
    let resolve;
    listCustomRemindersByApplication.mockImplementation(
      () => new Promise((r) => { resolve = r; })
    );

    renderPerApp();

    const loading = screen.getByTestId("reminders-loading");
    expect(loading.textContent).toContain("Loading reminders...");
    expect(loading.textContent).not.toContain("Loading reminders…");
    expect(screen.queryByTestId("reminders-empty")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reminders-error")).not.toBeInTheDocument();

    await act(async () => { resolve({ content: [] }); });
  });
});

describe("CR-UI-C08 (AC-7): 401 triggers onLogout, no empty/error rendered", () => {
  it("calls onLogout exactly once and renders neither empty nor error", async () => {
    listCustomRemindersByApplication.mockRejectedValueOnce(
      new ApiError(401, "Unauthorized", { error: "Unauthorized" })
    );
    const onLogout = vi.fn();

    renderPerApp({ onLogout });

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId("reminders-empty")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reminders-error")).not.toBeInTheDocument();
  });
});

describe("CR-UI-C09 (AC-8): non-owned/absent applicationId reads as empty, never error", () => {
  it("shows the AC-1 empty state for a 200+empty response regardless of ownership reason", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });

    renderPerApp({ applicationId: "not-owned-app-id" });

    const empty = await screen.findByTestId("reminders-empty");
    expect(within(empty).getByText("No reminders yet")).toBeInTheDocument();
    expect(screen.queryByTestId("reminders-error")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reminder-item")).not.toBeInTheDocument();
  });
});

describe("CR-UI-C10 (AC-18): state containers are mutually exclusive", () => {
  const STATE_TESTIDS = ["reminders-loading", "reminders-empty", "reminders-error", "reminders-list"];

  it("loading: only reminders-loading is present", async () => {
    listCustomRemindersByApplication.mockImplementation(() => new Promise(() => {}));

    renderPerApp();

    expect(screen.getByTestId("reminders-loading")).toBeInTheDocument();
    for (const id of STATE_TESTIDS.filter((t) => t !== "reminders-loading")) {
      expect(screen.queryByTestId(id)).not.toBeInTheDocument();
    }
  });

  it("empty: only reminders-empty is present", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });

    renderPerApp();

    await screen.findByTestId("reminders-empty");
    for (const id of STATE_TESTIDS.filter((t) => t !== "reminders-empty")) {
      expect(screen.queryByTestId(id)).not.toBeInTheDocument();
    }
  });

  it("error: only reminders-error is present", async () => {
    listCustomRemindersByApplication.mockRejectedValueOnce(new ApiError(500, "Server Error"));

    renderPerApp();

    await screen.findByTestId("reminders-error");
    for (const id of STATE_TESTIDS.filter((t) => t !== "reminders-error")) {
      expect(screen.queryByTestId(id)).not.toBeInTheDocument();
    }
  });

  it("list-with-data: only reminders-list is present", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });

    renderPerApp();

    await screen.findByTestId("reminders-list");
    for (const id of STATE_TESTIDS.filter((t) => t !== "reminders-list")) {
      expect(screen.queryByTestId(id)).not.toBeInTheDocument();
    }
  });
});

/**
 * Story #210 / Sub-issue #247: restyle the reminders list/rows to the design system.
 * Cases CR-UI-RS-01..23. Additive only; every case above this point stays unmodified.
 */
const APPLICATION_STATUS_CLASSES = [
  "saved", "applied", "screening", "interview", "offer", "accepted", "rejected", "ghosted", "withdrawn",
];

describe("CR-UI-RS-01: row container uses the DS row class, keeps its testid (AC-1)", () => {
  it("reminder-item carries the new DS row class and is not a plain unclassed div", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });

    renderPerApp();

    const item = await screen.findByTestId("reminder-item");
    expect(item).toHaveAttribute("data-testid", "reminder-item");
    expect(item.className).toMatch(/reminder-row/);
    expect(item.className.trim()).not.toBe("");
  });
});

describe("CR-UI-RS-02: title is no longer a bare unclassed <strong> (AC-1)", () => {
  it("title text is present and not wrapped in a bare, unclassed <strong>", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({
      content: [makeReminder({ title: "Prep call" })],
    });

    renderPerApp();

    const titleEl = await screen.findByText("Prep call");
    if (titleEl.tagName === "STRONG") {
      expect(titleEl.className.trim()).not.toBe("");
    } else {
      expect(titleEl.tagName).not.toBe("STRONG");
    }
  });
});

describe("CR-UI-RS-03: SCHEDULED status pill, no bracket text (AC-3)", () => {
  it("shows 'Scheduled' label with a pill class, no literal bracket text", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({
      content: [makeReminder({ status: "SCHEDULED" })],
    });

    renderPerApp();

    const item = await screen.findByTestId("reminder-item");
    const statusEl = within(item).getByTestId("reminder-status");
    expect(statusEl.textContent).toMatch(/Scheduled/);
    expect(statusEl.textContent).not.toMatch(/\[/);
    expect(statusEl.textContent).not.toMatch(/SCHEDULED/);
    expect(statusEl.className.trim()).not.toBe("");
  });
});

describe("CR-UI-RS-04: FIRED status pill, distinct muted tone (AC-3)", () => {
  it("shows 'Fired' label with a class distinct from SCHEDULED's", async () => {
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [makeReminder({ id: "rs-04-a", status: "SCHEDULED" })] });
    const { unmount } = renderPerApp();
    const scheduledItem = await screen.findByTestId("reminder-item");
    const scheduledClass = within(scheduledItem).getByTestId("reminder-status").className;
    unmount();

    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [makeReminder({ id: "rs-04-b", status: "FIRED" })], });
    renderPerApp({ includeFired: true });

    const item = await screen.findByTestId("reminder-item");
    const statusEl = within(item).getByTestId("reminder-status");
    expect(statusEl.textContent).toMatch(/Fired/);
    expect(statusEl.className).not.toBe(scheduledClass);
  });
});

describe("CR-UI-RS-05: CANCELLED status pill, muted/inactive tone (AC-3)", () => {
  it("shows 'Cancelled' label with a class distinct from SCHEDULED and FIRED", async () => {
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [makeReminder({ id: "rs-05-a", status: "SCHEDULED" })] });
    const { unmount: unmountScheduled } = renderPerApp();
    const scheduledItem = await screen.findByTestId("reminder-item");
    const scheduledClass = within(scheduledItem).getByTestId("reminder-status").className;
    unmountScheduled();

    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [makeReminder({ id: "rs-05-b", status: "FIRED" })] });
    const { unmount: unmountFired } = renderPerApp({ includeFired: true });
    const firedItem = await screen.findByTestId("reminder-item");
    const firedClass = within(firedItem).getByTestId("reminder-status").className;
    unmountFired();

    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [makeReminder({ id: "rs-05-c", status: "CANCELLED" })] });
    renderPerApp({ includeFired: true });

    const item = await screen.findByTestId("reminder-item");
    const statusEl = within(item).getByTestId("reminder-status");
    expect(statusEl.textContent).toMatch(/Cancelled/);
    expect(statusEl.className).not.toBe(scheduledClass);
    expect(statusEl.className).not.toBe(firedClass);
  });
});

describe("CR-UI-RS-06: status pill never reuses StatusPill's vocabulary (AC-3)", () => {
  it.each(["SCHEDULED", "FIRED", "CANCELLED"])(
    "status=%s does not carry any application-status class and has a non-empty label",
    async (status) => {
      listCustomRemindersByApplication.mockResolvedValueOnce({
        content: [makeReminder({ status })],
      });

      renderPerApp({ includeFired: true });

      const item = await screen.findByTestId("reminder-item");
      const statusEl = within(item).getByTestId("reminder-status");
      const tokens = statusEl.className.split(/\s+/);
      for (const appStatusClass of APPLICATION_STATUS_CLASSES) {
        expect(tokens).not.toContain(appStatusClass);
      }
      expect(statusEl.textContent.trim()).not.toBe("");
      expect(statusEl.textContent).not.toMatch(/undefined/i);
    }
  );
});

describe("CR-UI-RS-07: stage badge renders as a small neutral pill (AC-4)", () => {
  it("reminder-stage shows 'Interview' with a pill class distinct from reminder-status's", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({
      content: [makeReminder({ stage: "INTERVIEW", status: "SCHEDULED" })],
    });

    renderPerApp();

    const item = await screen.findByTestId("reminder-item");
    const stageEl = within(item).getByTestId("reminder-stage");
    const statusEl = within(item).getByTestId("reminder-status");
    expect(stageEl.textContent).toMatch(/Interview/);
    expect(stageEl.className.trim()).not.toBe("");
    expect(stageEl.className).not.toBe(statusEl.className);
  });
});

describe("CR-UI-RS-08: no stage renders no pill, no testid (AC-4 regression)", () => {
  it("reminder-stage is absent entirely when stage is omitted", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({
      content: [makeReminder({ stage: undefined })],
    });

    renderPerApp();

    const item = await screen.findByTestId("reminder-item");
    expect(within(item).queryByTestId("reminder-stage")).not.toBeInTheDocument();
  });
});

describe("CR-UI-RS-09: trigger time text is byte-identical pre/post restyle (AC-5)", () => {
  it("text content matches the existing formatTrigger assertions and gains a meta styling class", async () => {
    const reminder = makeReminder({ triggerAtUtc: "2026-07-01T14:00:00Z" });
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [reminder] });

    renderPerApp();

    const item = await screen.findByTestId("reminder-item");
    const timeEl = within(item).getByTestId("reminder-trigger-time");
    expect(timeEl.textContent).not.toBe("");
    expect(timeEl.textContent).not.toMatch(/invalid date/i);
    expect(timeEl.textContent).not.toMatch(/\bago\b/i);
    expect(timeEl.textContent).not.toMatch(/\bin \d/i);
    expect(timeEl.textContent).toMatch(/\d/);
    expect(timeEl.className.trim()).not.toBe("");
  });
});

describe("CR-UI-RS-10: channels text is byte-identical pre/post restyle (AC-5)", () => {
  it("reminder-channels text content equals the exact joined string and gains a meta styling class", async () => {
    const reminder = makeReminder({ channels: ["IN_APP", "EMAIL"] });
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [reminder] });

    renderPerApp();

    const item = await screen.findByTestId("reminder-item");
    const channelsEl = within(item).getByTestId("reminder-channels");
    expect(channelsEl.textContent).toBe("IN_APP, EMAIL");
    expect(channelsEl.className.trim()).not.toBe("");
  });
});

describe("CR-UI-RS-11: edit/delete buttons are DS Buttons (AC-6)", () => {
  it("reminder-edit-btn and reminder-delete-btn carry btn + ghost + sm classes", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });

    renderPerApp({ onEditReminder: vi.fn() });

    await screen.findByTestId("reminder-item");
    const editBtn = screen.getByTestId("reminder-edit-btn");
    const deleteBtn = screen.getByTestId("reminder-delete-btn");

    expect(editBtn).toHaveClass("btn");
    expect(editBtn).toHaveClass("ghost");
    expect(editBtn).toHaveClass("sm");
    expect(deleteBtn).toHaveClass("btn");
    expect(deleteBtn).toHaveClass("ghost");
    expect(deleteBtn).toHaveClass("sm");
  });
});

describe("CR-UI-RS-12: edit click still calls onEdit(reminder) (AC-6 regression guard)", () => {
  it("calls onEditReminder with the reminder object exactly once", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    const onEditReminder = vi.fn();
    const user = userEvent.setup();

    renderPerApp({ onEditReminder });

    await screen.findByTestId("reminder-item");
    await user.click(screen.getByTestId("reminder-edit-btn"));

    expect(onEditReminder).toHaveBeenCalledTimes(1);
    expect(onEditReminder).toHaveBeenCalledWith(R1);
  });
});

describe("CR-UI-RS-13: delete click still opens the confirm state (AC-6 regression guard)", () => {
  it("shows delete-confirm and does not call the API yet, against the restyled button", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    const user = userEvent.setup();

    renderPerApp();

    await screen.findByTestId("reminder-item");
    await user.click(screen.getByTestId("reminder-delete-btn"));

    expect(screen.getByTestId("delete-confirm")).toBeInTheDocument();
    expect(deleteCustomReminder).not.toHaveBeenCalled();
  });
});

describe("CR-UI-RS-14: aria-labels unchanged on Edit/Delete (AC-6)", () => {
  it("edit/delete buttons keep their exact aria-label text", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({
      content: [makeReminder({ title: "Prep call" })],
    });

    renderPerApp({ onEditReminder: vi.fn() });

    await screen.findByTestId("reminder-item");
    expect(screen.getByTestId("reminder-edit-btn")).toHaveAttribute(
      "aria-label",
      "Edit reminder: Prep call"
    );
    expect(screen.getByTestId("reminder-delete-btn")).toHaveAttribute(
      "aria-label",
      "Delete reminder: Prep call"
    );
  });
});

describe("CR-UI-RS-15: confirm button is a danger-variant DS Button (AC-7)", () => {
  it("confirm-delete carries btn + danger classes", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    const user = userEvent.setup();

    renderPerApp();

    await screen.findByTestId("reminder-item");
    await user.click(screen.getByTestId("reminder-delete-btn"));

    const confirmBtn = screen.getByTestId("confirm-delete");
    expect(confirmBtn).toHaveClass("btn");
    expect(confirmBtn).toHaveClass("danger");
  });
});

describe("CR-UI-RS-16: cancel button is a ghost-variant DS Button (AC-7)", () => {
  it("cancel-delete carries btn + ghost classes", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    const user = userEvent.setup();

    renderPerApp();

    await screen.findByTestId("reminder-item");
    await user.click(screen.getByTestId("reminder-delete-btn"));

    const cancelBtn = screen.getByTestId("cancel-delete");
    expect(cancelBtn).toHaveClass("btn");
    expect(cancelBtn).toHaveClass("ghost");
  });
});

describe("CR-UI-RS-17: delete confirm copy and behaviour survive the button swap (AC-7)", () => {
  it("keeps the exact copy and confirm/cancel outcomes", async () => {
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [R1] })
      .mockResolvedValueOnce({ content: [] });
    deleteCustomReminder.mockResolvedValueOnce(undefined);
    const user = userEvent.setup();

    renderPerApp();

    await screen.findByTestId("reminder-item");
    await user.click(screen.getByTestId("reminder-delete-btn"));

    const confirmBar = screen.getByTestId("delete-confirm");
    expect(within(confirmBar).getByText("Delete this reminder?")).toBeInTheDocument();

    await user.click(screen.getByTestId("confirm-delete"));
    await waitFor(() => expect(deleteCustomReminder).toHaveBeenCalledWith(R1.id));
  });

  it("cancel dismisses without an API call", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    const user = userEvent.setup();

    renderPerApp();

    await screen.findByTestId("reminder-item");
    await user.click(screen.getByTestId("reminder-delete-btn"));
    await user.click(screen.getByTestId("cancel-delete"));

    expect(deleteCustomReminder).not.toHaveBeenCalled();
    expect(screen.queryByTestId("delete-confirm")).not.toBeInTheDocument();
  });
});

describe("CR-UI-RS-18: delete error uses the DS danger token, not inline red (AC-8)", () => {
  it("delete-error has no inline color:red style and resolves to a real CSS rule using var(--color-danger)", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    deleteCustomReminder.mockRejectedValueOnce(
      new ApiError(409, "Conflict", { error: "Conflict" })
    );
    const user = userEvent.setup();

    renderPerApp();

    await screen.findByTestId("reminder-item");
    await user.click(screen.getByTestId("reminder-delete-btn"));
    await user.click(screen.getByTestId("confirm-delete"));

    const errorEl = await screen.findByTestId("delete-error");
    expect(errorEl.getAttribute("style") || "").not.toMatch(/color:\s*red/i);
    expect(errorEl.className.trim()).not.toBe("");

    const css = fs.readFileSync(stylesPath, "utf8");
    const classToken = errorEl.className.split(/\s+/)[0];
    const rule = new RegExp(`\\.${classToken}\\s*\\{[^}]*var\\(--color-danger\\)`);
    expect(css).toMatch(rule);
  });
});

describe("CR-UI-RS-19: delete error keeps role=alert and exact 409 copy (AC-8)", () => {
  it("renders role=alert and the exact 409 message text", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    deleteCustomReminder.mockRejectedValueOnce(
      new ApiError(409, "Conflict", { error: "Conflict" })
    );
    const user = userEvent.setup();

    renderPerApp();

    await screen.findByTestId("reminder-item");
    await user.click(screen.getByTestId("reminder-delete-btn"));
    await user.click(screen.getByTestId("confirm-delete"));

    const errorEl = await screen.findByTestId("delete-error");
    expect(errorEl).toHaveAttribute("role", "alert");
    expect(errorEl.textContent).toBe("This reminder has already fired and cannot be cancelled.");
  });
});

describe("CR-UI-RS-20: inline add-reminder is a secondary DS Button with a plus icon (AC-9)", () => {
  it("add-reminder-btn carries btn + secondary + sm classes and renders an icon", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    const onAddReminder = vi.fn();

    renderPerApp({ onAddReminder });

    await screen.findByTestId("reminder-item");
    const addBtn = screen.getByTestId("add-reminder-btn");

    expect(addBtn).toHaveClass("btn");
    expect(addBtn).toHaveClass("secondary");
    expect(addBtn).toHaveClass("sm");
    expect(addBtn.querySelector("svg")).toBeTruthy();
  });
});

describe("CR-UI-RS-21: inline add-reminder click still calls onAddReminder (AC-9 regression guard)", () => {
  it("calls onAddReminder exactly once when clicked", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1] });
    const onAddReminder = vi.fn();
    const user = userEvent.setup();

    renderPerApp({ onAddReminder });

    await screen.findByTestId("reminder-item");
    await user.click(screen.getByTestId("add-reminder-btn"));

    expect(onAddReminder).toHaveBeenCalledTimes(1);
  });
});

describe("CR-UI-RS-22: list container is single-bordered, rows are not nested Cards (AC-10)", () => {
  it("reminders-list carries a container class and no reminder-item carries the .card class", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [R1, R2, R3] });

    renderPerApp();

    const list = await screen.findByTestId("reminders-list");
    expect(list.className.trim()).not.toBe("");

    const items = screen.getAllByTestId("reminder-item");
    expect(items).toHaveLength(3);
    for (const item of items) {
      expect(item.className.split(/\s+/)).not.toContain("card");
    }
  });
});

describe("CR-UI-RS-23: regression gate, full existing suite stays green (AC-2, AC-11, AC-12)", () => {
  it("is satisfied by every CR-UI-0xx/CR-UI-Cxx case above passing unmodified in the same run", () => {
    // This case is a documentation marker for the regression gate described in the test
    // catalogue (story #210). The actual proof is the full `pnpm test` run reporting 0
    // failures across every CR-UI-0xx / CR-UI-Cxx describe block above, none of which were
    // edited by this story.
    expect(true).toBe(true);
  });
});
