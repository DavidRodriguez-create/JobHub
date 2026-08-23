/**
 * Integration tests: wiring CustomReminderList/CustomReminderForm into
 * ApplicationDetailScreen (story #163, ticket #166).
 *
 * Source: JobHub-ui/docs/specs/163-test-cases.md (CR-WIRE-001..018).
 * These cases cover the WIRING gap only — CustomReminderList / CustomReminderForm's own
 * internal behaviour is already covered by CustomReminderList.test.jsx / CustomReminderForm.test.jsx
 * and is NOT re-derived here.
 *
 * Cases:
 *   CR-WIRE-001 (AC-1)  Reminders card renders in the left column alongside Notes/Timeline
 *   CR-WIRE-002 (AC-2)  List call scoped to app.apiId, never the global list
 *   CR-WIRE-003 (AC-3)  Add flow: header button opens form, success closes modal + refetches
 *   CR-WIRE-004 (AC-3)  New reminder visible in the list after add, without manual refresh
 *   CR-WIRE-005 (AC-4)  Add validation error keeps form open (pass-through)
 *   CR-WIRE-006 (AC-4)  Add API error (409) keeps form open with entered data
 *   CR-WIRE-007 (AC-5)  Edit flow: onEditReminder opens form pre-filled, success closes + refetches
 *   CR-WIRE-008 (AC-6)  Edit-then-409 (already fired) shows inline error, form stays open
 *   CR-WIRE-009 (AC-7)  Delete success: row removed, no error shown
 *   CR-WIRE-010 (AC-8)  Delete 404: row removed silently, no toast/error
 *   CR-WIRE-011 (AC-9)  Delete 409: row stays, inline "already fired" message
 *   CR-WIRE-012 (AC-10) Empty state: header "Add reminder" reachable and opens the form
 *   CR-WIRE-013 (AC-13) 401 on any reminders action invokes onLogout consistently
 *   CR-WIRE-014 (AC-11) No apiId: inactive state shown, zero API calls on mount
 *   CR-WIRE-015 (AC-11) No apiId: inactive state holds through interaction attempts
 *   CR-WIRE-016 (AC-12) Loading state visible while fetch in flight
 *   CR-WIRE-017 (AC-12) Error state (non-401) shown instead of blank/empty card
 *   CR-WIRE-018 (AC-14) Regression guard: documented props only, no contract drift
 *
 * Story #175 / sub-issue #201 additions (save-reflects, AC-9..AC-11):
 *   CR-UI-C11 (AC-9)  create from empty state: modal closes, list replaces empty state
 *   CR-UI-C12 (AC-9)  create from list state: new reminder appended, existing rows kept
 *   CR-UI-C13 (AC-10) edit updates title/note/trigger/channels/stage without refresh
 *   CR-UI-C14 (AC-11) save-success refetch itself fails (ApiError): error state shown, not stale
 *   CR-UI-C15 (AC-11) save-success refetch network failure variant (no .status)
 *
 * Story #175 / sub-issue #201 additions (form visual consistency, requires the Modal wrapper
 * rendered at the RemindersCard call site, AC-13/AC-14):
 *   CR-UI-C19 (AC-13) form's fields are descendants of the Modal's modal-body container
 *   CR-UI-C20 (AC-14) primary action is shared Button variant=primary, inside modal-foot
 *   CR-UI-C21 (AC-14) secondary action is shared Button non-primary variant, inside modal-foot
 *
 * Story #211 / Sub-issue #253 additions (req-4 card identity on the add/edit-reminder modal):
 *   CR-UI-330 (AC-211-4.1) company icon + job title shown in the modal header on Add
 *   CR-UI-331 (AC-211-4.2) company icon + job title shown in the modal header on Edit
 *   CR-UI-332 (AC-211-4.3) graceful fallback (generic icon) when company/job identity is
 *             unresolved; edit mode still has no editable Title field (body-only stays)
 */
