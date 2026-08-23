/**
 * AdminPage: admin-only trigger panel.
 *
 * Gated on account.isAdmin (from parent App); never rendered for non-admins
 * or unauthenticated users.
 *
 * Renders:
 *  - Status panel (GET /jobs/admin/triggers/status): triggerEnabled, twoFactorRequired,
 *    per-kind last-run info (never-run / queued / running / succeeded / failed /
 *    cancel_requested / cancelled).
 *  - Trigger buttons for "crawl" and "enrichment".
 *  - Stop buttons for "crawl" and "enrichment" (visible while queued/running),
 *    calling POST /jobs/admin/triggers/{kind}/cancel (story #58).
 *  - 2FA gate (story #384): when twoFactorRequired=true (the admin's own account has
 *    2FA enabled), each kind panel shows a code input inline with the trigger action,
 *    no separate request step; when false, the trigger fires with a single click.
 *  - Auto-polls while a run is queued, running, or cancel_requested (every 5 s),
 *    or the user can click Refresh.
 */
import React from "react";
import { getAdminTriggerStatus, triggerAdminPass, cancelAdminTrigger } from "../api/jobs.js";
import * as UI from "../components/ui.jsx";
import { relativeTime, freshnessRelativeTime, duration } from "../lib/timeFormat.js";

const { Button } = UI;

const POLL_INTERVAL_MS = 5000;

// How often the freshness line's own display-only tick fires (BR-5): keeps
// "Updated Xs ago" advancing between refreshes, independent of the data-fetch
// interval. Whole seconds, so this matches BR-7's rounding grain exactly.
const FRESHNESS_TICK_MS = 1000;

// Statuses for which the 5s auto-poll keeps running (BR-6): the pass still
// has work in progress (queued/running) or is winding down (cancel_requested).
const POLL_ELIGIBLE_STATUSES = ["queued", "running", "cancel_requested"];

// Terminal statuses for which a duration line is shown (BR-8), matching the
// statuses already established as terminal in story #58.
const TERMINAL_STATUSES = ["succeeded", "failed", "cancelled"];

// Story #398 (ADR 0032): maps a finished run's origin to its admin-facing label.
// Rows predating ADR 0032 have no origin recorded and the contract has
// job-service default them to "manual" (TriggerRequestMapperTest C28), so this
// map never needs a fallback branch of its own.
const ORIGIN_LABELS = { scheduled: "Automatic", manual: "Manual" };

// Story #398 (AC-6): a crawl that found nothing ends succeeded/no_targets and
// must always read as "no more targets to crawl", never a parsed
// resultSummary like "crawled 0 targets". Other outcomes fall back to
// resultSummary/errorReason, since only no_targets has a fixed UI phrase.
const NO_TARGETS_TEXT = "no more targets to crawl";

// How long a success/error toast stays visible before auto-dismissing.
export const TOAST_DISMISS_MS = 5000;

