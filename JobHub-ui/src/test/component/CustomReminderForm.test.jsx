/**
 * Component tests for CustomReminderForm.
 * Story #134 / Sub-issue #158 — Custom Reminders UI.
 * Story #175 / Sub-issue #201: form visual consistency (design system restyle).
 * Story #211 / Sub-issue #253: reminder channels (prefs-gated EMAIL, always-on IN_APP)
 * and a "Now" quick-pick replacing the would-be "Today" control.
 *
 * Cases:
 *   CR-UI-010: create form renders all required fields (no IN_APP checkbox; EMAIL-only
 *              control gated on prefs; channels still include IN_APP on submit)
 *   CR-UI-011: submit with valid data calls createCustomReminder
 *   CR-UI-012: submit with past date shows validation error; API not called
 *   CR-UI-013: submit with no channel shows validation error; API not called
 *   CR-UI-014: submit with blank title shows validation error; API not called
 *   CR-UI-015: EMAIL channel toggle visible and toggleable (rewritten: no IN_APP toggle)
 *   CR-UI-016: channels submitted always include IN_APP from state, plus EMAIL when checked
 *              (rewritten: no IN_APP checkbox to click)
 *   CR-UI-017: API error (404) shows error; form stays open
 *   CR-UI-018: API error (500) shows generic error
 *   CR-UI-019: loading state while API call in-flight; submit disabled
 *   CR-UI-020: edit form pre-populated with existing reminder values (no Title field)
 *   CR-UI-021: edit submits updateCustomReminder with changed trigger
 *   CR-UI-022: edit cannot move trigger to past
 *   CR-UI-023: edit 409 from API shows conflict error
 *   CR-UI-024: edit 404 from API shows not-found error
 *   CR-UI-200: edit form has no Title input/label/read-only display; Note/When/Channels/Stage prefilled
 *   CR-UI-201: edit Save sends a body with no `title` key at all
 *   CR-UI-202: edit Save success path never reads/displays a `title` from the response
 *   CR-UI-203: edit Cancel makes no API call
 *   CR-UI-204: create form still has a required Title field (regression guard)
 *   CR-UI-205: edit form never reads the reminder's `title` value into the rendered form
 *   CR-UI-C16 (AC-12): title input wrapped in shared Field with bound label
 *   CR-UI-C17 (AC-12): note textarea wrapped in shared Field, uses Input's textarea styling
 *   CR-UI-C18 (AC-12): trigger date/time input wrapped in shared Field, uses Input styling
 *   CR-UI-C22 (AC-15): client-side validation error uses field-error + role=alert, not inline red
 *   CR-UI-C23 (AC-15): API error (409 already-fired) uses field-error + role=alert, exact copy
 *   CR-UI-C24 (AC-16): EMAIL channel checkbox uses the app's styled control treatment (rewritten:
 *              IN_APP checkbox no longer exists)
 *   CR-UI-C25 (AC-16): stage select uses select.input styling
 *
 *   CR-UI-300 (AC-211-1.1): interviewReminderEmail===true -> EMAIL control rendered, pre-checked
 *   CR-UI-301 (AC-211-1.2): interviewReminderEmail===false -> EMAIL control absent from DOM
 *   CR-UI-302 (AC-211-1.3): no prefs row / field absent -> defaults to email-allowed=true
 *   CR-UI-303 (AC-211-1.4): prefs fetch failure -> defaults to email-allowed=true, form usable
 *   CR-UI-304 (AC-211-1.x): EMAIL gate is keyed on interviewReminderEmail specifically (not e.g.
 *              weeklyDigestEmail or ghostedAlert)
 *   CR-UI-305 (AC-211-1.x): edit mode also respects the prefs gate for EMAIL
 *   CR-UI-310 (AC-211-2.1): no IN_APP checkbox/toggle anywhere in the form
 *   CR-UI-311 (AC-211-2.2): submitted channels always contain IN_APP even though there is no
 *              visible IN_APP control (create and edit)
 *   CR-UI-320 (AC-211-3.1): a "Now" quick-pick button is rendered next to the When input
 *   CR-UI-321 (AC-211-3.2): no app-level "Today" button/control exists
 *   CR-UI-322 (AC-211-3.3): clicking "Now" sets the When input to ~the current instant and
 *              the form passes future-trigger validation; manual entry still works unchanged
 */