import React from "react";
import { render, screen, waitFor, within, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

import DATA from "../../data/mockData.js";

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

vi.mock("../../api/notifications.js", () => ({
  getNotificationPreferences: vi.fn(),
}));

import {
  createCustomReminder,
  updateCustomReminder,
  deleteCustomReminder,
  listMyCustomReminders,
  listCustomRemindersByApplication,
} from "../../api/custom-reminders.js";
import { ApiError } from "../../api/client.js";
import { getNotificationPreferences } from "../../api/notifications.js";
import { ApplicationDetailScreen } from "../../screens/Applications.jsx";

const APP_ID = "ea000000-0000-0000-0000-000000000099";
const JOB_ID = "job-wire-163";
const FUTURE_DATE = "2099-12-31T10:00";

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

function appWithApiId(overrides = {}) {
  return {
    apiId: APP_ID,
    jobId: JOB_ID,
    status: "applied",
    notes: "",
    timeline: [],
    appliedOn: "2026-06-01",
    lastUpdate: "Jun 1, 10:00 AM",
    ...overrides,
  };
}

function appWithoutApiId(overrides = {}) {
  return { ...appWithApiId(), apiId: undefined, ...overrides };
}

// The card-header "Add reminder" button, the empty-state "Add reminder" CTA (story #175,
// AC-1/AC-9), and CustomReminderForm's own submit button ("Add reminder" in create mode)
// all share the same accessible name. Scope to the dialog to unambiguously target the
// form's submit button, and to the card header to unambiguously target the open-modal
// trigger used by these pre-existing wiring cases.
function submitButtonWithin(dialog, name) {
  return within(dialog).getByRole("button", { name });
}

function getHeaderAddButton() {
  const heading = screen.getByText("Reminders");
  const header = heading.closest(".card-header");
  return within(header).getByRole("button", { name: /add reminder/i });
}

function renderDetail(props = {}) {
  return render(
    <ApplicationDetailScreen
      app={appWithApiId()}
      goto={vi.fn()}
      onBack={vi.fn()}
      openSearch={vi.fn()}
      onDelete={vi.fn()}
      onStatusChange={vi.fn()}
      onNotesSave={vi.fn()}
      onEditSave={vi.fn()}
      onLogout={vi.fn()}
      {...props}
    />
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  // Default: no stored prefs row -> contract default interviewReminderEmail=true.
  getNotificationPreferences.mockResolvedValue({});
  // Seed the in-memory store so ApplicationDetailScreen's existing DATA.byId / DATA.coOf
  // lookups don't throw — per the QAE fixture note, reuse mockData.js's shape, not a new one.
  DATA.jobs.length = 0;
  Object.keys(DATA.companies).forEach((k) => delete DATA.companies[k]);
  DATA.jobs.push({
    id: JOB_ID,
    title: "Senior Engineer",
    co: "acme",
    location: "Remote",
    comp: "$120k",
    type: "Full-time",
    source: "LinkedIn",
    tags: [],
  });
  DATA.companies["acme"] = { name: "Acme Corp", industry: "Software", size: "201-500", hq: "Remote", url: "" };
});

describe("CR-WIRE-001: Reminders card renders on the detail screen (AC-1)", () => {
  it("shows a 'Reminders' card heading alongside Notes/Timeline, and a settled list state", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });

    renderDetail();

    expect(screen.getByText("Reminders")).toBeInTheDocument();
    expect(screen.getByText("Notes")).toBeInTheDocument();
    expect(screen.getByText("Timeline")).toBeInTheDocument();

    await screen.findByTestId("reminders-empty");
    expect(screen.queryByTestId("reminders-loading")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reminders-error")).not.toBeInTheDocument();
  });
});

describe("CR-WIRE-002: List is scoped to the open application only (AC-2)", () => {
  it("calls listCustomRemindersByApplication with app.apiId and never the global list", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });

    renderDetail();

    await screen.findByTestId("reminders-empty");

    expect(listCustomRemindersByApplication).toHaveBeenCalledWith(APP_ID, expect.anything());
    expect(listMyCustomReminders).not.toHaveBeenCalled();
  });
});

