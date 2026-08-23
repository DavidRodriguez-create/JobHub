// CustomReminderList: displays custom reminders for one application (per-app) or globally.
// Story #134 / Sub-issue #158.
// Story #175 / Sub-issue #200, #201: three-state UX (loading/empty/error) with exact copy.
//
// Time-zone display rule (ADR 0011 addendum A6):
//   triggerAtUtc is rendered as an absolute locale date+time in the user's local TZ using
//   Intl.DateTimeFormat, producing strings like "Mon 22 Jun, 14:30". No relative phrasing.
//
// Delete UX (ADR 0011 addendum A5):
//   404 on delete: silent refetch + row removal, no error toast.
//   409 on delete: inline error, row stays.
//   Other errors: inline error, row stays.
//
// State rule (PDA spec section 2, story #175): state is derived strictly from the HTTP outcome.
// 200 + content.length === 0 is always empty, never error. Any non-2xx status or thrown
// network error (no .status) is always error, never empty. 401 is a carve-out (onLogout).
//
// Props:
//   applicationId (string, optional)  : when set, uses listCustomRemindersByApplication
//   includeFired (boolean, optional)  : include FIRED/CANCELLED reminders
//   onLogout (fn)                     : called on 401
//   onAddReminder (fn, optional)      : callback to open add form
//   onEditReminder (fn, optional)     : callback to open edit form with (reminder)
import React from "react";
import Icon from "./Icon.jsx";
import { Button, Empty } from "./ui.jsx";
import {
  listMyCustomReminders,
  listCustomRemindersByApplication,
  deleteCustomReminder,
} from "../api/custom-reminders.js";

// Story #210 / Sub-issue #247: reminder status is a distinct, three-value vocabulary
// (SCHEDULED/FIRED/CANCELLED) that does not belong in StatusPill's application-status
// STATUS_LABEL map (see PDA spec section 4). Kept component-local since nothing else
// outside this list renders a reminder status today.
const REMINDER_STATUS_LABEL = { SCHEDULED: "Scheduled", FIRED: "Fired", CANCELLED: "Cancelled" };
const REMINDER_STATUS_CLASS = {
  SCHEDULED: "scheduled", FIRED: "fired", CANCELLED: "cancelled",
};
function ReminderStatusPill({ status }) {
  const modifier = REMINDER_STATUS_CLASS[status] || "";
  return (
    <span data-testid="reminder-status" className={`reminder-status-pill ${modifier}`}>
      <span className="dot" />
      {REMINDER_STATUS_LABEL[status] || status}
    </span>
  );
}

const DATE_FORMAT = new Intl.DateTimeFormat(undefined, {
  weekday: "short",
  day: "numeric",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
});

function formatTrigger(isoUtc) {
  if (!isoUtc) return "";
  try {
    const d = new Date(isoUtc);
    if (isNaN(d.getTime())) return "";
    return DATE_FORMAT.format(d);
  } catch {
    return "";
  }
}

const STAGE_LABEL = { SCREENING: "Screening", INTERVIEW: "Interview", OFFER: "Offer" };