import React from "react";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { render, screen, waitFor, within, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

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

import { createCustomReminder, updateCustomReminder } from "../../api/custom-reminders.js";
import { ApiError } from "../../api/client.js";
import { getNotificationPreferences } from "../../api/notifications.js";
import { CustomReminderForm } from "../../components/CustomReminderForm.jsx";

const FUTURE_DATE = "2099-12-31T10:00";
const PAST_DATE = "2000-01-01T10:00";
const APP_ID = "ea000000-0000-0000-0000-000000000001";

const SCHEDULED_REMINDER = {
  id: "er000000-0000-0000-0000-000000000001",
  applicationId: APP_ID,
  title: "Old title",
  note: "Old note",
  triggerAtUtc: "2099-06-01T14:00:00Z",
  channels: ["IN_APP"],
  stage: "INTERVIEW",
  status: "SCHEDULED",
  createdAt: "2026-06-20T10:00:00Z",
  updatedAt: "2026-06-20T10:00:00Z",
};

function renderCreate(props = {}) {
  return render(
    <CustomReminderForm
      applicationId={APP_ID}
      onSuccess={vi.fn()}
      onCancel={vi.fn()}
      {...props}
    />
  );
}

function renderEdit(reminder = SCHEDULED_REMINDER, props = {}) {
  return render(
    <CustomReminderForm
      applicationId={APP_ID}
      reminder={reminder}
      onSuccess={vi.fn()}
      onCancel={vi.fn()}
      {...props}
    />
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  // Default: no stored prefs row -> contract default interviewReminderEmail=true
  // (AC-211-1.3). Individual cases override this to exercise the other branches.
  getNotificationPreferences.mockResolvedValue({});
});

describe("CR-UI-010: create form renders all required fields", () => {
  it("shows title input, date-time input, EMAIL channel control, stage selector, and submit button; no IN_APP control", async () => {
    renderCreate();

    expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/when/i) || screen.getByRole("textbox", { name: /trigger|date|time/i }) || document.querySelector("input[type='datetime-local']")).toBeTruthy();
    // AC-211-2.1: IN_APP is implicit, no visible control for it.
    expect(screen.queryByRole("checkbox", { name: /in.?app/i })).not.toBeInTheDocument();
    expect(document.querySelector("input[type='checkbox'][data-channel='IN_APP']")).toBeNull();
    // AC-211-1.1: EMAIL control renders, pre-checked by default (no prefs row).
    const emailCb = await screen.findByRole("checkbox", { name: /email/i });
    expect(emailCb).toBeInTheDocument();
    expect(emailCb.checked).toBe(true);
    expect(screen.getByRole("combobox", { name: /stage/i }) || screen.getByTestId("stage-select")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save|add|create|submit/i })).toBeInTheDocument();
  });
});

describe("CR-UI-011: create form submits valid data", () => {
  it("calls createCustomReminder with matching body and invokes onSuccess", async () => {
    const CREATED = { ...SCHEDULED_REMINDER, title: "My Prep" };
    createCustomReminder.mockResolvedValueOnce(CREATED);
    const onSuccess = vi.fn();
    const user = userEvent.setup();

    render(
      <CustomReminderForm
        applicationId={APP_ID}
        onSuccess={onSuccess}
        onCancel={vi.fn()}
      />
    );

    await user.clear(screen.getByLabelText(/title/i));
    await user.type(screen.getByLabelText(/title/i), "My Prep");

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, FUTURE_DATE);

    // No IN_APP control to click (AC-211-2.1/2.2): it is sourced from internal state and
    // always included in the submitted channels.
    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    await waitFor(() => expect(createCustomReminder).toHaveBeenCalledTimes(1));
    const [arg] = createCustomReminder.mock.calls[0];
    expect(arg.applicationId).toBe(APP_ID);
    expect(arg.title).toBe("My Prep");
    expect(arg.channels).toContain("IN_APP");
    expect(arg.triggerAtUtc).toBeTruthy();

    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
  });
});

describe("CR-UI-012: past date shows validation error", () => {
  it("shows error and does not call API when trigger date is in the past", async () => {
    const user = userEvent.setup();
    renderCreate();

    await user.clear(screen.getByLabelText(/title/i));
    await user.type(screen.getByLabelText(/title/i), "My Prep");

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, PAST_DATE);

    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    await waitFor(() =>
      expect(screen.getByText(/future|past/i)).toBeInTheDocument()
    );
    expect(createCustomReminder).not.toHaveBeenCalled();
  });
});

describe("CR-UI-013: unchecking EMAIL still submits successfully (IN_APP from state guarantees non-empty channels)", () => {
  it("submits with channels=[IN_APP] only when the EMAIL control is unchecked; no validation error", async () => {
    createCustomReminder.mockResolvedValueOnce(SCHEDULED_REMINDER);
    const user = userEvent.setup();
    renderCreate();

    await user.clear(screen.getByLabelText(/title/i));
    await user.type(screen.getByLabelText(/title/i), "My Prep");

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, FUTURE_DATE);

    const emailCb = await screen.findByRole("checkbox", { name: /email/i });
    if (emailCb.checked) await user.click(emailCb);

    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    // AC-211-2.2: IN_APP is always present from state -- "at least one channel" can never
    // be violated through the UI any more, so this submits cleanly with IN_APP only.
    await waitFor(() => expect(createCustomReminder).toHaveBeenCalledTimes(1));
    const [arg] = createCustomReminder.mock.calls[0];
    expect(arg.channels).toEqual(["IN_APP"]);
    expect(screen.queryByTestId("validation-error")).not.toBeInTheDocument();
  });
});