describe("CR-WIRE-003: Add flow opens the form and creates against this application (AC-3)", () => {
  it("creates with applicationId=app.apiId, closes the modal, and refetches the list", async () => {
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [] })
      .mockResolvedValueOnce({ content: [makeReminder({ id: "new-1", title: "New reminder" })] });
    createCustomReminder.mockResolvedValueOnce(makeReminder({ id: "new-1", title: "New reminder" }));
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");
    expect(dialog).toBeInTheDocument();

    await user.type(screen.getByLabelText("Title"), "New reminder");
    const whenInput = screen.getByLabelText("When");
    await user.clear(whenInput);
    await user.type(whenInput, FUTURE_DATE);

    await user.click(submitButtonWithin(dialog, "Add reminder"));

    await waitFor(() => expect(createCustomReminder).toHaveBeenCalledTimes(1));
    expect(createCustomReminder.mock.calls[0][0]).toMatchObject({ applicationId: APP_ID });

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await waitFor(() => expect(listCustomRemindersByApplication).toHaveBeenCalledTimes(2));
  });
});

describe("CR-WIRE-004: New reminder appears in the list without manual refresh (AC-3)", () => {
  it("shows the newly created reminder's title from the component's own refetch", async () => {
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [] })
      .mockResolvedValueOnce({ content: [makeReminder({ id: "new-2", title: "Follow up call" })] });
    createCustomReminder.mockResolvedValueOnce(makeReminder({ id: "new-2", title: "Follow up call" }));
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog1 = screen.getByRole("dialog");
    await user.type(screen.getByLabelText("Title"), "Follow up call");
    const whenInput = screen.getByLabelText("When");
    await user.clear(whenInput);
    await user.type(whenInput, FUTURE_DATE);
    await user.click(submitButtonWithin(dialog1, "Add reminder"));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(await screen.findByText("Follow up call")).toBeInTheDocument();
  });
});

describe("CR-WIRE-005: Add validation error keeps form open (AC-4, pass-through)", () => {
  it("does not close the modal and does not call createCustomReminder on an empty title", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");
    await user.click(submitButtonWithin(dialog, "Add reminder"));

    expect(await screen.findByTestId("validation-error")).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(createCustomReminder).not.toHaveBeenCalled();
  });
});

describe("CR-WIRE-006: Add API error (409) keeps form open with entered data (AC-4)", () => {
  it("shows the inline API error and keeps the title value intact", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });
    createCustomReminder.mockRejectedValueOnce(new ApiError(409, "Conflict"));
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");
    await user.type(screen.getByLabelText("Title"), "Will conflict");
    const whenInput = screen.getByLabelText("When");
    await user.clear(whenInput);
    await user.type(whenInput, FUTURE_DATE);
    await user.click(submitButtonWithin(dialog, "Add reminder"));

    expect(await screen.findByTestId("form-error")).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByLabelText("Title")).toHaveValue("Will conflict");
  });
});

describe("CR-WIRE-007: Edit an existing reminder (AC-5)", () => {
  it("opens the form pre-filled (no Title field), updates via updateCustomReminder with no title key, closes and refetches", async () => {
    // Story #207 / Ticket #216 (AC-EDIT-1/AC-EDIT-2): the edit form has no Title field at
    // all, and the title is preserved server-side; editing only changes note/schedule.
    const existing = makeReminder({ id: "edit-1", title: "Original title", note: "Original note" });
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [existing] })
      .mockResolvedValueOnce({ content: [{ ...existing, note: "Updated note" }] });
    updateCustomReminder.mockResolvedValueOnce({ ...existing, note: "Updated note" });

    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminder-item");

    // Drive edit via the screen's own affordance: clicking the reminder's title area
    // is not part of CustomReminderList's contract, so the mount point must expose its
    // own edit trigger. We locate it by its accessible name "Edit reminder: Original title"
    // (the row's accessible name still derives from the reminder's create-time title).
    await user.click(screen.getByRole("button", { name: "Edit reminder: Original title" }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.queryByLabelText("Title")).not.toBeInTheDocument();
    expect(screen.getByLabelText(/note/i)).toHaveValue("Original note");

    await user.clear(screen.getByLabelText(/note/i));
    await user.type(screen.getByLabelText(/note/i), "Updated note");
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(updateCustomReminder).toHaveBeenCalledWith("edit-1", expect.objectContaining({ note: "Updated note" })));
    const [, body] = updateCustomReminder.mock.calls[0];
    expect(body).not.toHaveProperty("title");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await waitFor(() => expect(listCustomRemindersByApplication).toHaveBeenCalledTimes(2));
    expect(await screen.findByText("Original title")).toBeInTheDocument();
  });
});

