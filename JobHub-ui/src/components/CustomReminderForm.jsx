// CustomReminderForm: create or edit a custom reminder.
// Story #134 / Sub-issue #158.
// Story #175 / Sub-issue #201: restyled with the shared design-system primitives
// (Field/Input/Button) so it matches Applications.jsx's EditApplicationModal pattern.
// Story #207 / Ticket #216: the edit form is body-only. Title is create-time-only
// (AC-EDIT-1/AC-EDIT-4, BR-6/BR-7): the Title field never renders in edit mode, it is
// never read from the reminder object, and the update body never carries a `title`
// key, not even unchanged. The create form's Title field is unaffected.
// Story #211 / Sub-issue #253: channels are no longer two free-standing checkboxes.
// IN_APP is implicit and non-deselectable (always sourced from internal state, never a
// visible control, AC-211-2.x). EMAIL is gated on the user's notification preferences
// (`interviewReminderEmail`, the SAME field the backend dispatch gate uses) and is
// entirely absent from the DOM when that preference is false (AC-211-1.x). A "Now"
// quick-pick button next to the When input sets the trigger to the current instant
// (AC-211-3.x).
//
// Props:
//   applicationId (string, required)  : the application this reminder belongs to
//   reminder (object, optional)       : when set, renders in edit mode
//   onSuccess (fn)                    : called after successful create or update
//   onCancel (fn)                     : called when user cancels
//   footerRef (ref, optional)         : when provided, the submit/cancel buttons are
//                                       portaled into footerRef.current (e.g. a Modal's
//                                       footer slot) instead of rendering inline below
//                                       the fields. When omitted, buttons render inline
//                                       so the form still works stand-alone (existing tests).
import React from "react";
import ReactDOM from "react-dom";
import { createCustomReminder, updateCustomReminder } from "../api/custom-reminders.js";
import { getNotificationPreferences } from "../api/notifications.js";
import { Field, Input, Button, CheckboxToggle } from "./ui.jsx";

const STAGES = ["", "SCREENING", "INTERVIEW", "OFFER"];
const STAGE_LABEL = { SCREENING: "Screening", INTERVIEW: "Interview", OFFER: "Offer" };