describe("CR-UI-014: blank title shows validation error", () => {
  it("shows error and does not call API when title is blank", async () => {
    const user = userEvent.setup();
    renderCreate();

    const titleInput = screen.getByLabelText(/title/i);
    await user.clear(titleInput);

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, FUTURE_DATE);

    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    await waitFor(() =>
      expect(screen.getByText(/title.*required|required.*title|title.*blank/i)).toBeInTheDocument()
    );
    expect(createCustomReminder).not.toHaveBeenCalled();
  });
});

describe("CR-UI-015: EMAIL channel toggle visible and toggleable", () => {
  it("shows EMAIL toggle and responds to clicks", async () => {
    const user = userEvent.setup();
    renderCreate();

    const emailCheckbox =
      document.querySelector("input[type='checkbox'][data-channel='EMAIL']") ||
      screen.getByRole("checkbox", { name: /email/i });
    expect(emailCheckbox).toBeInTheDocument();

    const before = emailCheckbox.checked;
    await user.click(emailCheckbox);
    expect(emailCheckbox.checked).toBe(!before);
  });
});

describe("CR-UI-016: submitted channels always include IN_APP from state, plus EMAIL when checked", () => {
  it("submits channels containing both IN_APP and EMAIL when the EMAIL control is checked, with no IN_APP control to click", async () => {
    const user = userEvent.setup();
    createCustomReminder.mockResolvedValueOnce(SCHEDULED_REMINDER);
    const onSuccess = vi.fn();

    render(
      <CustomReminderForm
        applicationId={APP_ID}
        onSuccess={onSuccess}
        onCancel={vi.fn()}
      />
    );

    await user.clear(screen.getByLabelText(/title/i));
    await user.type(screen.getByLabelText(/title/i), "My Prep");

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, FUTURE_DATE);

    // AC-211-2.1: no IN_APP control exists to click; only EMAIL is a visible toggle.
    expect(document.querySelector("input[type='checkbox'][data-channel='IN_APP']")).toBeNull();
    const emailCb = await screen.findByRole("checkbox", { name: /email/i });
    if (!emailCb.checked) await user.click(emailCb);
    expect(emailCb.checked).toBe(true);

    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    await waitFor(() => expect(createCustomReminder).toHaveBeenCalledTimes(1));
    const [arg] = createCustomReminder.mock.calls[0];
    expect(arg.channels).toContain("IN_APP");
    expect(arg.channels).toContain("EMAIL");
  });
});

describe("CR-UI-017: API 404 shows error; form stays open", () => {
  it("shows error message and keeps form open on 404", async () => {
    createCustomReminder.mockRejectedValueOnce(
      new ApiError(404, "Not Found", { error: "Not Found", message: "Application not found" })
    );
    const onSuccess = vi.fn();
    const user = userEvent.setup();

    render(
      <CustomReminderForm
        applicationId={APP_ID}
        onSuccess={onSuccess}
        onCancel={vi.fn()}
      />
    );

    await user.clear(screen.getByLabelText(/title/i));
    await user.type(screen.getByLabelText(/title/i), "My Prep");

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, FUTURE_DATE);

    // IN_APP is always in state -- no control to click before submitting.
    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    await waitFor(() => expect(screen.getByTestId("form-error")).toBeInTheDocument());
    expect(onSuccess).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: /save|add|create|submit/i })).not.toBeDisabled();
  });
});

describe("CR-UI-018: API 500 shows generic error", () => {
  it("shows error message on 500 from createCustomReminder", async () => {
    createCustomReminder.mockRejectedValueOnce(
      new ApiError(500, "Server Error", { error: "Server Error", message: "Unexpected error" })
    );
    const user = userEvent.setup();

    renderCreate();

    await user.clear(screen.getByLabelText(/title/i));
    await user.type(screen.getByLabelText(/title/i), "My Prep");

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, FUTURE_DATE);

    // IN_APP is always in state -- no control to click before submitting.
    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    await waitFor(() => expect(screen.getByTestId("form-error")).toBeInTheDocument());
  });
});

describe("CR-UI-019: loading state while in-flight", () => {
  it("disables submit button while createCustomReminder is pending", async () => {
    let resolveCreate;
    createCustomReminder.mockImplementation(
      () => new Promise((resolve) => { resolveCreate = resolve; })
    );
    const user = userEvent.setup();

    renderCreate();

    await user.clear(screen.getByLabelText(/title/i));
    await user.type(screen.getByLabelText(/title/i), "My Prep");

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, FUTURE_DATE);

    // IN_APP is always in state -- no control to click before submitting.
    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    await waitFor(() =>
      expect(screen.getByRole("button", { name: /save|add|create|submit|saving|loading/i })).toBeDisabled()
    );

    await act(async () => { resolveCreate(SCHEDULED_REMINDER); });
  });
});