describe("CR-WIRE-008: Editing an already-fired reminder is rejected inline (AC-6)", () => {
  it("shows the 409 inline message, keeps the modal open, and does not refetch", async () => {
    const existing = makeReminder({ id: "edit-2", title: "Fired soon" });
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [existing] });
    updateCustomReminder.mockRejectedValueOnce(new ApiError(409, "Conflict"));
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminder-item");

    await user.click(screen.getByRole("button", { name: "Edit reminder: Fired soon" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByTestId("form-error")).toHaveTextContent(
      "This reminder has already fired and cannot be edited."
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(listCustomRemindersByApplication).toHaveBeenCalledTimes(1);
  });
});

describe("CR-WIRE-009: Delete a reminder (success path) still works once mounted (AC-7)", () => {
  it("removes the row with no error shown", async () => {
    const existing = makeReminder({ id: "del-1", title: "To delete" });
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [existing] })
      .mockResolvedValueOnce({ content: [] });
    deleteCustomReminder.mockResolvedValueOnce(undefined);
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminder-item");

    await user.click(screen.getByTestId("reminder-delete-btn"));
    await user.click(screen.getByTestId("confirm-delete"));

    await waitFor(() => expect(deleteCustomReminder).toHaveBeenCalledWith("del-1"));
    await waitFor(() => expect(screen.queryByText("To delete")).not.toBeInTheDocument());
    expect(screen.queryByTestId("delete-error")).not.toBeInTheDocument();
  });
});

describe("CR-WIRE-010: Delete a reminder that's already gone (404) is silent (AC-8)", () => {
  it("removes the row silently, no toast/alert anywhere on the screen", async () => {
    const existing = makeReminder({ id: "del-2", title: "Already gone" });
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [existing] })
      .mockResolvedValueOnce({ content: [] });
    deleteCustomReminder.mockRejectedValueOnce(new ApiError(404, "Not Found"));
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminder-item");

    await user.click(screen.getByTestId("reminder-delete-btn"));
    await user.click(screen.getByTestId("confirm-delete"));

    await waitFor(() => expect(screen.queryByText("Already gone")).not.toBeInTheDocument());
    expect(screen.queryByTestId("delete-error")).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("CR-WIRE-011: Delete a reminder that already fired (409) keeps the row (AC-9)", () => {
  it("keeps the row and shows the inline 'already fired' message", async () => {
    const existing = makeReminder({ id: "del-3", title: "Already fired" });
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [existing] });
    deleteCustomReminder.mockRejectedValueOnce(new ApiError(409, "Conflict"));
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminder-item");

    await user.click(screen.getByTestId("reminder-delete-btn"));
    await user.click(screen.getByTestId("confirm-delete"));

    expect(await screen.findByTestId("delete-error")).toHaveTextContent(
      "This reminder has already fired and cannot be cancelled."
    );
    expect(screen.getByText("Already fired")).toBeInTheDocument();
  });
});

describe("CR-WIRE-012: Empty state with reachable Add action (AC-10)", () => {
  it("shows the empty state and a card-header Add reminder action that opens the form", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });
    const user = userEvent.setup();

    renderDetail();

    expect(await screen.findByTestId("reminders-empty")).toBeInTheDocument();
    // CustomReminderList's list-with-data state's own add-reminder-btn is not rendered
    // while empty (story #175: the empty state's own CTA shares onAddReminder instead).
    expect(screen.queryByTestId("add-reminder-btn")).not.toBeInTheDocument();

    await user.click(getHeaderAddButton());

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByLabelText("Title")).toBeInTheDocument();
  });
});