function toLocalDateTimeInputValue(isoUtc) {
  if (!isoUtc) return "";
  try {
    const d = new Date(isoUtc);
    if (isNaN(d.getTime())) return "";
    const pad = (n) => String(n).padStart(2, "0");
    return (
      `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
      `T${pad(d.getHours())}:${pad(d.getMinutes())}`
    );
  } catch {
    return "";
  }
}

function toUtcIsoString(localDateTimeValue) {
  if (!localDateTimeValue) return "";
  try {
    return new Date(localDateTimeValue).toISOString();
  } catch {
    return "";
  }
}

/** Current instant formatted for an <input type="datetime-local"> value (AC-211-3.3). */
function nowAsLocalDateTimeInputValue() {
  return toLocalDateTimeInputValue(new Date().toISOString());
}

function errorMessageFor(err, statusMessages) {
  if (err && err.status && statusMessages[err.status]) {
    return statusMessages[err.status];
  }
  if (err && err.message) return err.message;
  return "An unexpected error occurred. Please try again.";
}

export function CustomReminderForm({ applicationId, reminder, onSuccess, onCancel, footerRef }) {
  const isEdit = Boolean(reminder);

  // AC-EDIT-1/BR-7: edit mode never reads the reminder's title into form state.
  const [title, setTitle] = React.useState("");
  const [note, setNote] = React.useState(reminder?.note ?? "");
  const [triggerLocal, setTriggerLocal] = React.useState(
    () => toLocalDateTimeInputValue(reminder?.triggerAtUtc)
  );
  // AC-211-2.2: channels always carry IN_APP from internal state -- it is never sourced
  // from a visible control. EMAIL starts pre-checked by default (create) or from the
  // existing reminder's channels (edit); the prefs effect below only ever *removes* EMAIL
  // (when disallowed) -- it never re-adds it once mounted, so a user's own toggle is never
  // clobbered by the prefs fetch resolving later (AC-211-1.x).
  const [channels, setChannels] = React.useState(() => {
    const next = new Set(["IN_APP"]);
    if (isEdit ? reminder?.channels?.includes("EMAIL") : true) next.add("EMAIL");
    return next;
  });
  const [stage, setStage] = React.useState(reminder?.stage ?? "");

  // AC-211-1.x: EMAIL is gated on interviewReminderEmail -- the SAME field the backend
  // CustomReminderDispatchService gates EMAIL dispatch on. Default true (no row / field
  // absent / fetch failure all resolve to email-allowed) so the form stays usable.
  const [emailAllowed, setEmailAllowed] = React.useState(true);

  React.useEffect(() => {
    let cancelled = false;
    getNotificationPreferences()
      .then((prefs) => {
        if (cancelled) return;
        const allowed = prefs?.interviewReminderEmail !== false;
        setEmailAllowed(allowed);
        if (!allowed) {
          setChannels((prev) => {
            if (!prev.has("EMAIL")) return prev;
            const next = new Set(prev);
            next.delete("EMAIL");
            return next;
          });
        }
      })
      .catch(() => {
        if (!cancelled) setEmailAllowed(true);
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const [validationError, setValidationError] = React.useState("");
  const [apiError, setApiError] = React.useState("");
  const [loading, setLoading] = React.useState(false);

  function toggleChannel(channel) {
    setChannels((prev) => {
      const next = new Set(prev);
      if (next.has(channel)) next.delete(channel);
      else next.add(channel);
      return next;
    });
  }

  function setNow() {
    setTriggerLocal(nowAsLocalDateTimeInputValue());
  }

  // datetime-local has minute precision (no seconds), so a value picked via "Now"
  // (AC-211-3.3) always truncates to the start of the current minute and can read as
  // a few seconds in the past by the time validate() runs. Grace window absorbs that
  // truncation/round-trip without opening the door to genuinely past values.
  const NOW_GRACE_MS = 60_000;

  function validate() {
    // AC-EDIT-4: Title is required only on create; the edit form has no Title field at all.
    if (!isEdit && !title.trim()) return "Title is required.";
    if (channels.size === 0) return "At least one channel is required.";
    if (!triggerLocal) return "Trigger date and time is required.";
    const triggerDate = new Date(triggerLocal);
    if (isNaN(triggerDate.getTime())) return "Invalid date and time.";
    if (triggerDate.getTime() <= Date.now() - NOW_GRACE_MS) return "Trigger must be in the future.";
    return "";
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setApiError("");

    const vErr = validate();
    if (vErr) {
      setValidationError(vErr);
      return;
    }
    setValidationError("");
    setLoading(true);

    const triggerAtUtc = toUtcIsoString(triggerLocal);
    // AC-EDIT-2/BR-6: the update body never carries a `title` key, not even unchanged.
    const body = isEdit
      ? { triggerAtUtc, channels: Array.from(channels) }
      : { title: title.trim(), triggerAtUtc, channels: Array.from(channels) };
    if (note.trim()) body.note = note.trim();
    if (stage) body.stage = stage;

    try {
      if (isEdit) {
        const result = await updateCustomReminder(reminder.id, body);
        onSuccess(result);
      } else {
        body.applicationId = applicationId;
        const result = await createCustomReminder(body);
        onSuccess(result);
      }
    } catch (err) {
      const STATUS_MESSAGES = {
        404: "Not found or not yours.",
        409: "This reminder has already fired and cannot be edited.",
        400: err?.message || "Invalid input.",
      };
      setApiError(errorMessageFor(err, STATUS_MESSAGES));
      setLoading(false);
    }
  }

  const actions = (
    <>
      <Button type="button" variant="ghost" onClick={onCancel} disabled={loading}>
        Cancel
      </Button>
      <Button type="submit" form="custom-reminder-form" variant="primary" disabled={loading}>
        {loading ? "Saving..." : isEdit ? "Save" : "Add reminder"}
      </Button>
    </>
  );

  return (
    <form
      id="custom-reminder-form"
      onSubmit={handleSubmit}
      aria-label={isEdit ? "Edit reminder" : "Add reminder"}
      style={{ display: "flex", flexDirection: "column", gap: 14 }}
    >
      {!isEdit && (
        <Field label="Title" htmlFor="cr-title">
          <Input
            id="cr-title"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={200}
            disabled={loading}
          />
          {validationError === "Title is required." && (
            <div role="alert" data-testid="validation-error" className="field-error">
              {validationError}
            </div>
          )}
        </Field>
      )}

      <Field label="Note (optional)" htmlFor="cr-note">
        <textarea
          id="cr-note"
          className="input"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          maxLength={2000}
          disabled={loading}
          style={{ width: "100%", overflowWrap: "anywhere", wordBreak: "break-word" }}
        />
      </Field>

      <Field label="When" htmlFor="cr-trigger">
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <Input
            id="cr-trigger"
            type="datetime-local"
            value={triggerLocal}
            onChange={(e) => setTriggerLocal(e.target.value)}
            disabled={loading}
            style={{ flex: 1 }}
          />
          <Button type="button" variant="ghost" size="sm" onClick={setNow} disabled={loading}>
            Now
          </Button>
        </div>
      </Field>

      {/* AC-211-2.1: IN_APP is implicit and non-deselectable -- no visible control for it,
          it is always sourced from internal state on submit. AC-211-1.x: EMAIL only renders
          when the user's notification preferences allow it (interviewReminderEmail !== false). */}
      {emailAllowed && (
        <fieldset className="field" style={{ border: "none", padding: 0, margin: 0 }}>
          <legend className="field-label" style={{ padding: 0 }}>Channels</legend>
          <div style={{ display: "flex", gap: 16 }}>
            <label className="checkbox-label" style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <CheckboxToggle
                data-channel="EMAIL"
                checked={channels.has("EMAIL")}
                onChange={() => toggleChannel("EMAIL")}
                disabled={loading}
                aria-label="Email"
              />
              Email
            </label>
          </div>
        </fieldset>
      )}

      <Field label="Stage" htmlFor="cr-stage">
        <select
          id="cr-stage"
          className="input"
          data-testid="stage-select"
          value={stage}
          onChange={(e) => setStage(e.target.value)}
          disabled={loading}
        >
          <option value="">-- none --</option>
          {STAGES.filter(Boolean).map((s) => (
            <option key={s} value={s}>{STAGE_LABEL[s] ?? s}</option>
          ))}
        </select>
      </Field>

      {validationError && validationError !== "Title is required." && (
        <div role="alert" data-testid="validation-error" className="field-error">
          {validationError}
        </div>
      )}

      {apiError && (
        <div role="alert" data-testid="form-error" className="field-error">
          {apiError}
        </div>
      )}

      {footerRef?.current
        ? ReactDOM.createPortal(actions, footerRef.current)
        : <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>{actions}</div>}
    </form>
  );
}

export default CustomReminderForm;