describe("CR-UI-020: edit form pre-populated", () => {
  it("shows existing reminder note and stage pre-filled, with no Title field and no IN_APP control", async () => {
    renderEdit();

    // AC-EDIT-1/BR-7: the edit form must not expose a Title input at all.
    expect(screen.queryByLabelText(/title/i)).not.toBeInTheDocument();

    expect(screen.getByLabelText(/note/i)).toHaveValue("Old note");

    // AC-211-2.1: IN_APP has no visible control even in edit mode; it is implicit from state.
    expect(document.querySelector("input[type='checkbox'][data-channel='IN_APP']")).toBeNull();
    expect(screen.queryByRole("checkbox", { name: /in.?app/i })).not.toBeInTheDocument();
    // SCHEDULED_REMINDER.channels is ["IN_APP"] only, so EMAIL renders unchecked.
    const emailCb = await screen.findByRole("checkbox", { name: /email/i });
    expect(emailCb.checked).toBe(false);

    const stageEl =
      document.querySelector("[data-testid='stage-select']") ||
      screen.getByRole("combobox", { name: /stage/i });
    expect(stageEl).toHaveValue("INTERVIEW");
  });
});

describe("CR-UI-021: edit form submits updateCustomReminder", () => {
  it("calls updateCustomReminder with the reminder id and body", async () => {
    const UPDATED = { ...SCHEDULED_REMINDER, triggerAtUtc: "2099-07-01T12:00:00Z" };
    updateCustomReminder.mockResolvedValueOnce(UPDATED);
    const onSuccess = vi.fn();
    const user = userEvent.setup();

    renderEdit(SCHEDULED_REMINDER, { onSuccess });

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, "2099-07-01T12:00");

    await user.click(screen.getByRole("button", { name: /save|update|submit/i }));

    await waitFor(() => expect(updateCustomReminder).toHaveBeenCalledTimes(1));
    const [id, body] = updateCustomReminder.mock.calls[0];
    expect(id).toBe(SCHEDULED_REMINDER.id);
    expect(body.triggerAtUtc).toBeTruthy();
    expect(body).not.toHaveProperty("title");
    expect(body.channels).toContain("IN_APP");
    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
  });
});

describe("CR-UI-200: edit form has no Title field; Note/When/Channels/Stage prefilled", () => {
  it("renders no title input/label/read-only display, and no IN_APP control, anywhere in the edit form", async () => {
    renderEdit();

    expect(screen.queryByLabelText(/title/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^title$/i)).not.toBeInTheDocument();
    expect(document.getElementById("cr-title")).toBeNull();

    expect(screen.getByLabelText(/note/i)).toHaveValue("Old note");

    const dtInput = document.querySelector("input[type='datetime-local']");
    expect(dtInput).toBeTruthy();
    expect(dtInput.value).toBeTruthy();

    expect(document.querySelector("input[type='checkbox'][data-channel='IN_APP']")).toBeNull();
    expect(screen.queryByRole("checkbox", { name: /in.?app/i })).not.toBeInTheDocument();
    await screen.findByRole("checkbox", { name: /email/i });

    const stageEl =
      document.querySelector("[data-testid='stage-select']") ||
      screen.getByRole("combobox", { name: /stage/i });
    expect(stageEl).toHaveValue("INTERVIEW");
  });
});

describe("CR-UI-201: edit Save sends a body with no title key at all", () => {
  it("omits title entirely from the updateCustomReminder body, even unchanged", async () => {
    updateCustomReminder.mockResolvedValueOnce(SCHEDULED_REMINDER);
    const user = userEvent.setup();
    renderEdit();

    const noteInput = screen.getByLabelText(/note/i);
    await user.clear(noteInput);
    await user.type(noteInput, "Updated note");

    await user.click(screen.getByRole("button", { name: /save|update|submit/i }));

    await waitFor(() => expect(updateCustomReminder).toHaveBeenCalledTimes(1));
    const [, body] = updateCustomReminder.mock.calls[0];
    expect(Object.keys(body)).not.toContain("title");
    expect(body.note).toBe("Updated note");
  });
});

describe("CR-UI-202: edit Save success path never reads/displays a title from the response", () => {
  it("does not render the response's title anywhere after a successful save", async () => {
    const UPDATED = { ...SCHEDULED_REMINDER, title: "Server-side unchanged title", note: "Updated note" };
    updateCustomReminder.mockResolvedValueOnce(UPDATED);
    const onSuccess = vi.fn();
    const user = userEvent.setup();
    renderEdit(SCHEDULED_REMINDER, { onSuccess });

    const noteInput = screen.getByLabelText(/note/i);
    await user.clear(noteInput);
    await user.type(noteInput, "Updated note");

    await user.click(screen.getByRole("button", { name: /save|update|submit/i }));

    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
    expect(screen.queryByText("Server-side unchanged title")).not.toBeInTheDocument();
    expect(screen.queryByText("Old title")).not.toBeInTheDocument();
  });
});