describe("CR-WIRE-013: Session expiry during any reminders action signs the user out (AC-13)", () => {
  it("calls onLogout once when the initial list fetch returns 401", async () => {
    listCustomRemindersByApplication.mockRejectedValueOnce(new ApiError(401, "Unauthorized"));
    const onLogout = vi.fn();

    renderDetail({ onLogout });

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId("reminders-error")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reminders-empty")).not.toBeInTheDocument();
  });

  it("calls the same onLogout when a delete returns 401", async () => {
    const existing = makeReminder({ id: "del-401", title: "Session will expire" });
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [existing] });
    deleteCustomReminder.mockRejectedValueOnce(new ApiError(401, "Unauthorized"));
    const onLogout = vi.fn();
    const user = userEvent.setup();

    renderDetail({ onLogout });
    await screen.findByTestId("reminder-item");

    await user.click(screen.getByTestId("reminder-delete-btn"));
    await user.click(screen.getByTestId("confirm-delete"));

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
  });
});

describe("CR-WIRE-014: Mock-mode guard: no API call when apiId is absent (AC-11)", () => {
  it("renders an inactive state and never calls any custom-reminders API function", async () => {
    renderDetail({ app: appWithoutApiId() });

    expect(screen.getByText("Reminders")).toBeInTheDocument();
    expect(screen.queryByTestId("reminders-loading")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reminders-empty")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reminders-error")).not.toBeInTheDocument();
    expect(screen.getByTestId("reminders-inactive")).toBeInTheDocument();

    await waitFor(() => {
      expect(listCustomRemindersByApplication).not.toHaveBeenCalled();
      expect(createCustomReminder).not.toHaveBeenCalled();
      expect(updateCustomReminder).not.toHaveBeenCalled();
      expect(deleteCustomReminder).not.toHaveBeenCalled();
    });
  });
});

describe("CR-WIRE-015: Mock-mode guard holds even when the user tries to interact (AC-11)", () => {
  it("never calls any custom-reminders API function even if a header button is clicked", async () => {
    const user = userEvent.setup();
    renderDetail({ app: appWithoutApiId() });

    const inactive = screen.getByTestId("reminders-inactive");
    const addBtn = within(inactive.closest(".card") || inactive).queryByRole("button", { name: /add reminder/i });
    if (addBtn) {
      await user.click(addBtn);
    }

    expect(listCustomRemindersByApplication).not.toHaveBeenCalled();
    expect(createCustomReminder).not.toHaveBeenCalled();
    expect(updateCustomReminder).not.toHaveBeenCalled();
    expect(deleteCustomReminder).not.toHaveBeenCalled();
  });
});

describe("CR-WIRE-016: Loading state is visible while fetch is in flight (AC-12)", () => {
  it("shows 'Loading reminders…' before the fetch resolves", async () => {
    let resolve;
    listCustomRemindersByApplication.mockImplementation(() => new Promise((r) => { resolve = r; }));

    renderDetail();

    expect(screen.getByTestId("reminders-loading")).toBeInTheDocument();

    await act(async () => { resolve({ content: [] }); });
    await waitFor(() => expect(screen.queryByTestId("reminders-loading")).not.toBeInTheDocument());
  });
});

describe("CR-WIRE-017: Error state is visible on non-401 fetch failure (AC-12)", () => {
  it("shows the error state instead of a blank or empty card, with no onLogout call", async () => {
    listCustomRemindersByApplication.mockRejectedValueOnce(new ApiError(500, "Server Error"));
    const onLogout = vi.fn();

    renderDetail({ onLogout });

    expect(await screen.findByTestId("reminders-error")).toBeInTheDocument();
    expect(screen.queryByTestId("reminders-empty")).not.toBeInTheDocument();
    expect(onLogout).not.toHaveBeenCalled();
  });
});