function ReminderItem({ reminder, onDelete, onEdit }) {
  const [confirming, setConfirming] = React.useState(false);
  const [deleteError, setDeleteError] = React.useState("");
  const [deleting, setDeleting] = React.useState(false);

  function handleDeleteClick() {
    setConfirming(true);
    setDeleteError("");
  }

  function handleConfirmCancel() {
    setConfirming(false);
  }

  async function handleConfirmDelete() {
    setDeleting(true);
    setConfirming(false);
    try {
      await onDelete(reminder.id);
    } catch (err) {
      if (err && err.status !== 404) {
        setDeleteError(
          err.status === 409
            ? "This reminder has already fired and cannot be cancelled."
            : err.message || "Could not delete. Please try again."
        );
      }
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div data-testid="reminder-item" className="reminder-row">
      <div className="reminder-row-body">
        <div className="reminder-row-identity">
          <span className="reminder-row-title">{reminder.title}</span>
          {reminder.stage && (
            <span data-testid="reminder-stage" className="reminder-stage-pill">
              {STAGE_LABEL[reminder.stage] ?? reminder.stage}
            </span>
          )}
          <ReminderStatusPill status={reminder.status} />
        </div>
        {reminder.note && <div data-testid="reminder-note" className="reminder-row-note">{reminder.note}</div>}
        <div className="reminder-row-meta">
          <span data-testid="reminder-trigger-time" className="reminder-row-meta-item">
            <Icon name="calendar" size={12} />
            {formatTrigger(reminder.triggerAtUtc)}
          </span>
          <span data-testid="reminder-channels" className="reminder-row-meta-item">
            {(reminder.channels || []).join(", ")}
          </span>
        </div>

        {confirming && (
          <div data-testid="delete-confirm" className="reminder-row-delete-confirm">
            <span>Delete this reminder?</span>
            <Button variant="danger" size="sm" icon="trash" data-testid="confirm-delete" onClick={handleConfirmDelete}>
              Confirm
            </Button>
            <Button variant="ghost" size="sm" data-testid="cancel-delete" onClick={handleConfirmCancel}>
              Cancel
            </Button>
          </div>
        )}

        {deleteError && (
          <div role="alert" data-testid="delete-error" className="reminder-row-error">
            {deleteError}
          </div>
        )}
      </div>

      {!confirming && (
        <div className="reminder-row-actions">
          {onEdit && (
            <Button
              variant="ghost"
              size="sm"
              icon="notebook-pen"
              data-testid="reminder-edit-btn"
              onClick={() => onEdit(reminder)}
              disabled={deleting}
              aria-label={`Edit reminder: ${reminder.title}`}
            >
              Edit
            </Button>
          )}

          <Button
            variant="ghost"
            size="sm"
            icon="trash"
            data-testid="reminder-delete-btn"
            onClick={handleDeleteClick}
            disabled={deleting}
            aria-label={`Delete reminder: ${reminder.title}`}
            style={{ color: "var(--color-danger)" }}
          >
            Delete
          </Button>
        </div>
      )}
    </div>
  );
}

export function CustomReminderList({ applicationId, includeFired = false, onLogout, onAddReminder, onEditReminder }) {
  const [state, setState] = React.useState("loading");
  const [reminders, setReminders] = React.useState([]);

  const fetchReminders = React.useCallback(async () => {
    setState("loading");
    try {
      let result;
      if (applicationId) {
        result = await listCustomRemindersByApplication(applicationId, { includeFired });
      } else {
        result = await listMyCustomReminders({ includeFired });
      }
      setReminders((result && result.content) || []);
      setState("ready");
    } catch (err) {
      if (err && err.status === 401) {
        onLogout?.();
        return;
      }
      setState("error");
    }
  }, [applicationId, includeFired, onLogout]);

  React.useEffect(() => {
    fetchReminders();
  }, [fetchReminders]);

  async function handleDelete(id) {
    try {
      await deleteCustomReminder(id);
      setReminders((prev) => prev.filter((r) => r.id !== id));
      fetchReminders();
    } catch (err) {
      if (err && err.status === 401) {
        onLogout?.();
        return;
      }
      if (err && err.status === 404) {
        setReminders((prev) => prev.filter((r) => r.id !== id));
        fetchReminders();
        return;
      }
      throw err;
    }
  }

  if (state === "loading") {
    return (
      <div data-testid="reminders-loading">
        <Icon name="clock" size={16} />
        Loading reminders...
      </div>
    );
  }

  if (state === "error") {
    return (
      <div data-testid="reminders-error" role="alert">
        <Empty
          icon="alert-circle"
          className="danger"
          title="We could not load reminders. Check your connection and try again."
          cta={<Button variant="ghost" size="sm" onClick={fetchReminders}>Retry</Button>}
        />
      </div>
    );
  }

  if (reminders.length === 0) {
    return (
      <div data-testid="reminders-empty">
        <Empty
          icon="bell"
          title="No reminders yet"
          desc="Add a reminder so you do not lose track of this application."
          cta={onAddReminder && (
            <Button variant="primary" size="sm" onClick={onAddReminder}>Add reminder</Button>
          )}
        />
      </div>
    );
  }

  return (
    <div data-testid="reminders-list" className="reminders-list-body">
      {onAddReminder && (
        <div className="reminders-list-actions">
          <Button variant="secondary" size="sm" icon="plus" onClick={onAddReminder} data-testid="add-reminder-btn">
            Add reminder
          </Button>
        </div>
      )}
      {reminders.map((r) => (
        <ReminderItem
          key={r.id}
          reminder={r}
          onDelete={handleDelete}
          onEdit={onEditReminder}
        />
      ))}
    </div>
  );
}

export default CustomReminderList;