// Formats an ISO datetime string to a locale-friendly short form, e.g. "6 Jun 2026, 10:00".
function fmtDateTime(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * A single trigger kind section: shows last-run status + a trigger button.
 * Props:
 *  - kind: "crawl" | "enrichment"
 *  - label: string, e.g. "Crawl"
 *  - runInfo: TriggerRunInfo | null
 *  - triggerEnabled: boolean
 *  - twoFactorRequired: boolean, this admin's own 2FA state (story #384)
 *  - onTrigger(kind): called when the user fires the trigger (no-2FA path)
 *  - onTriggerWithCode(kind, code): called when the user fires the trigger with
 *    their own TOTP/backup code (2FA-enrolled path)
 *  - busy: boolean, true while the trigger POST is in flight
 *  - onCancel(kind): called when the user clicks Stop
 *  - cancelBusy: boolean, true while the cancel POST is in flight
 *  - requesting: boolean, story #398 (AC-11): true only for the UI-local phase
 *    between the click and the 202 response plus its follow-up status refetch;
 *    there is no server-side "requesting" status, so this never comes from runInfo
 *  - forceDisabled: boolean, story #398 (AC-12): true once a trigger POST has
 *    been rejected as disabled (403), so the button stays visibly disabled
 *    without waiting for the next status refetch to catch up
 *  - lastRun: TriggerLastRun | null, story #398 (AC-9/AC-10): the most recent
 *    FINISHED run of this kind, whatever started it (distinct from `runInfo`,
 *    which is the most recent request and may still be in flight)
 */
function TriggerKindPanel({ kind, label, runInfo, triggerEnabled, twoFactorRequired, onTrigger, onTriggerWithCode, busy, onCancel, cancelBusy, requesting, forceDisabled, lastRun }) {
  const [codeInput, setCodeInput] = React.useState("");
  const [codeError, setCodeError] = React.useState(null);
  const [codeSubmitting, setCodeSubmitting] = React.useState(false);

  async function handleSubmitCode() {
    if (!codeInput) return; // AC-12/TC-384-U5: empty code never reaches the API
    setCodeSubmitting(true);
    setCodeError(null);
    try {
      await onTriggerWithCode(kind, codeInput);
      // success: reset for the next trigger
      setCodeInput("");
    } catch (e) {
      if (e && (e.status === 422 || e.status === 400)) {
        setCodeError((e && e.message) || "Verification required. Check your code and try again.");
      } else if (e && e.status === 429) {
        setCodeError("Too many requests, try again later.");
      } else {
        setCodeError((e && e.message) || "Trigger failed.");
      }
    } finally {
      setCodeSubmitting(false);
    }
  }

  // When trigger is disabled: show disabled state + message (handled by parent, but we also
  // check here so the button is visually disabled even inside the panel).
  const isRunning = runInfo && runInfo.status === "running";
  const isQueued = runInfo && runInfo.status === "queued";
  // Story #398 follow-up (ticket #565 reopen): a manual trigger fired while a
  // run of this kind is RUNNING is now accepted server-side and lands as
  // `queued` (job-service #564, in parallel), so `isRunning` no longer blocks
  // the button. `isQueued` still blocks it: the DB partial unique index only
  // ever allows one running plus one queued per kind, so a second `queued`
  // request still gets a 409.
  const canTrigger = triggerEnabled && !isQueued && !busy && !forceDisabled;
  // Stop is offered iff the kind is queued or running (BR-3). Not subject to
  // triggerEnabled/twoFactorRequired gates (BR-9/BR-384-7).
  const canCancel = (isRunning || isQueued) && !cancelBusy;

  return (
    <section
      data-testid={`kind-panel-${kind}`}
      className="card admin-kind-panel"
    >
      <div className="card-pad">
        <h3>{label}</h3>

        {/* Requesting: story #398 AC-11, the UI-local phase between the click
            and the 202 response (plus its follow-up status refetch). Takes
            over the current-run display entirely; there is no persisted
            "requesting" status to merge with runInfo. */}
        {requesting ? (
          <p data-testid={`trigger-requesting-${kind}`} className="admin-run-info admin-requesting">
            Status: <strong>Requesting…</strong>
          </p>
        ) : (
          <RunInfoDisplay runInfo={runInfo} />
        )}

        {/* Last finished run: story #398 AC-9/AC-10, whatever kind started it
            (scheduled or manual), independent of the current-run block above. */}
        <LastRunDisplay kind={kind} label={label} lastRun={lastRun} />

        {/* Stop button: visible iff status is "queued" or "running" (BR-3) */}
        {(isRunning || isQueued) && (
          <Button
            data-testid={`stop-btn-${kind}`}
            variant="danger"
            size="sm"
            onClick={() => onCancel(kind)}
            disabled={!canCancel}
            style={{ marginTop: 10, marginRight: 8 }}
          >
            {cancelBusy ? "Stopping…" : "Stop"}
          </Button>
        )}

        {/* Trigger disabled banner (rendered inside panel too) */}
        {!triggerEnabled && (
          <p
            data-testid="trigger-disabled-message"
            className="admin-trigger-disabled-message"
          >
            Triggering is currently disabled by deployment configuration.
          </p>
        )}

        {/* 2FA gate (story #384): the admin's own account has 2FA enabled, so a
            code (TOTP or backup) is entered inline with the trigger action. Only
            offered while there is actually something to trigger: not while a
            pass of this kind is already queued (mirroring the direct trigger
            button below), but a RUNNING pass no longer blocks a manual request
            (story #398 reopen, ticket #565), since it is accepted as queued. */}
        {triggerEnabled && twoFactorRequired && !isQueued && (
          <div className="admin-code-gate">
            <div className="admin-code-entry">
              <div>
                <p style={{ fontSize: 13, margin: "0 0 4px" }}>
                  Enter your 6-digit authenticator code, or an 8-character backup code:
                </p>
                <input
                  data-testid={`code-input-${kind}`}
                  type="text"
                  maxLength={8}
                  placeholder="123456"
                  value={codeInput}
                  onChange={(e) => setCodeInput(e.target.value.replace(/[^0-9a-zA-Z]/g, "").slice(0, 8))}
                  className="input"
                  style={{ width: 120, fontFamily: "var(--font-mono)" }}
                />
                {codeError && (
                  <p role="alert" data-testid={`code-error-${kind}`} className="admin-error-text">
                    {codeError}
                  </p>
                )}
              </div>
              <Button
                data-testid={`submit-code-btn-${kind}`}
                onClick={handleSubmitCode}
                disabled={!codeInput || codeSubmitting || !canTrigger}
              >
                {codeSubmitting ? "Triggering…" : `Trigger ${label}`}
              </Button>
            </div>
          </div>
        )}

        {/* Direct trigger: this admin has no 2FA enabled, so the trigger fires
            with a single click and no code step at all. */}
        {triggerEnabled && !twoFactorRequired && (
          <Button
            data-testid={`trigger-btn-${kind}`}
            onClick={() => onTrigger(kind)}
            disabled={!canTrigger}
            style={{ marginTop: 10 }}
          >
            {busy ? "Triggering…" : `Trigger ${label}`}
          </Button>
        )}
      </div>
    </section>
  );
}

/**
 * LastRunDisplay: the most recent FINISHED run of one kind (story #398,
 * ADR 0032, AC-9/AC-10), whatever started it. Distinct from RunInfoDisplay
 * above, which reflects the most recent *request* and may still be in
 * flight (queued/running).
 *
 * Props:
 *  - kind: "crawl" | "enrichment"
 *  - label: string, e.g. "Crawl"
 *  - lastRun: TriggerLastRun | null. `null` means this kind has never had a
 *    finished run (AC-10, also true for pre-story deployments and pre-ADR
 *    0032 rows once mapped): shown as an empty state, never an error.
 */
function LastRunDisplay({ kind, label, lastRun }) {
  if (!lastRun) {
    return (
      <p data-testid={`last-run-${kind}-empty`} className="admin-last-run-empty">
        No run history yet for {label.toLowerCase()}.
      </p>
    );
  }

  const { finishedAt, status, outcome, origin, resultSummary, errorReason } = lastRun;
  // Pre-ADR-0032 rows carry no origin; the contract has job-service default
  // them to "manual" before this ever reaches the UI (edge case in the
  // acceptance doc), so ORIGIN_LABELS never needs its own fallback branch.
  const originLabel = ORIGIN_LABELS[origin] ?? "Manual";
  // AC-6: no_targets always reads as the fixed phrase, regardless of what
  // resultSummary happens to contain, never a parsed "crawled 0 targets".
  const outcomeText =
    outcome === "no_targets"
      ? NO_TARGETS_TEXT
      : status === "failed"
        ? errorReason || "Failed"
        : resultSummary || outcome || status;

  return (
    <div data-testid={`last-run-${kind}`} className="admin-last-run">
      <span
        data-testid={`last-run-${kind}-when`}
        className="meta"
        title={fmtDateTime(finishedAt) ?? undefined}
      >
        Last {label.toLowerCase()}: {relativeTime(finishedAt) ?? fmtDateTime(finishedAt)}
      </span>
      <span data-testid={`last-run-${kind}-origin`} className="admin-last-run-origin">
        {originLabel}
      </span>
      <span data-testid={`last-run-${kind}-outcome`} className="admin-last-run-outcome">
        {outcomeText}
      </span>
    </div>
  );
}

// Maps raw status values to their admin-facing display label.
// `cancel_requested` and `cancelled` get human-readable labels per spec §5;
// other statuses are shown as-is.
const STATUS_LABELS = {
  cancel_requested: "Cancelling…",
  cancelled: "Cancelled",
};

function RunInfoDisplay({ runInfo }) {
  if (!runInfo) {
    return (
      <p data-testid="run-info-never" className="admin-run-info-never">
        Never run.
      </p>
    );
  }

  const { status, finishedAt, resultSummary, errorReason, requestedAt, progress, origin } = runInfo;
  const statusLabel = STATUS_LABELS[status] ?? status;
  // "cancel_requested" still has work in progress (the crawler hasn't
  // finished its current item yet), so it gets the same "in progress"
  // visual treatment as "running".
  const inProgress = status === "running" || status === "cancel_requested";
  // Story #398 reopen (ticket #565): the in-progress run must show whether it
  // is the automatic scheduled pass or an admin's own manual click, not just
  // LastRunDisplay's finished-run block, otherwise "running (in progress...)"
  // reads the same whoever started it, exactly the confusion the reporter
  // raised. Reuses the same origin labels as LastRunDisplay.
  const showOrigin = inProgress && !!origin;

  // Duration line: only for terminal runs, with both timestamps present and
  // parseable, never negative (BR-8/BR-9/BR-10).
  const isTerminal = TERMINAL_STATUSES.includes(status);
  const durationText = isTerminal ? duration(requestedAt, finishedAt) : null;

  // Story #513 (BR-513-1/2/6/7): the live progress detail is additive, shown
  // only while the run is still in progress. `progress` is a normal `null`
  // (not an error) for a queued run, a running run before its first report,
  // every enrichment run, and every pre-feature run (also treat a missing
  // `progress` key the same way, AC-513-20). The instant status becomes
  // terminal, this hands off entirely to resultSummary/errorReason below.
  const showProgress = inProgress && !!progress;

  return (
    <div data-testid="run-info" className="admin-run-info">
      <span data-testid="run-status">
        Status: <strong>{statusLabel}</strong>
        {inProgress && (
          <span data-testid="running-indicator" className="running-indicator">
            (in progress…)
          </span>
        )}
        {showOrigin && (
          <span data-testid="run-origin" className="admin-run-origin">
            {ORIGIN_LABELS[origin] ?? "Manual"}
          </span>
        )}
      </span>
      {showProgress && (
        <div data-testid="run-progress" className="admin-run-progress">
          <p className="admin-run-progress-current">
            {progress.currentCompany
              ? `Crawling ${progress.currentCompany} (${progress.currentSourceType})`
              : "Waiting for the next target to start..."}
          </p>
          <p className="admin-run-progress-counters">
            {progress.targetsVisited} targets done so far, {progress.newPosts} new posts so far
          </p>
          {progress.lastCompany && (
            <p className="admin-run-progress-last">
              Last: {progress.lastCompany} ({progress.lastSourceType}), {progress.lastFoundPosts} found, {progress.lastNewPosts} new
            </p>
          )}
        </div>
      )}
      {requestedAt && (
        <span
          data-testid="run-requested-at"
          className="meta"
          title={fmtDateTime(requestedAt) ?? undefined}
        >
          Requested: {relativeTime(requestedAt) ?? fmtDateTime(requestedAt)}
        </span>
      )}
      {finishedAt && (
        <span
          data-testid="run-finished-at"
          className="meta"
          title={fmtDateTime(finishedAt) ?? undefined}
        >
          Finished: {relativeTime(finishedAt) ?? fmtDateTime(finishedAt)}
        </span>
      )}
      {durationText && (
        <span data-testid="run-duration" className="meta">
          Duration: {durationText}
        </span>
      )}
      {resultSummary && (
        <p data-testid="run-result-summary" className="result-summary">
          {resultSummary}
        </p>
      )}
      {errorReason && (
        <p data-testid="run-error-reason" className="error-reason">
          Error: {errorReason}
        </p>
      )}
    </div>
  );
}

/**
 * Toast: a self-dismissing feedback message.
 *
 * Mount this with a `key` derived from the toast's id (e.g. timestamp) so that
 * a fresh trigger remounts the component and restarts its dismiss timer.
 *
 * Props:
 *  - kind: "crawl" | "enrichment", used to build the data-testid
 *  - tone: "success" | "error"
 *  - message: string
 *  - onDismiss(): called when the auto-dismiss timer elapses
 */
function Toast({ kind, tone, message, onDismiss }) {
  React.useEffect(() => {
    const timer = setTimeout(() => {
      onDismiss();
    }, TOAST_DISMISS_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const isError = tone === "error";

  return (
    <p
      role={isError ? "alert" : "status"}
      data-testid={`trigger-${tone}-${kind}`}
      className={`admin-feedback ${tone}`}
    >
      {message}
    </p>
  );
}

/**
 * AdminPage component.
 *
 * Props:
 *  - account: AccountResponse with isAdmin=true (enforced by parent)
 */
export function AdminPage({ account }) {
  const [status, setStatus] = React.useState(null); // TriggerStatusResponse | null
  const [loadError, setLoadError] = React.useState(null);
  const [loading, setLoading] = React.useState(true);
  const [triggerBusy, setTriggerBusy] = React.useState({}); // { crawl: bool, enrichment: bool }
  const [cancelBusy, setCancelBusy] = React.useState({}); // { crawl: bool, enrichment: bool }
  // Story #398 (AC-11): UI-local "requesting" phase, between the click and the
  // 202 response plus its follow-up status refetch. Set synchronously on
  // click, cleared in the same finally block as triggerBusy so it never
  // outlives the request (AC-12).
  const [requesting, setRequesting] = React.useState({}); // { crawl: bool, enrichment: bool }
  // Story #398 (AC-12): once a trigger POST for a kind is rejected as disabled
  // (403), the button for that kind stays visibly disabled until the next
  // successful status refetch, rather than waiting on the (possibly stale)
  // triggerEnabled flag already in state.
  const [triggerDisabledByError, setTriggerDisabledByError] = React.useState({});
  // Per-kind toast: { crawl: { id, tone: "success"|"error", message } | null, enrichment: ... }
  // `id` changes on every trigger so the Toast remounts and its dismiss timer restarts.
  const [toasts, setToasts] = React.useState({});

  // Refresh button's own busy state (BR-1/BR-2): only true while a fetch
  // triggered by the manual Refresh click is in flight, independent of the
  // pre-load `loading` state and independent of auto-poll ticks (AC-4).
  const [refreshBusy, setRefreshBusy] = React.useState(false);

  // BR-3/BR-4: the instant of the most recent *successful* fetchStatus()
  // completion, whichever path triggered it (manual Refresh or auto-poll).
  // A failed fetch never updates this (E2/E3/AC-8).
  const [lastSuccessAt, setLastSuccessAt] = React.useState(null);

  // BR-5: a display-only tick, independent of the data-fetch interval, so the
  // freshness line's "Updated Xs ago" keeps advancing even when nothing is
  // being refetched (e.g. both kinds terminal). Re-render is driven purely by
  // this counter changing; the actual age is computed at render time from
  // lastSuccessAt vs. now.
  const [, setFreshnessTick] = React.useState(0);

  const showToast = React.useCallback((kind, tone, message) => {
    setToasts((prev) => ({ ...prev, [kind]: { id: Date.now(), tone, message } }));
  }, []);

  const dismissToast = React.useCallback((kind) => {
    setToasts((prev) => ({ ...prev, [kind]: null }));
  }, []);

  const fetchStatus = React.useCallback(async () => {
    try {
      const data = await getAdminTriggerStatus();
      setStatus(data);
      setLoadError(null);
      setLastSuccessAt(new Date());
      // A fresh server read is authoritative: any 403-driven local override
      // (AC-12) is superseded by whatever triggerEnabled says now.
      setTriggerDisabledByError({});
    } catch (e) {
      setLoadError((e && e.message) || "Failed to load trigger status.");
    } finally {
      setLoading(false);
    }
  }, []);

  // Manual Refresh click: wraps fetchStatus with the busy/disabled state
  // (BR-1). Never routes through the verification-code flow (BR-11): it
  // calls only fetchStatus, which itself calls only getAdminTriggerStatus().
  const handleRefreshClick = React.useCallback(async () => {
    setRefreshBusy(true);
    try {
      await fetchStatus();
    } finally {
      setRefreshBusy(false);
    }
  }, [fetchStatus]);

  // Initial load
  React.useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  // Poll while any kind is queued, running, or cancel_requested (BR-6):
  // cancel_requested still has work in progress until crawler-service
  // finalizes it to "cancelled".
  const hasRunning = status && (
    (status.crawl && POLL_ELIGIBLE_STATUSES.includes(status.crawl.status)) ||
    (status.enrichment && POLL_ELIGIBLE_STATUSES.includes(status.enrichment.status))
  );

  React.useEffect(() => {
    if (!hasRunning) return;
    const timer = setInterval(fetchStatus, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [hasRunning, fetchStatus]);

  // BR-5: the freshness line's own tick, ticking every second regardless of
  // auto-poll state, so the displayed age never lags by more than a second.
  React.useEffect(() => {
    if (!lastSuccessAt) return;
    const timer = setInterval(() => setFreshnessTick((n) => n + 1), FRESHNESS_TICK_MS);
    return () => clearInterval(timer);
  }, [lastSuccessAt]);

  async function handleTrigger(kind) {
    // AC-11: "Requesting" shows synchronously on click, before the POST ever
    // resolves; it is UI-local only, there is no server-side "requesting" status.
    setRequesting((prev) => ({ ...prev, [kind]: true }));
    setTriggerBusy((prev) => ({ ...prev, [kind]: true }));
    try {
      await triggerAdminPass({ kind });
      showToast(kind, "success", `${kind} queued.`);
      // AC-11: "Requesting" hands off to queued/running only once this
      // follow-up status refetch (the poll confirming acceptance) resolves.
      await fetchStatus();
    } catch (e) {
      if (e && e.status === 409) {
        showToast(kind, "error", "A pass of this kind is already in progress.");
      } else if (e && e.status === 403) {
        showToast(kind, "error", "Triggering is disabled.");
        // AC-12: a 403 disabled rejection keeps the button disabled without
        // waiting for the next status refetch to catch up.
        setTriggerDisabledByError((prev) => ({ ...prev, [kind]: true }));
      } else {
        showToast(kind, "error", (e && e.message) || "Trigger failed.");
      }
    } finally {
      // AC-12: any rejection leaves "Requesting" immediately, never stuck.
      setTriggerBusy((prev) => ({ ...prev, [kind]: false }));
      setRequesting((prev) => ({ ...prev, [kind]: false }));
    }
  }

  async function handleTriggerWithCode(kind, code) {
    // throws on error (caught by TriggerKindPanel)
    await triggerAdminPass({ kind, code });
    await fetchStatus();
  }

  async function handleCancel(kind) {
    if (cancelBusy[kind]) return; // guard against double-clicks (E12)
    setCancelBusy((prev) => ({ ...prev, [kind]: true }));
    try {
      const updated = await cancelAdminTrigger(kind);
      // Reflect the response immediately so the panel updates without
      // waiting for the next poll tick (spec §5.3).
      setStatus((prev) => (prev ? { ...prev, [kind]: updated } : prev));
    } catch (e) {
      if (e && e.status === 409) {
        showToast(kind, "error", "No active run to stop for this kind.");
        await fetchStatus();
      } else if (e && e.status === 403) {
        showToast(kind, "error", "Not authorized to stop this run.");
      } else {
        showToast(kind, "error", "Stop failed. Try again.");
      }
    } finally {
      setCancelBusy((prev) => ({ ...prev, [kind]: false }));
    }
  }

  if (loading) {
    return (
      <>
        <UI.Topbar title="Admin: Trigger Panel" />
        <div data-testid="admin-page" className="content">
          <p>Loading trigger status…</p>
        </div>
      </>
    );
  }

  // BR-3/BR-4/BR-5: the freshness line's age text, recomputed on every render
  // (driven by fetches updating lastSuccessAt and the freshness tick above).
  const freshnessText = lastSuccessAt ? freshnessRelativeTime(lastSuccessAt.toISOString()) : null;

  return (
    <>
      <UI.Topbar
        title="Admin: Trigger Panel"
        actions={
          <Button data-testid="refresh-btn" size="sm" onClick={handleRefreshClick} disabled={refreshBusy}>
            {refreshBusy ? "Refreshing…" : "Refresh"}
          </Button>
        }
      />
      {freshnessText && (
        <p data-testid="freshness-line" className="admin-freshness-line">
          Updated {freshnessText}
          {hasRunning && <span> · auto-refreshing</span>}
        </p>
      )}
      <div data-testid="admin-page" className="content">
        {loadError && (
          <p role="alert" data-testid="load-error" className="admin-load-error">
            {loadError}
          </p>
        )}

        {status && (
          <div data-testid="trigger-status-panel">
            {/* Global disabled banner */}
            {!status.triggerEnabled && (
              <p
                data-testid="triggering-disabled-banner"
                role="status"
                className="banner-warning"
              >
                Triggering is currently disabled. Contact your deployment administrator.
              </p>
            )}

            {/* Crawl panel */}
            <TriggerKindPanel
              kind="crawl"
              label="Crawl"
              runInfo={status.crawl ?? null}
              triggerEnabled={status.triggerEnabled}
              twoFactorRequired={status.twoFactorRequired}
              onTrigger={handleTrigger}
              onTriggerWithCode={handleTriggerWithCode}
              busy={!!triggerBusy.crawl}
              onCancel={handleCancel}
              cancelBusy={!!cancelBusy.crawl}
              requesting={!!requesting.crawl}
              forceDisabled={!!triggerDisabledByError.crawl}
              lastRun={status.lastCrawlRun ?? null}
            />
            {toasts.crawl && (
              <Toast
                key={toasts.crawl.id}
                kind="crawl"
                tone={toasts.crawl.tone}
                message={toasts.crawl.message}
                onDismiss={() => dismissToast("crawl")}
              />
            )}

            {/* Enrichment panel */}
            <TriggerKindPanel
              kind="enrichment"
              label="Enrichment"
              runInfo={status.enrichment ?? null}
              triggerEnabled={status.triggerEnabled}
              twoFactorRequired={status.twoFactorRequired}
              onTrigger={handleTrigger}
              onTriggerWithCode={handleTriggerWithCode}
              busy={!!triggerBusy.enrichment}
              onCancel={handleCancel}
              cancelBusy={!!cancelBusy.enrichment}
              requesting={!!requesting.enrichment}
              forceDisabled={!!triggerDisabledByError.enrichment}
              lastRun={status.lastEnrichmentRun ?? null}
            />
            {toasts.enrichment && (
              <Toast
                key={toasts.enrichment.id}
                kind="enrichment"
                tone={toasts.enrichment.tone}
                message={toasts.enrichment.message}
                onDismiss={() => dismissToast("enrichment")}
              />
            )}
          </div>
        )}
      </div>
    </>
  );
}