describe("CR-WIRE-018: Component contracts unchanged by the wiring (AC-14, regression guard)", () => {
  it("calls listCustomRemindersByApplication without includeFired:true on this surface", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });

    renderDetail();
    await screen.findByTestId("reminders-empty");

    expect(listCustomRemindersByApplication).toHaveBeenCalledWith(
      APP_ID,
      expect.not.objectContaining({ includeFired: true })
    );
  });

  it("creates with only documented fields (applicationId, title, triggerAtUtc, channels)", async () => {
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [] })
      .mockResolvedValueOnce({ content: [] });
    createCustomReminder.mockResolvedValueOnce(makeReminder());
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");
    await user.type(screen.getByLabelText("Title"), "Contract check");
    const whenInput = screen.getByLabelText("When");
    await user.clear(whenInput);
    await user.type(whenInput, FUTURE_DATE);
    await user.click(submitButtonWithin(dialog, "Add reminder"));

    await waitFor(() => expect(createCustomReminder).toHaveBeenCalledTimes(1));
    const body = createCustomReminder.mock.calls[0][0];
    expect(Object.keys(body).sort()).toEqual(["applicationId", "channels", "title", "triggerAtUtc"].sort());
  });
});

describe("CR-UI-C11 (AC-9): create from empty state replaces the empty state with the list", () => {
  it("closes the modal and shows reminders-list with the new reminder's title, no manual reload", async () => {
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [] })
      .mockResolvedValueOnce({ content: [makeReminder({ id: "c11-1", title: "From empty" })] });
    createCustomReminder.mockResolvedValueOnce(makeReminder({ id: "c11-1", title: "From empty" }));
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");
    await user.type(screen.getByLabelText("Title"), "From empty");
    const whenInput = screen.getByLabelText("When");
    await user.clear(whenInput);
    await user.type(whenInput, FUTURE_DATE);
    await user.click(submitButtonWithin(dialog, "Add reminder"));

    // Pure React state transition: no manual reload, list replaces empty state on its own.
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await waitFor(() => expect(screen.queryByTestId("reminders-empty")).not.toBeInTheDocument());
    expect(await screen.findByTestId("reminders-list")).toBeInTheDocument();
    expect(screen.getByText("From empty")).toBeInTheDocument();
  });
});

describe("CR-UI-C12 (AC-9): create from list state appends without losing existing rows", () => {
  it("shows both the original and the new reminder after a successful create", async () => {
    const existing = makeReminder({ id: "c12-1", title: "Already here" });
    const added = makeReminder({ id: "c12-2", title: "Newly added" });
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [existing] })
      .mockResolvedValueOnce({ content: [existing, added] });
    createCustomReminder.mockResolvedValueOnce(added);
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminder-item");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");
    await user.type(screen.getByLabelText("Title"), "Newly added");
    const whenInput = screen.getByLabelText("When");
    await user.clear(whenInput);
    await user.type(whenInput, FUTURE_DATE);
    await user.click(submitButtonWithin(dialog, "Add reminder"));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(await screen.findByText("Newly added")).toBeInTheDocument();
    expect(screen.getByText("Already here")).toBeInTheDocument();
  });
});

describe("CR-UI-C13 (AC-10): edit updates note/schedule/channels/stage without manual refresh", () => {
  it("reflects new note, trigger time, channels, and stage in the list; title is unchanged (AC-EDIT-1/2)", async () => {
    // Story #207 / Ticket #216: title is create-time-only and not part of the edit form,
    // so this case now edits the remaining four fields instead of five.
    const before = makeReminder({
      id: "c13-1",
      title: "Before title",
      note: "Before note",
      triggerAtUtc: "2099-01-01T10:00:00Z",
      channels: ["IN_APP"],
      stage: "SCREENING",
    });
    const after = {
      ...before,
      note: "After note",
      triggerAtUtc: "2099-02-02T12:00:00Z",
      channels: ["IN_APP", "EMAIL"],
      stage: "OFFER",
    };
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [before] })
      .mockResolvedValueOnce({ content: [after] });
    updateCustomReminder.mockResolvedValueOnce(after);
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminder-item");

    await user.click(screen.getByRole("button", { name: "Edit reminder: Before title" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.queryByLabelText("Title")).not.toBeInTheDocument();

    const noteInput = screen.getByLabelText(/note/i);
    await user.clear(noteInput);
    await user.type(noteInput, "After note");

    const whenInput = screen.getByLabelText("When");
    await user.clear(whenInput);
    await user.type(whenInput, "2099-02-02T12:00");

    const emailCb =
      document.querySelector("input[type='checkbox'][data-channel='EMAIL']") ||
      screen.getByRole("checkbox", { name: /email/i });
    if (!emailCb.checked) await user.click(emailCb);

    const stageEl =
      document.querySelector("[data-testid='stage-select']") ||
      screen.getByRole("combobox", { name: /stage/i });
    await user.selectOptions(stageEl, "OFFER");

    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(updateCustomReminder).toHaveBeenCalledTimes(1));
    const [, body] = updateCustomReminder.mock.calls[0];
    expect(body).not.toHaveProperty("title");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    expect(await screen.findByText("Before title")).toBeInTheDocument();
    expect(screen.getByText("After note")).toBeInTheDocument();
    const item = screen.getByTestId("reminder-item");
    expect(within(item).getByTestId("reminder-channels").textContent).toMatch(/IN_APP/);
    expect(within(item).getByTestId("reminder-channels").textContent).toMatch(/EMAIL/);
    expect(within(item).getByTestId("reminder-stage").textContent).toMatch(/Offer/i);
  });
});