describe("CR-UI-203: edit Cancel makes no API call", () => {
  it("does not call updateCustomReminder and invokes onCancel when Cancel is clicked", async () => {
    const onCancel = vi.fn();
    const user = userEvent.setup();
    renderEdit(SCHEDULED_REMINDER, { onCancel });

    const noteInput = screen.getByLabelText(/note/i);
    await user.clear(noteInput);
    await user.type(noteInput, "Changed but not saved");

    await user.click(screen.getByRole("button", { name: /cancel/i }));

    expect(updateCustomReminder).not.toHaveBeenCalled();
    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});

describe("CR-UI-204: create form still has a required Title field (regression guard)", () => {
  it("shows the Title field and rejects a blank submission, unaffected by the edit-mode change", async () => {
    const user = userEvent.setup();
    renderCreate();

    const titleInput = screen.getByLabelText(/title/i);
    expect(titleInput).toBeInTheDocument();

    await user.clear(titleInput);
    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, FUTURE_DATE);

    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    await waitFor(() =>
      expect(screen.getByText(/title.*required|required.*title/i)).toBeInTheDocument()
    );
    expect(createCustomReminder).not.toHaveBeenCalled();
  });
});

describe("CR-UI-205: edit form never reads the reminder's title value into the rendered form", () => {
  it("does not render the reminder's title text anywhere in the form, even though the object carries one", () => {
    renderEdit({ ...SCHEDULED_REMINDER, title: "Prepare for system design round" });

    expect(screen.queryByText("Prepare for system design round")).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue("Prepare for system design round")).not.toBeInTheDocument();
  });
});

describe("CR-UI-022: edit cannot move trigger to past", () => {
  it("shows error and does not call API when new trigger is in the past", async () => {
    const user = userEvent.setup();
    renderEdit();

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, PAST_DATE);

    await user.click(screen.getByRole("button", { name: /save|update|submit/i }));

    await waitFor(() =>
      expect(screen.getByText(/future|past/i)).toBeInTheDocument()
    );
    expect(updateCustomReminder).not.toHaveBeenCalled();
  });
});

describe("CR-UI-023: edit 409 shows conflict error", () => {
  it("shows conflict error when API returns 409", async () => {
    updateCustomReminder.mockRejectedValueOnce(
      new ApiError(409, "Conflict", { error: "Conflict", message: "Reminder already fired" })
    );
    const user = userEvent.setup();
    renderEdit();

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, "2099-08-01T10:00");

    await user.click(screen.getByRole("button", { name: /save|update|submit/i }));

    await waitFor(() => expect(screen.getByTestId("form-error")).toBeInTheDocument());
    expect(screen.getByTestId("form-error").textContent).toMatch(/fired|conflict|cannot be edited/i);
  });
});

describe("CR-UI-024: edit 404 shows not-found error", () => {
  it("shows not-found error when API returns 404", async () => {
    updateCustomReminder.mockRejectedValueOnce(
      new ApiError(404, "Not Found", { error: "Not Found", message: "Reminder not found" })
    );
    const user = userEvent.setup();
    renderEdit();

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, "2099-08-01T10:00");

    await user.click(screen.getByRole("button", { name: /save|update|submit/i }));

    await waitFor(() => expect(screen.getByTestId("form-error")).toBeInTheDocument());
    expect(screen.getByTestId("form-error").textContent).toMatch(/not found|404/i);
  });
});

describe("CR-UI-C16 (AC-12): title input wrapped in shared Field with bound label", () => {
  it("title input has class 'input' and is inside a 'field' wrapper with a bound 'field-label'", () => {
    renderCreate();

    const titleInput = screen.getByLabelText(/title/i);
    expect(titleInput).toHaveClass("input");

    const field = titleInput.closest(".field");
    expect(field).toBeTruthy();

    const label = field.querySelector("label.field-label");
    expect(label).toBeTruthy();
    expect(label).toHaveAttribute("for", titleInput.id);
  });
});

describe("CR-UI-C17 (AC-12): note textarea wrapped in shared Field, uses Input's textarea styling", () => {
  it("note textarea has class 'input' and is inside a 'field' wrapper", () => {
    renderCreate();

    const noteInput = screen.getByLabelText(/note/i);
    expect(noteInput.tagName).toBe("TEXTAREA");
    expect(noteInput).toHaveClass("input");

    const field = noteInput.closest(".field");
    expect(field).toBeTruthy();
    const label = field.querySelector("label.field-label");
    expect(label).toBeTruthy();
    expect(label).toHaveAttribute("for", noteInput.id);
  });
});