describe("CR-UI-C14 (AC-11): save-success refetch itself fails (ApiError) shows error, not stale", () => {
  it("create-from-empty: shows reminders-error when the post-create refetch rejects with 500", async () => {
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [] })
      .mockRejectedValueOnce(new ApiError(500, "Server Error"));
    createCustomReminder.mockResolvedValueOnce(makeReminder({ id: "c14-1", title: "Will fail refetch" }));
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");
    await user.type(screen.getByLabelText("Title"), "Will fail refetch");
    const whenInput = screen.getByLabelText("When");
    await user.clear(whenInput);
    await user.type(whenInput, FUTURE_DATE);
    await user.click(submitButtonWithin(dialog, "Add reminder"));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(await screen.findByTestId("reminders-error")).toBeInTheDocument();
    expect(screen.queryByTestId("reminders-empty")).not.toBeInTheDocument();
  });

  it("edit-from-list: shows reminders-error when the post-edit refetch rejects with 500", async () => {
    const existing = makeReminder({ id: "c14-2", title: "Edit me" });
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [existing] })
      .mockRejectedValueOnce(new ApiError(500, "Server Error"));
    updateCustomReminder.mockResolvedValueOnce({ ...existing, title: "Edited" });
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminder-item");

    await user.click(screen.getByRole("button", { name: "Edit reminder: Edit me" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(await screen.findByTestId("reminders-error")).toBeInTheDocument();
  });
});

describe("CR-UI-C15 (AC-11): save-success refetch network failure variant", () => {
  it("shows reminders-error when the post-create refetch throws a plain network Error (no .status)", async () => {
    listCustomRemindersByApplication
      .mockResolvedValueOnce({ content: [] })
      .mockRejectedValueOnce(new Error("Network failure"));
    createCustomReminder.mockResolvedValueOnce(makeReminder({ id: "c15-1", title: "Network fail refetch" }));
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");
    await user.type(screen.getByLabelText("Title"), "Network fail refetch");
    const whenInput = screen.getByLabelText("When");
    await user.clear(whenInput);
    await user.type(whenInput, FUTURE_DATE);
    await user.click(submitButtonWithin(dialog, "Add reminder"));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(await screen.findByTestId("reminders-error")).toBeInTheDocument();
  });
});

describe("CR-UI-C19 (AC-13): form renders inside the shared Modal's body container", () => {
  it("the title input is a descendant of an element with class modal-body", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");

    const titleInput = within(dialog).getByLabelText("Title");
    const modalBody = dialog.querySelector(".modal-body");
    expect(modalBody).toBeTruthy();
    expect(modalBody.contains(titleInput)).toBe(true);
  });
});