describe("CR-UI-C18 (AC-12): trigger date/time input wrapped in shared Field, uses Input styling", () => {
  it("the 'When' datetime-local input has class 'input' and sits inside a 'field' wrapper", () => {
    renderCreate();

    const whenInput = document.querySelector("input[type='datetime-local']");
    expect(whenInput).toHaveClass("input");

    const field = whenInput.closest(".field");
    expect(field).toBeTruthy();
    const label = field.querySelector("label.field-label");
    expect(label).toBeTruthy();
    expect(label).toHaveAttribute("for", whenInput.id);
  });
});

describe("CR-UI-C22 (AC-15): client-side validation error uses field-error + role=alert", () => {
  it("renders role=alert, class field-error, no inline red style, next to the title field", async () => {
    const user = userEvent.setup();
    renderCreate();

    const titleInput = screen.getByLabelText(/title/i);
    await user.clear(titleInput);

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, FUTURE_DATE);

    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    const error = await screen.findByText("Title is required.");
    expect(error).toHaveAttribute("role", "alert");
    expect(error).toHaveClass("field-error");
    expect(error.getAttribute("style") || "").not.toMatch(/red/i);

    const titleField = titleInput.closest(".field");
    expect(titleField.contains(error)).toBe(true);
  });
});

describe("CR-UI-C23 (AC-15): API error (409 already-fired) uses field-error + role=alert", () => {
  it("renders role=alert, class field-error, exact copy preserved, no inline red style", async () => {
    updateCustomReminder.mockRejectedValueOnce(
      new ApiError(409, "Conflict", { error: "Conflict", message: "Reminder already fired" })
    );
    const user = userEvent.setup();
    renderEdit();

    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, "2099-08-01T10:00");

    await user.click(screen.getByRole("button", { name: /save|update|submit/i }));

    const error = await screen.findByTestId("form-error");
    expect(error).toHaveAttribute("role", "alert");
    expect(error).toHaveClass("field-error");
    expect(error.getAttribute("style") || "").not.toMatch(/red/i);
    expect(error.textContent).toBe("This reminder has already fired and cannot be edited.");
  });
});

describe("CR-UI-C24 (AC-16): EMAIL channel checkbox uses the app's styled control treatment", () => {
  it("the EMAIL checkbox uses the shared CheckboxToggle primitive's real CSS class, not the unstyled 'checkbox' class; no IN_APP checkbox exists", async () => {
    renderCreate();

    // AC-211-2.1: IN_APP no longer has any checkbox in the DOM.
    expect(document.querySelector("input[type='checkbox'][data-channel='IN_APP']")).toBeNull();

    const emailCb = await screen.findByRole("checkbox", { name: /email/i });

    // The shared primitive (ui.jsx CheckboxToggle) renders the real "toggle-checkbox" class,
    // which has an actual CSS rule (pill + knob, brand-600 on/off colors). The old bare
    // "checkbox" className had no matching CSS rule anywhere in the app -- that's the bug
    // this case guards against, so assert the real class is present and the dead one is gone.
    expect(emailCb).toHaveClass("toggle-checkbox");
    expect(emailCb.className).not.toMatch(/(^|\s)checkbox(\s|$)/);

    // Confirm the class is genuinely styled (not just present, which was the original bug:
    // ".checkbox" had zero matching CSS anywhere in the repo). Read styles.css directly since
    // jsdom does not load stylesheets during component tests.
    const stylesPath = path.join(
      path.dirname(fileURLToPath(import.meta.url)),
      "../../styles/styles.css"
    );
    const css = fs.readFileSync(stylesPath, "utf8");
    expect(css).toMatch(/\.toggle-checkbox\s*\{[^}]*background/);
    expect(css).toMatch(/\.toggle-checkbox:checked\s*\{[^}]*var\(--color-brand-600\)/);
    expect(css).not.toMatch(/(^|\s)\.checkbox\s*\{/m);

    const fieldset = emailCb.closest("fieldset");
    expect(fieldset).toBeTruthy();
    expect(fieldset.className.trim()).not.toBe("");

    // Keeps real checkbox semantics (form participation, .checked, accessible name).
    expect(emailCb).toHaveAttribute("type", "checkbox");
    expect(emailCb).toHaveAccessibleName(/email/i);
  });
});

describe("CR-UI-C25 (AC-16): stage select uses select.input styling", () => {
  it("the stage select has class 'input'", () => {
    renderCreate();

    const stageEl =
      document.querySelector("[data-testid='stage-select']") ||
      screen.getByRole("combobox", { name: /stage/i });
    expect(stageEl.tagName).toBe("SELECT");
    expect(stageEl).toHaveClass("input");
  });
});

/* ── Story #211 / Sub-issue #253: channel gating + Now quick-pick ── */

describe("CR-UI-300 (AC-211-1.1): interviewReminderEmail===true renders EMAIL pre-checked", () => {
  it("shows the EMAIL control pre-checked when prefs say interviewReminderEmail=true", async () => {
    getNotificationPreferences.mockResolvedValueOnce({ interviewReminderEmail: true });
    renderCreate();

    const emailCb = await screen.findByRole("checkbox", { name: /email/i });
    expect(emailCb).toBeInTheDocument();
    expect(emailCb.checked).toBe(true);
  });
});

describe("CR-UI-301 (AC-211-1.2): interviewReminderEmail===false removes EMAIL from the DOM", () => {
  it("does not render the EMAIL control at all (absent from DOM, not merely disabled/unchecked)", async () => {
    getNotificationPreferences.mockResolvedValueOnce({ interviewReminderEmail: false });
    renderCreate();

    await waitFor(() => expect(getNotificationPreferences).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("checkbox", { name: /email/i })).not.toBeInTheDocument();
    expect(document.querySelector("input[type='checkbox'][data-channel='EMAIL']")).toBeNull();
  });
});

describe("CR-UI-302 (AC-211-1.3): no prefs row / field absent defaults to email-allowed=true", () => {
  it("renders EMAIL pre-checked when the prefs response is an empty object (field absent)", async () => {
    getNotificationPreferences.mockResolvedValueOnce({});
    renderCreate();

    const emailCb = await screen.findByRole("checkbox", { name: /email/i });
    expect(emailCb.checked).toBe(true);
  });

  it("renders EMAIL pre-checked when interviewReminderEmail is explicitly null", async () => {
    getNotificationPreferences.mockResolvedValueOnce({ interviewReminderEmail: null });
    renderCreate();

    const emailCb = await screen.findByRole("checkbox", { name: /email/i });
    expect(emailCb.checked).toBe(true);
  });
});

describe("CR-UI-303 (AC-211-1.4): prefs fetch failure defaults to email-allowed=true, form stays usable", () => {
  it("renders EMAIL pre-checked and keeps the form interactive when getNotificationPreferences rejects", async () => {
    getNotificationPreferences.mockRejectedValueOnce(new Error("Network failure"));
    renderCreate();

    const emailCb = await screen.findByRole("checkbox", { name: /email/i });
    expect(emailCb.checked).toBe(true);

    // Form remains fully usable: title input still accepts input, submit button enabled.
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/title/i), "Still works");
    expect(screen.getByLabelText(/title/i)).toHaveValue("Still works");
    expect(screen.getByRole("button", { name: /save|add|create|submit/i })).not.toBeDisabled();
  });
});

describe("CR-UI-304 (AC-211-1.x): EMAIL gate keys specifically on interviewReminderEmail", () => {
  it("ignores weeklyDigestEmail/ghostedAlert and still shows EMAIL when interviewReminderEmail is true", async () => {
    getNotificationPreferences.mockResolvedValueOnce({
      weeklyDigestEmail: false,
      ghostedAlert: false,
      interviewReminderEmail: true,
    });
    renderCreate();

    const emailCb = await screen.findByRole("checkbox", { name: /email/i });
    expect(emailCb.checked).toBe(true);
  });

  it("hides EMAIL when interviewReminderEmail is false even though other email flags are true", async () => {
    getNotificationPreferences.mockResolvedValueOnce({
      weeklyDigestEmail: true,
      ghostedAlert: true,
      interviewReminderEmail: false,
    });
    renderCreate();

    await waitFor(() => expect(getNotificationPreferences).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("checkbox", { name: /email/i })).not.toBeInTheDocument();
  });
});

describe("CR-UI-305 (AC-211-1.x): edit mode also respects the prefs gate for EMAIL", () => {
  it("hides EMAIL in edit mode when interviewReminderEmail is false", async () => {
    getNotificationPreferences.mockResolvedValueOnce({ interviewReminderEmail: false });
    renderEdit();

    await waitFor(() => expect(getNotificationPreferences).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("checkbox", { name: /email/i })).not.toBeInTheDocument();
  });

  it("shows EMAIL pre-checked from the reminder's own channels in edit mode when allowed", async () => {
    getNotificationPreferences.mockResolvedValueOnce({ interviewReminderEmail: true });
    renderEdit({ ...SCHEDULED_REMINDER, channels: ["IN_APP", "EMAIL"] });

    const emailCb = await screen.findByRole("checkbox", { name: /email/i });
    expect(emailCb.checked).toBe(true);
  });
});