describe("CR-UI-C20 (AC-14): primary action is shared Button variant=primary, inside modal-foot", () => {
  it("the 'Add reminder' submit button has btn+primary classes and sits in modal-foot", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");

    const submitBtn = submitButtonWithin(dialog, "Add reminder");
    expect(submitBtn).toHaveClass("btn");
    expect(submitBtn).toHaveClass("primary");

    const footer = dialog.querySelector(".modal-foot");
    expect(footer).toBeTruthy();
    expect(footer.contains(submitBtn)).toBe(true);
  });

  it("the 'Save' submit button in edit mode has btn+primary classes and sits in modal-foot", async () => {
    const existing = makeReminder({ id: "c20-1", title: "Edit me too" });
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [existing] });
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminder-item");

    await user.click(screen.getByRole("button", { name: "Edit reminder: Edit me too" }));
    const dialog = screen.getByRole("dialog");

    const saveBtn = within(dialog).getByRole("button", { name: "Save" });
    expect(saveBtn).toHaveClass("btn");
    expect(saveBtn).toHaveClass("primary");

    const footer = dialog.querySelector(".modal-foot");
    expect(footer).toBeTruthy();
    expect(footer.contains(saveBtn)).toBe(true);
  });
});

describe("CR-UI-C21 (AC-14): secondary action is shared Button non-primary variant, in modal-foot", () => {
  it("the Cancel button has a non-primary btn variant and sits in modal-foot alongside the primary action", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");

    const cancelBtn = within(dialog).getByRole("button", { name: "Cancel" });
    expect(cancelBtn).toHaveClass("btn");
    expect(cancelBtn.className).not.toMatch(/\bprimary\b/);

    const footer = dialog.querySelector(".modal-foot");
    expect(footer).toBeTruthy();
    expect(footer.contains(cancelBtn)).toBe(true);

    const submitBtn = submitButtonWithin(dialog, "Add reminder");
    expect(footer.contains(submitBtn)).toBe(true);
  });
});

describe("CR-UI-330 (AC-211-4.1): Add-reminder modal header shows company icon + job title", () => {
  it("renders the job's company logo and title in the modal header on Add", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");
    const header = dialog.querySelector(".modal-head");

    expect(within(header).getByTestId("notification-row-job-title")).toHaveTextContent("Senior Engineer");
    expect(within(header).getByTestId("notification-row-co-logo")).toBeInTheDocument();
  });
});

describe("CR-UI-331 (AC-211-4.2): Edit-reminder modal header shows company icon + job title", () => {
  it("renders the job's company logo and title in the modal header on Edit, with no editable Title field", async () => {
    const existing = makeReminder({ id: "edit-330", title: "Original title" });
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [existing] });
    const user = userEvent.setup();

    renderDetail();
    await screen.findByTestId("reminder-item");
    await user.click(screen.getByRole("button", { name: "Edit reminder: Original title" }));

    const dialog = screen.getByRole("dialog");
    const header = dialog.querySelector(".modal-head");

    expect(within(header).getByTestId("notification-row-job-title")).toHaveTextContent("Senior Engineer");
    expect(within(header).getByTestId("notification-row-co-logo")).toBeInTheDocument();

    // AC-EDIT-1/AC-EDIT-4 regression guard: body-only edit form still has no Title input.
    expect(within(dialog).queryByLabelText("Title")).not.toBeInTheDocument();
  });
});

describe("CR-UI-332 (AC-211-4.3): graceful fallback when company/job identity is unresolved", () => {
  it("shows a generic fallback icon and label instead of throwing when the company is unresolved", async () => {
    listCustomRemindersByApplication.mockResolvedValueOnce({ content: [] });
    const user = userEvent.setup();

    // Job still resolves to a title (the top-level detail screen's own DATA.byId lookup
    // must not throw), but its company code has no matching DATA.companies entry. DATA.coOf
    // falls back to a stub { name: co, ... } (mockData.js's own existing graceful-fallback
    // behaviour) rather than a falsy value, so emulate "company unresolved" the same way
    // story #207's notification cards do: a blank company name.
    DATA.companies["acme"] = { name: "", industry: "Software", size: "201-500", hq: "Remote", url: "" };

    renderDetail();
    await screen.findByTestId("reminders-empty");

    await user.click(getHeaderAddButton());
    const dialog = screen.getByRole("dialog");
    const header = dialog.querySelector(".modal-head");

    expect(within(header).getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(within(header).queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
  });
});