describe("CR-UI-310 (AC-211-2.1): no IN_APP checkbox/toggle anywhere in the form", () => {
  it("create mode has zero IN_APP controls", async () => {
    renderCreate();
    await screen.findByRole("checkbox", { name: /email/i });
    expect(screen.queryByRole("checkbox", { name: /in.?app/i })).not.toBeInTheDocument();
    expect(document.querySelector("[data-channel='IN_APP']")).toBeNull();
  });

  it("edit mode has zero IN_APP controls", async () => {
    renderEdit();
    await screen.findByRole("checkbox", { name: /email/i });
    expect(screen.queryByRole("checkbox", { name: /in.?app/i })).not.toBeInTheDocument();
    expect(document.querySelector("[data-channel='IN_APP']")).toBeNull();
  });
});

describe("CR-UI-311 (AC-211-2.2): submitted channels always contain IN_APP with no visible control", () => {
  it("create: channels include IN_APP even when EMAIL is hidden entirely (interviewReminderEmail=false)", async () => {
    getNotificationPreferences.mockResolvedValueOnce({ interviewReminderEmail: false });
    createCustomReminder.mockResolvedValueOnce(SCHEDULED_REMINDER);
    const user = userEvent.setup();
    renderCreate();

    await waitFor(() => expect(getNotificationPreferences).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("checkbox", { name: /email/i })).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/title/i), "No email allowed");
    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, FUTURE_DATE);

    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    await waitFor(() => expect(createCustomReminder).toHaveBeenCalledTimes(1));
    const [arg] = createCustomReminder.mock.calls[0];
    expect(arg.channels).toEqual(["IN_APP"]);
  });

  it("edit: update body channels include IN_APP with no checkbox ever rendered for it", async () => {
    updateCustomReminder.mockResolvedValueOnce(SCHEDULED_REMINDER);
    const user = userEvent.setup();
    renderEdit();
    await screen.findByRole("checkbox", { name: /email/i });

    await user.click(screen.getByRole("button", { name: /save|update|submit/i }));

    await waitFor(() => expect(updateCustomReminder).toHaveBeenCalledTimes(1));
    const [, body] = updateCustomReminder.mock.calls[0];
    expect(body.channels).toContain("IN_APP");
  });
});

describe("CR-UI-320 (AC-211-3.1): a 'Now' quick-pick button is rendered next to the When input", () => {
  it("shows a button named 'Now' adjacent to the When field", () => {
    renderCreate();

    const nowBtn = screen.getByRole("button", { name: /^now$/i });
    expect(nowBtn).toBeInTheDocument();

    const dtInput = document.querySelector("input[type='datetime-local']");
    const whenField = dtInput.closest(".field");
    expect(whenField.contains(nowBtn)).toBe(true);
  });
});

describe("CR-UI-321 (AC-211-3.2): no app-level 'Today' button/control exists", () => {
  it("never renders a control labelled 'Today' anywhere in the form", () => {
    renderCreate();

    expect(screen.queryByRole("button", { name: /^today$/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/^today$/i)).not.toBeInTheDocument();
  });
});

describe("CR-UI-322 (AC-211-3.3): 'Now' sets ~the current instant and passes validation; manual entry unaffected", () => {
  it("clicking Now fills the When input with the current local instant and submits successfully", async () => {
    createCustomReminder.mockResolvedValueOnce(SCHEDULED_REMINDER);
    const user = userEvent.setup();
    renderCreate();

    await user.type(screen.getByLabelText(/title/i), "Right now");

    const before = Date.now();
    await user.click(screen.getByRole("button", { name: /^now$/i }));
    const after = Date.now();

    const dtInput = document.querySelector("input[type='datetime-local']");
    expect(dtInput.value).toBeTruthy();
    // The datetime-local input has minute precision; parse back and confirm it lands
    // within a generous window around the click (no seconds component to compare exactly).
    const parsed = new Date(dtInput.value).getTime();
    expect(parsed).toBeGreaterThanOrEqual(before - 60000);
    expect(parsed).toBeLessThanOrEqual(after + 60000);

    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    // "Now" must pass the existing future-trigger validation (no "past" error raised).
    await waitFor(() => expect(createCustomReminder).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId("validation-error")).not.toBeInTheDocument();
  });

  it("manual entry of the When field still works unchanged after Now exists", async () => {
    createCustomReminder.mockResolvedValueOnce(SCHEDULED_REMINDER);
    const user = userEvent.setup();
    renderCreate();

    await user.type(screen.getByLabelText(/title/i), "Manual entry");
    const dtInput = document.querySelector("input[type='datetime-local']");
    await user.clear(dtInput);
    await user.type(dtInput, FUTURE_DATE);
    expect(dtInput.value).toBe(FUTURE_DATE);

    await user.click(screen.getByRole("button", { name: /save|add|create|submit/i }));

    await waitFor(() => expect(createCustomReminder).toHaveBeenCalledTimes(1));
    const [arg] = createCustomReminder.mock.calls[0];
    expect(arg.triggerAtUtc).toBeTruthy();
  });
});
