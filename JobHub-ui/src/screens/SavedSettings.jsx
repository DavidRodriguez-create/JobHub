import React from "react";
import Icon from "../components/Icon.jsx";
import DATA from "../data/mockData.js";
import * as UI from "../components/ui.jsx";
import { accountName, accountInitials } from "../api/mappers.js";
import { getNotificationPreferences, updateNotificationPreferences } from "../api/notifications.js";
import { changePassword, setupTwoFactor, verifyTwoFactorSetup, disableTwoFactor, resendVerification, verifyEmail, getApplyProfile, saveApplyProfile } from "../api/auth.js";
import {
  APPLY_PROFILE_TEXT_FIELDS, APPLY_PROFILE_BOOL_FIELDS, APPLY_PROFILE_FIELD_LABELS, formatApplyProfileSavedAt,
} from "../components/applyProfile/applyProfileFields.js";
import { useApplyProfileCopy } from "../components/applyProfile/useApplyProfileCopy.js";
import { ApplyProfileCopyButton } from "../components/applyProfile/ApplyProfileCopyButton.jsx";
import { setCachedApplyProfile } from "../components/applyProfile/applyProfileCache.js";
// JobHub — Saved Jobs + Settings screens
const { Button, Input, Field, Toggle, CoLogo, Avatar, Card, Empty, JobRow } = UI;

/* ─── Saved Screen ─── */
function SavedScreen({ savedIds, onSaveToggle, openJob, goto, appliedJobIds, openSearch }) {
  const savedJobs = DATA.jobs.filter((j) => savedIds.has(j.id));
  return (
    <>
      <UI.Topbar title="Saved" sub={`${savedJobs.length} jobs you bookmarked`}
        searchLabel="Search jobs…"
        onSearchClick={openSearch}
        actions={<Button variant="secondary" icon="search" onClick={() => goto("search")}>Find more</Button>} />
      <div className="content">
        {savedJobs.length === 0 ? (
          <Empty icon="bookmark" title="No saved jobs"
            desc="Save one to come back to it later."
            cta={<Button variant="primary" icon="search" onClick={() => goto("search")}>Browse jobs</Button>} />
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {savedJobs.map((j) => (
              <JobRow key={j.id} job={j} isSaved={true}
                isApplied={appliedJobIds.has(j.id)}
                onSave={onSaveToggle} onOpen={openJob} />
            ))}
          </div>
        )}
      </div>
    </>
  );
}

/* ─── Settings Screen ─── */
// Prefetch notification preferences on mount (when authed) so the data is in-flight
// while the user reads the Account tab. The SWR cache in notifications.js means this
// call is free on second visit (cache hit). State is passed down to NotificationsSection
// so the section never needs to re-fetch on tab click.
function SettingsScreen({ authed, account, onLogout, onLogin, openSearch, pushToast, initialSection }) {
  const [section, setSection] = React.useState(initialSection || "account");

  // Story #304: an incoming target section (e.g. from the command palette's settings
  // index) replaces whatever section was last active, including repeat selections
  // while already mounted on the Settings screen (BR-7 #3/#4, AC-14).
  React.useEffect(() => {
    if (initialSection) setSection(initialSection);
  }, [initialSection]);

  // Lifted preferences state — prefetched on mount so tab click is instant.
  const [prefs, setPrefs] = React.useState(null);
  const [loadError, setLoadError] = React.useState(false);

  // Prefetch on authed change (initial mount and re-login).
  // TC-E3: authed=false guard means no fetch fires for unauthenticated users.
  // TC-23: 401 on GET triggers onLogout in addition to showing the error banner.
  React.useEffect(() => {
    if (!authed) return;
    let cancelled = false;
    setLoadError(false);
    // getNotificationPreferences is SWR-cached: first call hits network, subsequent calls
    // return the cached value synchronously. Errors do not poison the cache.
    getNotificationPreferences()
      .then((data) => { if (!cancelled) setPrefs(data); })
      .catch((err) => {
        if (!cancelled) {
          setLoadError(true);
          if (err && err.status === 401 && onLogout) onLogout();
        }
      });
    return () => { cancelled = true; };
  }, [authed]);

  // Track how many times the Notifications section has been activated (tab clicked).
  // Used by NotificationsSection to detect a retry-eligible remount (second+ visit).
  const notifVisitCountRef = React.useRef(0);

  return (
    <>
      <UI.Topbar title="Settings" searchLabel="Search settings…" onSearchClick={openSearch} />
      <div className="content">
        <div className="settings-grid">
          <nav className="settings-nav">
            {[
              ["account", "Account"],
              ["apply-profile", "Apply profile"],
              ["notifications", "Notifications"],
              ["sources", "Sources & filters"],
              ["integrations", "Integrations"],
              ["billing", "Billing"],
              ["data", "Data & privacy"],
            ].map(([id, label]) => (
              <a key={id} className={section === id ? "active" : ""} onClick={() => setSection(id)}>{label}</a>
            ))}
          </nav>

          <div className="settings-section">
            {section === "account" && (
              <>
                <div><h3>Account</h3><p style={{ marginTop: 4 }}>Your profile and how you sign in.</p></div>
                {authed ? (
                  <>
                    <Card pad={false}>
                      <div style={{ padding: 18, display: "flex", gap: 16, alignItems: "center" }}>
                        <Avatar initials={accountInitials(account) || "?"} size={56} />
                        <div style={{ flex: 1 }}>
                          <div style={{ fontSize: 16, fontWeight: 600, letterSpacing: "-0.012em" }}>{accountName(account) || "Your account"}</div>
                          {account && <EmailVerificationSection account={account} />}
                        </div>
                        <Button variant="secondary">Change photo</Button>
                      </div>
                    </Card>
                    <Card>
                      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
                        <Field label="First name"><Input key={account?.id || "fn"} defaultValue={account?.firstName || ""} /></Field>
                        <Field label="Last name"><Input key={account?.id || "ln"} defaultValue={account?.lastName || ""} /></Field>
                        <Field label="Email"><Input key={account?.id || "em"} defaultValue={account?.email || ""} /></Field>
                      </div>
                    </Card>
                    <ChangePasswordRow account={account} />
                    <TwoFactorSettingsRow account={account} pushToast={pushToast} />
                    <div style={{ borderTop: "1px solid var(--color-border)", paddingTop: 16, marginTop: 8 }}>
                      <Button variant="ghost" icon="logout" onClick={onLogout} style={{ color: "var(--color-danger)" }}>Sign out</Button>
                    </div>
                  </>
                ) : (
                  <Card>
                    <div style={{ textAlign: "center", padding: "24px 0" }}>
                      <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Not signed in</div>
                      <div style={{ fontSize: 13, color: "var(--color-ink-3)", marginBottom: 16 }}>Sign in to track applications and save jobs.</div>
                      <Button variant="primary" onClick={onLogin}>Sign in</Button>
                    </div>
                  </Card>
                )}
              </>
            )}

            {section === "apply-profile" && (
              authed ? (
                <ApplyProfileSection onLogout={onLogout} pushToast={pushToast} />
              ) : (
                <>
                  <div><h3>Apply profile</h3><p style={{ marginTop: 4 }}>Answers you reuse on every external application.</p></div>
                  <Card>
                    <div style={{ textAlign: "center", padding: "24px 0" }}>
                      <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Not signed in</div>
                      <div style={{ fontSize: 13, color: "var(--color-ink-3)", marginBottom: 16 }}>Sign in to fill in and copy your apply profile.</div>
                      <Button variant="primary" onClick={onLogin}>Sign in</Button>
                    </div>
                  </Card>
                </>
              )
            )}

            {section === "notifications" && (
              <NotificationsSection
                authed={authed}
                onLogout={onLogout}
                pushToast={pushToast}
                prefs={prefs}
                setPrefs={setPrefs}
                loadError={loadError}
                setLoadError={setLoadError}
                notifVisitCountRef={notifVisitCountRef}
              />
            )}

            {section === "sources" && (
              <>
                <div><h3>Sources &amp; filters</h3><p style={{ marginTop: 4 }}>Pick where JobHub looks for jobs.</p></div>
                <SettingsRow name="Greenhouse boards" desc="Across 1,200+ company career pages." action={<Toggle on={true} />} />
                <SettingsRow name="Lever boards" desc="Across 800+ company career pages." action={<Toggle on={true} />} />
                <SettingsRow name="Ashby boards" desc="Newer ATS, growing fast." action={<Toggle on={true} />} />
                <SettingsRow name="LinkedIn (filtered)" desc="Drops sponsored / aggregator / promoted posts." action={<Toggle on={false} />} />
                <SettingsRow name="Hide jobs without compensation" desc="If a post hides salary, don't show it." action={<Toggle on={true} />} />
              </>
            )}

            {section === "integrations" && (
              <>
                <div><h3>Integrations</h3><p style={{ marginTop: 4 }}>Connect calendar and email to keep timelines fresh.</p></div>
                <SettingsRow name="Google Calendar" desc="Auto-create events for interviews." action={<Button variant="secondary">Connect</Button>} />
                <SettingsRow name="Gmail (read-only)" desc="Detect recruiter replies." action={<Button variant="secondary">Connect</Button>} />
                <SettingsRow name="iCloud Calendar" desc="" action={<Button variant="secondary">Connect</Button>} />
                <SettingsRow name="Notion" desc="Mirror applications to a Notion database." action={<Button variant="secondary">Connect</Button>} />
              </>
            )}

            {section === "billing" && (
              <>
                <div><h3>Billing</h3><p style={{ marginTop: 4 }}>JobHub is free while you're job hunting.</p></div>
                <Card>
                  <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 14, fontWeight: 600 }}>Free plan</div>
                      <div style={{ fontSize: 13, color: "var(--color-ink-3)", marginTop: 4 }}>Unlimited applications. Up to 5 saved searches. No credit card.</div>
                    </div>
                    <Button variant="secondary">Compare plans</Button>
                  </div>
                </Card>
              </>
            )}

            {section === "data" && (
              <>
                <div><h3>Data &amp; privacy</h3><p style={{ marginTop: 4 }}>Your data is yours. Export or delete any time.</p></div>
                <SettingsRow name="Export your data" desc="A zip of all your applications, notes, and saved jobs." action={<Button variant="secondary">Export</Button>} />
                <SettingsRow name="Anonymous analytics" desc="Help us prioritize features." action={<Toggle on={true} />} />
                <DeleteAccountRow />
              </>
            )}
          </div>
        </div>
      </div>
    </>
  );
}

/* ─── Notifications section ───
   Settings -> Notifications, wired to notification-service
   (GET/PUT /notifications/preferences).
   Story #135: re-grouped layout per ADR 0010.
   - Group 1: Weekly news posts (weeklyDigestEmail)
   - Group 2: Alerts and reminders master toggle (UI-only, derived from
     interviewReminders OR ghostedAlert; OFF writes both false, ON writes both true)
     - Nested: Interview reminders (interviewReminders)
     - Nested: Ghosted alert (ghostedAlert)
     - Nested: Also email me for interview reminders (interviewReminderEmail,
       additionally disabled when interviewReminders is off)
   inAppNotificationsEnabled is NOT surfaced (per ADR 0010 / PDA #146). */

// Loading skeleton fields: 5 rows matching the new grouped layout (no inApp row).
const LOADING_SKELETON_FIELDS = [
  { key: "sk-weekly", name: "Weekly news posts", desc: "A Monday summary of new jobs that match your saved filters." },
  { key: "sk-master", name: "Alerts and reminders", desc: "Turn off to silence all alerts and reminders." },
  { key: "sk-interview", name: "Interview reminders", desc: "A notification 24 hours and 1 hour before each scheduled event." },
  { key: "sk-ghosted", name: "Ghosted alert", desc: "A nudge when an application has had no activity for 14 days." },
  { key: "sk-email", name: "Also email me for interview reminders", desc: "Send a reminder email before interviews. Does not affect ghosted-alert emails (those follow the Ghosted alert toggle)." },
];

// NotificationsSection receives prefs/loadError/setPrefs/setLoadError from SettingsScreen
// (which prefetches on mount). This eliminates the tab-click round-trip.
// Story #135: masterOn is UI-only state; nestedDisplay holds the visual values for the
// nested cluster (preserved on master-OFF, reconciled on master-ON or individual saves).
function NotificationsSection({ authed, onLogout, pushToast, prefs, setPrefs, loadError, setLoadError, notifVisitCountRef }) {
  const [toggleError, setToggleError] = React.useState("");
  const [pending, setPending] = React.useState(() => new Set());

  // masterOn: UI-only boolean, derived from server truth on first render, then managed
  // independently. true when interviewReminders OR ghostedAlert is true.
  const [masterOn, setMasterOn] = React.useState(() =>
    prefs ? !!(prefs.interviewReminders || prefs.ghostedAlert) : false
  );

  // nestedDisplay: visual values for the nested cluster. Preserved on master-OFF so the
  // user sees their pre-OFF state while the cluster is dimmed. Reconciled on master-ON
  // (from server response) and on individual nested saves (from server response).
  const [nestedDisplay, setNestedDisplay] = React.useState(() =>
    prefs
      ? {
          interviewReminders: !!prefs.interviewReminders,
          ghostedAlert: !!prefs.ghostedAlert,
          interviewReminderEmail: !!prefs.interviewReminderEmail,
        }
      : { interviewReminders: false, ghostedAlert: false, interviewReminderEmail: false }
  );

  // When a master-OFF PUT resolves, we must NOT sync nestedDisplay from the server
  // response (TC-11: visual preservation of pre-OFF values in the disabled nested cluster).
  // This ref counts how many "suppress" requests are pending so we don't race.
  const suppressNestedSyncCountRef = React.useRef(0);

  // Sync masterOn and nestedDisplay when prefs arrive from parent (initial load / retry).
  // Does NOT sync nestedDisplay when suppressNestedSyncCountRef > 0 (master-OFF resolve).
  const prevPrefsRef = React.useRef(prefs);
  React.useEffect(() => {
    if (prefs !== null && prefs !== prevPrefsRef.current) {
      prevPrefsRef.current = prefs;
      setMasterOn(!!(prefs.interviewReminders || prefs.ghostedAlert));
      if (suppressNestedSyncCountRef.current > 0) {
        // Master-OFF PUT just resolved: preserve nestedDisplay as-is; clear one suppress token.
        suppressNestedSyncCountRef.current -= 1;
      } else {
        setNestedDisplay({
          interviewReminders: !!prefs.interviewReminders,
          ghostedAlert: !!prefs.ghostedAlert,
          interviewReminderEmail: !!prefs.interviewReminderEmail,
        });
      }
    }
  }, [prefs]);

  React.useEffect(() => {
    notifVisitCountRef.current += 1;
    const isFirstVisit = notifVisitCountRef.current === 1;

    setToggleError("");

    // TC-E2: on a return visit after an error, retry the GET.
    if (!isFirstVisit && authed && loadError) {
      let cancelled = false;
      setLoadError(false);
      getNotificationPreferences()
        .then((data) => { if (!cancelled) setPrefs(data); })
        .catch(() => { if (!cancelled) setLoadError(true); });
      return () => { cancelled = true; };
    }
    return undefined;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Handle the master "Alerts and reminders" toggle.
  // OFF: PUT {interviewReminders: false, ghostedAlert: false}; nestedDisplay preserved.
  // ON:  PUT {interviewReminders: true, ghostedAlert: true}; reconcile nestedDisplay from response.
  function handleMasterToggle() {
    if (!prefs) return;
    const nextMasterOn = !masterOn;

    setToggleError("");
    setMasterOn(nextMasterOn); // optimistic flip of master

    if (!nextMasterOn) {
      // Master going OFF: write both nested flags false. Preserve nestedDisplay visually.
      // Increment the suppress counter so the prefs-change sync effect skips nestedDisplay update.
      suppressNestedSyncCountRef.current += 1;
      setPending((p) => new Set([...p, "interviewReminders", "ghostedAlert"]));
      updateNotificationPreferences({ interviewReminders: false, ghostedAlert: false })
        .then((data) => {
          // Reconcile prefs (weeklyDigestEmail + interviewReminderEmail from server response)
          // but the sync effect will NOT update nestedDisplay (suppressNestedSyncCountRef > 0).
          setPrefs(data);
        })
        .catch((err) => {
          // On failure: clear the suppress token since setPrefs won't fire.
          suppressNestedSyncCountRef.current = Math.max(0, suppressNestedSyncCountRef.current - 1);
          setMasterOn(!nextMasterOn); // revert master
          const message = "Couldn't save your notification preference. Please try again.";
          setToggleError(message);
          if (pushToast) pushToast(message, "info");
          if (err && err.status === 401 && onLogout) onLogout();
        })
        .finally(() => {
          setPending((p) => {
            const next = new Set(p);
            next.delete("interviewReminders");
            next.delete("ghostedAlert");
            return next;
          });
        });
    } else {
      // Master going ON: write both nested flags true. Reconcile nestedDisplay from response.
      setPending((p) => new Set([...p, "interviewReminders", "ghostedAlert"]));
      updateNotificationPreferences({ interviewReminders: true, ghostedAlert: true })
        .then((data) => {
          setPrefs(data);
          setNestedDisplay({
            interviewReminders: !!data.interviewReminders,
            ghostedAlert: !!data.ghostedAlert,
            interviewReminderEmail: !!data.interviewReminderEmail,
          });
        })
        .catch((err) => {
          setMasterOn(!nextMasterOn); // revert master
          const message = "Couldn't save your notification preference. Please try again.";
          setToggleError(message);
          if (pushToast) pushToast(message, "info");
          if (err && err.status === 401 && onLogout) onLogout();
        })
        .finally(() => {
          setPending((p) => {
            const next = new Set(p);
            next.delete("interviewReminders");
            next.delete("ghostedAlert");
            return next;
          });
        });
    }
  }

  // Handle individual nested field toggles (interviewReminders, ghostedAlert, interviewReminderEmail).
  function handleNestedToggle(field) {
    if (!prefs || pending.has(field)) return;
    const prevValue = !!nestedDisplay[field];
    const nextValue = !prevValue;

    setToggleError("");
    setNestedDisplay((d) => ({ ...d, [field]: nextValue })); // optimistic flip
    setPending((p) => new Set(p).add(field));

    updateNotificationPreferences({ [field]: nextValue })
      .then((data) => {
        setPrefs(data);
        setNestedDisplay({
          interviewReminders: !!data.interviewReminders,
          ghostedAlert: !!data.ghostedAlert,
          interviewReminderEmail: !!data.interviewReminderEmail,
        });
        // Re-derive masterOn from server response
        setMasterOn(!!(data.interviewReminders || data.ghostedAlert));
      })
      .catch((err) => {
        setNestedDisplay((d) => ({ ...d, [field]: prevValue })); // revert
        const message = "Couldn't save your notification preference. Please try again.";
        setToggleError(message);
        if (pushToast) pushToast(message, "info");
        if (err && err.status === 401 && onLogout) onLogout();
      })
      .finally(() => {
        setPending((p) => {
          const next = new Set(p);
          next.delete(field);
          return next;
        });
      });
  }

  // Handle weekly digest (top-level, non-nested).
  function handleTopLevelToggle(field) {
    if (!prefs || pending.has(field)) return;
    const prevValue = !!prefs[field];
    const nextValue = !prevValue;

    setToggleError("");
    setPrefs((p) => ({ ...p, [field]: nextValue })); // optimistic flip
    setPending((p) => new Set(p).add(field));

    updateNotificationPreferences({ [field]: nextValue })
      .then((data) => {
        setPrefs(data);
      })
      .catch((err) => {
        setPrefs((p) => ({ ...p, [field]: prevValue })); // revert
        const message = "Couldn't save your notification preference. Please try again.";
        setToggleError(message);
        if (pushToast) pushToast(message, "info");
        if (err && err.status === 401 && onLogout) onLogout();
      })
      .finally(() => {
        setPending((p) => {
          const next = new Set(p);
          next.delete(field);
          return next;
        });
      });
  }

  // Nested toggles are disabled when: master is OFF, or a pending PUT is in flight for the field.
  // "Also email me" is additionally disabled when interviewReminders (nested display) is off.
  const nestedDisabled = !masterOn;
  const alsoEmailDisabled = nestedDisabled || !nestedDisplay.interviewReminders || pending.has("interviewReminderEmail");
  const interviewDisabled = nestedDisabled || pending.has("interviewReminders");
  const ghostedDisabled = nestedDisabled || pending.has("ghostedAlert");

  // Unique ID for aria-labelledby on the group element.
  const groupLabelId = "alerts-reminders-group-label";

  return (
    <div data-testid="notifications-section">
      <div><h3>Notifications</h3><p style={{ marginTop: 4 }}>What we tell you, and how.</p></div>

      {loadError && (
        <div data-testid="notifications-error" className="banner-warning" role="alert">
          <Icon name="info" size={14} style={{ marginRight: 6, verticalAlign: "-2px" }} />
          Couldn't load your notification preferences. Please try again later.
        </div>
      )}

      {!loadError && prefs === null && (
        <div data-testid="notifications-loading" style={{ display: "flex", flexDirection: "column", gap: 0 }}>
          {LOADING_SKELETON_FIELDS.map(({ key, name, desc }) => (
            <SettingsRow key={key} name={name} desc={desc} action={<Toggle on={false} disabled />} />
          ))}
        </div>
      )}

      {!loadError && prefs !== null && (
        <>
          {toggleError && (
            <div data-testid="notifications-toggle-error" className="admin-error-text" role="alert">
              {toggleError}
            </div>
          )}

          {/* Group 1: Weekly news posts */}
          <SettingsRow
            name="Weekly news posts"
            desc="A Monday summary of new jobs that match your saved filters."
            action={
              <Toggle
                on={!!prefs.weeklyDigestEmail}
                onChange={() => handleTopLevelToggle("weeklyDigestEmail")}
                disabled={pending.has("weeklyDigestEmail")}
                data-testid="toggle-weeklyDigestEmail"
                aria-label="Weekly news posts"
              />
            }
          />

          {/* Group 2: Alerts and reminders -- master toggle + nested cluster */}
          <SettingsRow
            name="Alerts and reminders"
            desc="Turn off to silence all alerts and reminders."
            action={
              <Toggle
                on={masterOn}
                onChange={handleMasterToggle}
                disabled={pending.has("interviewReminders") && pending.has("ghostedAlert")}
                data-testid="toggle-alertsAndReminders"
                aria-label="Alerts and reminders"
              />
            }
          />

          {/* Nested cluster wrapped in role="group" for TC-29 / AC-12 */}
          <div
            role="group"
            aria-labelledby={groupLabelId}
            style={{ paddingLeft: 16 }}
          >
            {/* Hidden label element for the group */}
            <span id={groupLabelId} style={{ display: "none" }}>Alerts and reminders</span>

            {/* data-testid="interview-reminders-section" preserved for TC-156 */}
            <div data-testid="interview-reminders-section">
              <SettingsRow
                name="Interview reminders"
                desc="A notification 24 hours and 1 hour before each scheduled event."
                action={
                  <Toggle
                    on={nestedDisplay.interviewReminders}
                    onChange={() => { if (!interviewDisabled) handleNestedToggle("interviewReminders"); }}
                    disabled={interviewDisabled}
                    data-testid="toggle-interviewReminders"
                    aria-label="Interview reminders"
                  />
                }
              />

              <SettingsRow
                name="Also email me for interview reminders"
                desc="Send a reminder email before interviews. Does not affect ghosted-alert emails (those follow the Ghosted alert toggle)."
                action={
                  <Toggle
                    on={nestedDisplay.interviewReminderEmail}
                    onChange={() => { if (!alsoEmailDisabled) handleNestedToggle("interviewReminderEmail"); }}
                    disabled={alsoEmailDisabled}
                    data-testid="toggle-interviewReminderEmail"
                    aria-label="Also email me for interview reminders"
                  />
                }
              />
            </div>

            <SettingsRow
              name="Ghosted alert"
              desc="A nudge when an application has had no activity for 14 days."
              action={
                <Toggle
                  on={nestedDisplay.ghostedAlert}
                  onChange={() => { if (!ghostedDisabled) handleNestedToggle("ghostedAlert"); }}
                  disabled={ghostedDisabled}
                  data-testid="toggle-ghostedAlert"
                  aria-label="Ghosted alert"
                />
              }
            />
          </div>
        </>
      )}
    </div>
  );
}

/* ─── Apply profile section ───
   Settings -> Apply profile, wired to auth-service
   (GET/PUT /auth/account/apply-profile). Story #336 / ADR 0022.
   One profile per user, full-replace PUT (BR-2): the form always holds every
   field's current value and resubmits all of them on save, so an untouched
   field round-trips instead of being cleared. Copy-to-clipboard is read-only
   client-side (BR-8): it never calls saveApplyProfile. */

// Best-effort: the frozen ErrorResponse shape ({error, message}) has no structured
// field identifier (see BACKEND_GAPS.md #336 note), so the offending field on a 400
// is inferred from its message text. Order matters: more specific patterns first.
const APPLY_PROFILE_FIELD_MATCHERS = [
  ["workAuthorization", /work ?authorization/i],
  ["noticePeriod", /notice ?period/i],
  ["salaryExpectation", /salary ?expectation/i],
  ["currentLocation", /current ?location/i],
  ["linkedinUrl", /linkedin/i],
  ["githubUrl", /github/i],
  ["portfolioUrl", /portfolio/i],
  ["languages", /language/i],
  ["roomToGrow", /room ?to ?grow/i],
];

function identifyApplyProfileErrorField(err) {
  const text = [err && err.body && err.body.message, err && err.body && err.body.error, err && err.message]
    .filter(Boolean)
    .join(" ");
  const match = APPLY_PROFILE_FIELD_MATCHERS.find(([, pattern]) => pattern.test(text));
  return match ? match[0] : null;
}

// Composes a plain-language error that always names the field by its business
// label (AC7/AC8/AC9), regardless of whether the server message uses the same
// camelCase property name.
function formatApplyProfileFieldError(field, serverMessage) {
  const label = APPLY_PROFILE_FIELD_LABELS[field] || field;
  return serverMessage ? `${label}: ${serverMessage}` : `${label} is invalid. Please check and try again.`;
}

function emptyApplyProfileForm() {
  const form = { roomToGrow: "" };
  APPLY_PROFILE_TEXT_FIELDS.forEach(({ key }) => { form[key] = ""; });
  APPLY_PROFILE_BOOL_FIELDS.forEach((key) => { form[key] = null; });
  form.languages = [];
  return form;
}

function applyProfileToForm(profile) {
  if (!profile) return emptyApplyProfileForm();
  const form = { roomToGrow: profile.roomToGrow || "" };
  APPLY_PROFILE_TEXT_FIELDS.forEach(({ key }) => { form[key] = profile[key] || ""; });
  APPLY_PROFILE_BOOL_FIELDS.forEach((key) => { form[key] = profile[key] === undefined ? null : profile[key]; });
  form.languages = Array.isArray(profile.languages) ? [...profile.languages] : [];
  return form;
}

function normalizeApplyProfileText(value) {
  if (value == null) return null;
  return value.trim() === "" ? null : value;
}

function buildApplyProfileSaveBody(form) {
  const body = {};
  APPLY_PROFILE_TEXT_FIELDS.forEach(({ key }) => { body[key] = normalizeApplyProfileText(form[key]); });
  APPLY_PROFILE_BOOL_FIELDS.forEach((key) => { body[key] = form[key] === undefined ? null : form[key]; });
  body.roomToGrow = normalizeApplyProfileText(form.roomToGrow);
  body.languages = form.languages && form.languages.length > 0 ? form.languages : null;
  return body;
}

function ApplyProfileSection({ onLogout, pushToast }) {
  const [profile, setProfile] = React.useState(null); // last-saved ApplyProfileResponse, or null while loading
  const [form, setForm] = React.useState(null); // editable form state, mirrors `profile` once loaded
  const [loadError, setLoadError] = React.useState(false);
  const [fieldErrors, setFieldErrors] = React.useState({});
  const [saveError, setSaveError] = React.useState("");
  const [saving, setSaving] = React.useState(false);
  const { copiedField, handleCopy } = useApplyProfileCopy(pushToast);

  React.useEffect(() => {
    let cancelled = false;
    setLoadError(false);
    getApplyProfile()
      .then((data) => {
        if (cancelled) return;
        setProfile(data);
        setForm(applyProfileToForm(data));
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadError(true);
        if (err && err.status === 401 && onLogout) onLogout();
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function updateText(key, value) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function updateBool(key, value) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function updateLanguage(index, value) {
    setForm((f) => {
      const languages = [...f.languages];
      languages[index] = value;
      return { ...f, languages };
    });
  }

  function addLanguage() {
    setForm((f) => ({ ...f, languages: [...f.languages, ""] }));
  }

  function removeLanguage(index) {
    setForm((f) => ({ ...f, languages: f.languages.filter((_, i) => i !== index) }));
  }

  async function handleSave() {
    if (!form || saving) return;
    setFieldErrors({});
    setSaveError("");
    setSaving(true);
    try {
      const saved = await saveApplyProfile(buildApplyProfileSaveBody(form));
      setProfile(saved);
      setForm(applyProfileToForm(saved));
      // Story #483 (#3): keep the quick-access drawer's cache in sync so it never
      // shows a value the user just changed here as stale on its next open.
      setCachedApplyProfile(saved);
    } catch (err) {
      if (err && err.status === 401) {
        if (onLogout) onLogout();
        return;
      }
      if (err && err.status === 400) {
        const field = identifyApplyProfileErrorField(err);
        const serverMessage = err.body && err.body.message;
        if (field) setFieldErrors({ [field]: formatApplyProfileFieldError(field, serverMessage) });
        else setSaveError(serverMessage || "Please check the highlighted field and try again.");
      } else {
        setSaveError("Couldn't save your apply profile. Please try again.");
      }
    } finally {
      setSaving(false);
    }
  }

  if (loadError && !form) {
    return (
      <div data-testid="apply-profile-section">
        <div><h3>Apply profile</h3><p style={{ marginTop: 4 }}>Answers you reuse on every external application.</p></div>
        <div data-testid="apply-profile-error" className="banner-warning" role="alert">
          <Icon name="info" size={14} style={{ marginRight: 6, verticalAlign: "-2px" }} />
          Couldn't load your apply profile. Please try again later.
        </div>
      </div>
    );
  }

  if (!form) {
    return (
      <div data-testid="apply-profile-section">
        <div><h3>Apply profile</h3><p style={{ marginTop: 4 }}>Answers you reuse on every external application.</p></div>
        <div data-testid="apply-profile-loading" style={{ padding: "24px 0", color: "var(--color-ink-3)", fontSize: 13 }}>Loading…</div>
      </div>
    );
  }

  const lastSaved = formatApplyProfileSavedAt(profile && profile.updatedAt);

  return (
    <div data-testid="apply-profile-section">
      <div>
        <h3>Apply profile</h3>
        <p style={{ marginTop: 4 }}>Answers you reuse on every external application, ready to copy.</p>
      </div>

      {saveError && (
        <div data-testid="apply-profile-save-error" className="admin-error-text" role="alert">
          {saveError}
        </div>
      )}

      <Card>
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {APPLY_PROFILE_TEXT_FIELDS.slice(0, 4).map(({ key, label, maxLength, placeholder }) => (
            <ApplyProfileTextField
              key={key}
              fieldKey={key}
              label={label}
              value={form[key]}
              maxLength={maxLength}
              placeholder={placeholder}
              error={fieldErrors[key]}
              copiedField={copiedField}
              onChange={(v) => updateText(key, v)}
              onCopy={handleCopy}
            />
          ))}

          <ApplyProfileBoolField
            fieldKey="requiresSponsorship"
            label="Requires sponsorship"
            value={form.requiresSponsorship}
            copiedField={copiedField}
            onChange={(v) => updateBool("requiresSponsorship", v)}
            onCopy={handleCopy}
          />

          <ApplyProfileBoolField
            fieldKey="willingToRelocate"
            label="Willing to relocate"
            value={form.willingToRelocate}
            copiedField={copiedField}
            onChange={(v) => updateBool("willingToRelocate", v)}
            onCopy={handleCopy}
          />

          {APPLY_PROFILE_TEXT_FIELDS.slice(4).map(({ key, label, maxLength, placeholder }) => (
            <ApplyProfileTextField
              key={key}
              fieldKey={key}
              label={label}
              value={form[key]}
              maxLength={maxLength}
              placeholder={placeholder}
              error={fieldErrors[key]}
              copiedField={copiedField}
              onChange={(v) => updateText(key, v)}
              onCopy={handleCopy}
            />
          ))}

          <ApplyProfileLanguagesField
            languages={form.languages}
            error={fieldErrors.languages}
            copiedField={copiedField}
            onChangeLanguage={updateLanguage}
            onAdd={addLanguage}
            onRemove={removeLanguage}
            onCopy={handleCopy}
          />

          <ApplyProfileTextAreaField
            fieldKey="roomToGrow"
            label="Room to grow"
            value={form.roomToGrow}
            maxLength={2000}
            placeholder="Where do you want to grow next?"
            error={fieldErrors.roomToGrow}
            copiedField={copiedField}
            onChange={(v) => updateText("roomToGrow", v)}
            onCopy={handleCopy}
          />
        </div>
      </Card>

      <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 4 }}>
        <Button variant="primary" onClick={handleSave} disabled={saving} data-testid="apply-profile-save-button">
          {saving ? "Saving…" : "Save apply profile"}
        </Button>
        {lastSaved && (
          <span data-testid="apply-profile-last-saved" style={{ fontSize: 12, color: "var(--color-ink-3)" }}>
            Last saved {lastSaved}
          </span>
        )}
      </div>
    </div>
  );
}

// Deliberately no HTML maxLength attribute: the backend is the source of truth
// for length limits (BR-6). Hard-capping input client-side would make it
// impossible for the user to ever submit an over-length value and see the
// server's validation rejection (AC7) — the cap is enforced on save, not typing.
function ApplyProfileTextField({ fieldKey, label, value, placeholder, error, copiedField, onChange, onCopy }) {
  return (
    <div data-testid={`field-${fieldKey}`} style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
      <div style={{ flex: 1 }}>
        <Field label={label} error={error}>
          <Input
            value={value}
            placeholder={placeholder}
            onChange={(e) => onChange(e.target.value)}
            data-testid={`field-input-${fieldKey}`}
          />
        </Field>
      </div>
      <div style={{ marginTop: 22 }}>
        <ApplyProfileCopyButton fieldKey={fieldKey} label={label} value={value} copiedField={copiedField} onCopy={onCopy} />
      </div>
    </div>
  );
}

function ApplyProfileTextAreaField({ fieldKey, label, value, placeholder, error, copiedField, onChange, onCopy }) {
  return (
    <div data-testid={`field-${fieldKey}`} style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
      <div style={{ flex: 1 }}>
        <Field label={label} error={error}>
          <textarea
            className="input"
            value={value}
            placeholder={placeholder}
            rows={4}
            style={{ resize: "vertical", width: "100%" }}
            onChange={(e) => onChange(e.target.value)}
            data-testid={`field-input-${fieldKey}`}
          />
        </Field>
      </div>
      <div style={{ marginTop: 22 }}>
        <ApplyProfileCopyButton fieldKey={fieldKey} label={label} value={value} copiedField={copiedField} onCopy={onCopy} />
      </div>
    </div>
  );
}

const APPLY_PROFILE_TRISTATE_OPTIONS = [
  { key: "unset", value: null, text: "Not set" },
  { key: "yes", value: true, text: "Yes" },
  { key: "no", value: false, text: "No" },
];

function ApplyProfileBoolField({ fieldKey, label, value, copiedField, onChange, onCopy }) {
  const hasValue = value !== null && value !== undefined;
  const copyValue = hasValue ? (value ? "Yes" : "No") : null;
  return (
    <div data-testid={`field-${fieldKey}`} style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
      <div style={{ flex: 1 }}>
        <Field label={label}>
          <div role="group" aria-label={label} style={{ display: "flex", gap: 6 }}>
            {APPLY_PROFILE_TRISTATE_OPTIONS.map((opt) => (
              <Button
                key={opt.key}
                type="button"
                size="sm"
                variant={value === opt.value ? "primary" : "secondary"}
                aria-pressed={value === opt.value}
                data-testid={`bool-${fieldKey}-${opt.key}`}
                onClick={() => onChange(opt.value)}
              >
                {opt.text}
              </Button>
            ))}
          </div>
        </Field>
      </div>
      <div style={{ marginTop: 22 }}>
        <ApplyProfileCopyButton fieldKey={fieldKey} label={label} value={copyValue} copiedField={copiedField} onCopy={onCopy} />
      </div>
    </div>
  );
}

function ApplyProfileLanguagesField({ languages, error, copiedField, onChangeLanguage, onAdd, onRemove, onCopy }) {
  const nonEmpty = languages.filter((l) => l && l.trim() !== "");
  const copyValue = nonEmpty.length > 0 ? nonEmpty.join(", ") : null;
  return (
    <div data-testid="field-languages" style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
      <div style={{ flex: 1 }}>
        <Field label="Languages" error={error}>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {languages.map((lang, i) => (
              <div key={i} style={{ display: "flex", gap: 6 }}>
                <Input
                  value={lang}
                  maxLength={60}
                  placeholder="e.g. English (native)"
                  onChange={(e) => onChangeLanguage(i, e.target.value)}
                  data-testid={`language-input-${i}`}
                  style={{ flex: 1 }}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  icon="x"
                  aria-label={`Remove language ${i + 1}`}
                  data-testid={`language-remove-${i}`}
                  onClick={() => onRemove(i)}
                />
              </div>
            ))}
            <Button type="button" variant="secondary" size="sm" icon="plus" onClick={onAdd} data-testid="language-add">
              Add language
            </Button>
          </div>
        </Field>
      </div>
      <div style={{ marginTop: 22 }}>
        <ApplyProfileCopyButton fieldKey="languages" label="Languages" value={copyValue} copiedField={copiedField} onCopy={onCopy} />
      </div>
    </div>
  );
}

function SettingsRow({ name, desc, action }) {
  return (
    <div className="settings-row">
      <div className="lbl-block">
        <div className="name">{name}</div>
        {desc && <div className="desc">{desc}</div>}
      </div>
      {action}
    </div>
  );
}

function ChangePasswordRow({ account }) {
  const [open, setOpen] = React.useState(false);
  const [current, setCurrent] = React.useState("");
  const [newPw, setNewPw] = React.useState("");
  const [confirmPw, setConfirmPw] = React.useState("");
  const [totpCode, setTotpCode] = React.useState("");
  const [error, setError] = React.useState("");
  const [success, setSuccess] = React.useState(false);
  const [busy, setBusy] = React.useState(false);

  const has2fa = !!(account && account.twoFactorEnabled);

  const handleSave = async () => {
    setError("");
    if (!current) { setError("Enter your current password."); return; }
    if (newPw.length < 8) { setError("New password must be at least 8 characters."); return; }
    if (newPw !== confirmPw) { setError("New passwords don't match."); return; }
    if (current === newPw) { setError("New password must be different from current."); return; }

    setBusy(true);
    try {
      const payload = { currentPassword: current, newPassword: newPw };
      if (has2fa) payload.totpCode = totpCode;
      await changePassword(payload);
      setSuccess(true);
      setTimeout(() => {
        setOpen(false);
        setSuccess(false);
        setCurrent("");
        setNewPw("");
        setConfirmPw("");
        setTotpCode("");
      }, 1500);
    } catch (ex) {
      if (ex && ex.status === 401) {
        setError("Current password is incorrect.");
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <div className="settings-row">
        <div className="lbl-block">
          <div className="name">Password</div>
          <div className="desc">Update the password you use to sign in.</div>
        </div>
        <Button variant="secondary" onClick={() => setOpen(true)}>Change password</Button>
      </div>
      {open && (
        <UI.Modal title="Change password" onClose={() => { setOpen(false); setError(""); setSuccess(false); }}
          footer={
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", width: "100%" }}>
              <Button variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
              <Button variant="primary" onClick={handleSave} disabled={busy}>
                {busy ? "Updating…" : "Update password"}
              </Button>
            </div>
          }>
          {success ? (
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8, padding: "16px 0" }}>
              <div style={{ width: 40, height: 40, borderRadius: "50%", background: "var(--color-success-bg)", color: "var(--color-success)",
                display: "flex", alignItems: "center", justifyContent: "center" }}>
                <Icon name="check" size={20} />
              </div>
              <div style={{ fontSize: 14, fontWeight: 600, color: "var(--color-ink)" }}>Password updated</div>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <Field label="Current password">
                <Input type="password" placeholder="••••••••" value={current} onChange={(e) => setCurrent(e.target.value)} autoFocus />
              </Field>
              <Field label="New password" hint="At least 8 characters">
                <Input type="password" placeholder="New password" value={newPw} onChange={(e) => setNewPw(e.target.value)} />
              </Field>
              <Field label="Confirm new password">
                <Input type="password" placeholder="Repeat new password" value={confirmPw} onChange={(e) => setConfirmPw(e.target.value)} />
              </Field>
              {has2fa && (
                <Field label="Authentication code" hint="Enter the 6-digit code from your authenticator app, or a backup code.">
                  <Input
                    type="text"
                    inputMode="text"
                    placeholder="123456"
                    value={totpCode}
                    onChange={(e) => setTotpCode(e.target.value)}
                    maxLength={8}
                    aria-label="Authentication code"
                  />
                </Field>
              )}
              {error && <div role="alert" style={{ fontSize: 12, color: "var(--color-danger)", display: "flex", alignItems: "center", gap: 6 }}>
                <Icon name="info" size={13} />{error}
              </div>}
            </div>
          )}
        </UI.Modal>
      )}
    </>
  );
}

/* ─── Email verification: badge + resend/verify flow ───
   Story #301: surfaces emailVerified on the Account profile card, with a
   "Verify now" action that resends a 6-digit code (resendVerification) and
   reveals a code entry wired to verifyEmail, mirroring VerifyEmailScreen's
   code-entry pattern in Auth.jsx (inputMode="numeric", maxLength 6, strip
   non-digits, 400 -> invalid/expired copy, 429 -> "try again later" copy).
   Owns the "verified" flag locally (optimistic flip on success) since the
   account prop here isn't refetched by this component; the badge and the
   resend/verify row are rendered together so they stay in sync. */
function EmailVerificationSection({ account }) {
  const email = account?.email || "";
  const [verified, setVerified] = React.useState(!!account?.emailVerified);
  const [codeVisible, setCodeVisible] = React.useState(false);
  const [code, setCode] = React.useState("");
  const [resendBusy, setResendBusy] = React.useState(false);
  const [resendMsg, setResendMsg] = React.useState("");
  const [verifyBusy, setVerifyBusy] = React.useState(false);
  const [verifyErr, setVerifyErr] = React.useState("");

  const canSubmit = code.trim().length === 6 && !verifyBusy;

  async function handleVerifyNow() {
    if (resendBusy || !email) return;
    setResendMsg("");
    setResendBusy(true);
    try {
      await resendVerification(email);
      setCodeVisible(true);
    } catch (ex) {
      if (ex && ex.status === 429) {
        setResendMsg("Too many requests, try again later.");
      } else {
        setResendMsg("Couldn't send a new code. Please try again.");
      }
    } finally {
      setResendBusy(false);
    }
  }

  async function handleSubmitCode() {
    if (!canSubmit) return;
    setVerifyErr("");
    setVerifyBusy(true);
    try {
      await verifyEmail({ email, code: code.trim() });
      setVerified(true);
    } catch (ex) {
      if (ex && ex.status === 429) {
        setVerifyErr("Too many requests, try again later.");
      } else if (ex && ex.status === 400) {
        setVerifyErr("That code is invalid or has expired. Please check your email or request a new code.");
      } else {
        setVerifyErr(ex && ex.message ? ex.message : "Something went wrong. Please try again.");
      }
      setVerifyBusy(false);
    }
  }

  return (
    <div style={{ marginTop: 2, display: "flex", flexDirection: "column", gap: 8 }}>
      <div style={{ fontSize: 13, color: "var(--color-ink-3)", display: "flex", alignItems: "center", gap: 8 }}>
        <span>{email}</span>
        <span className={"email-verify-pill " + (verified ? "verified" : "unverified")}>
          <span className="dot" />
          {verified ? "Verified" : "Not verified"}
        </span>
      </div>

      {!verified && (
        !codeVisible ? (
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <Button variant="secondary" size="sm" onClick={handleVerifyNow} disabled={resendBusy || !email}>
              {resendBusy ? "Sending…" : "Verify now"}
            </Button>
            {resendMsg && (
              <span style={{ fontSize: 12, color: resendMsg.includes("try again later") ? "var(--color-danger)" : "var(--color-ink-3)" }}>
                {resendMsg}
              </span>
            )}
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            <div style={{ fontSize: 12, color: "var(--color-ink-3)" }}>
              We sent a 6-digit code to <strong>{email}</strong>.
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <Input
                type="text"
                inputMode="numeric"
                placeholder="123456"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                maxLength={6}
                aria-label="Verification code"
                style={{ maxWidth: 140 }}
              />
              <Button variant="primary" size="sm" onClick={handleSubmitCode} disabled={!canSubmit}>
                {verifyBusy ? "Verifying…" : "Submit code"}
              </Button>
            </div>
            {verifyErr && (
              <div role="alert" style={{ fontSize: 12, color: "var(--color-danger)", display: "flex", alignItems: "center", gap: 6 }}>
                <Icon name="info" size={13} />{verifyErr}
              </div>
            )}
          </div>
        )
      )}
    </div>
  );
}

/* ─── Two-factor authentication settings row ───
   Enable flow: setup -> QR/manual key + code entry -> verify-setup -> backup codes.
   Disable flow: confirm modal asking for a TOTP code -> disable. */
function TwoFactorSettingsRow({ account, pushToast }) {
  const enabled = !!(account && account.twoFactorEnabled);

  const [mode, setMode] = React.useState(null); // null | "enable" | "disable"

  // Enable-flow state
  const [setupData, setSetupData] = React.useState(null); // { otpauthUri, setupKey }
  const [setupCode, setSetupCode] = React.useState("");
  const [backupCodes, setBackupCodes] = React.useState(null); // string[] once verified
  const [setupError, setSetupError] = React.useState("");
  const [setupBusy, setSetupBusy] = React.useState(false);
  const [alreadyEnabledError, setAlreadyEnabledError] = React.useState(false);

  // Disable-flow state
  const [disableCode, setDisableCode] = React.useState("");
  const [disableError, setDisableError] = React.useState("");
  const [disableBusy, setDisableBusy] = React.useState(false);

  function resetEnableState() {
    setSetupData(null);
    setSetupCode("");
    setBackupCodes(null);
    setSetupError("");
    setAlreadyEnabledError(false);
  }

  function resetDisableState() {
    setDisableCode("");
    setDisableError("");
  }

  async function startEnable() {
    setMode("enable");
    resetEnableState();
    setSetupBusy(true);
    try {
      const data = await setupTwoFactor();
      setSetupData(data);
    } catch (ex) {
      if (ex && ex.status === 409) {
        setAlreadyEnabledError(true);
      } else {
        setSetupError("Couldn't start setup. Please try again.");
      }
    } finally {
      setSetupBusy(false);
    }
  }

  function startDisable() {
    setMode("disable");
    resetDisableState();
  }

  function closeModal() {
    setMode(null);
    resetEnableState();
    resetDisableState();
  }

  async function handleVerifySetup() {
    setSetupError("");
    if (!setupCode.trim()) { setSetupError("Enter the 6-digit code from your authenticator app."); return; }
    setSetupBusy(true);
    try {
      const data = await verifyTwoFactorSetup({ totpCode: setupCode.trim() });
      setBackupCodes(data && data.backupCodes ? data.backupCodes : []);
    } catch (ex) {
      if (ex && ex.status === 400) {
        setSetupError("That code is invalid or has expired. Please try again.");
      } else if (ex && ex.status === 409) {
        setAlreadyEnabledError(true);
      } else {
        setSetupError("Something went wrong. Please try again.");
      }
    } finally {
      setSetupBusy(false);
    }
  }

  function finishEnable() {
    closeModal();
    if (pushToast) pushToast("Two-factor authentication enabled.", "check");
  }

  async function handleDisable() {
    setDisableError("");
    if (!disableCode.trim()) { setDisableError("Enter your authentication code."); return; }
    setDisableBusy(true);
    try {
      await disableTwoFactor({ totpCode: disableCode.trim() });
      closeModal();
      if (pushToast) pushToast("Two-factor authentication disabled.", "check");
    } catch (ex) {
      if (ex && ex.status === 401) {
        setDisableError("That code is incorrect. Please try again.");
      } else {
        setDisableError("Something went wrong. Please try again.");
      }
    } finally {
      setDisableBusy(false);
    }
  }

  return (
    <>
      <SettingsRow
        name="Two-factor auth"
        desc="Add a second step at sign-in."
        action={
          <Toggle
            on={enabled}
            onChange={() => { enabled ? startDisable() : startEnable(); }}
            aria-label="Two-factor auth"
          />
        }
      />

      {mode === "enable" && (
        <UI.Modal title="Enable two-factor authentication" onClose={closeModal}
          footer={
            backupCodes ? (
              <div style={{ display: "flex", justifyContent: "flex-end", width: "100%" }}>
                <Button variant="primary" onClick={finishEnable}>Done</Button>
              </div>
            ) : (
              <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", width: "100%" }}>
                <Button variant="ghost" onClick={closeModal}>Cancel</Button>
                <Button variant="primary" onClick={handleVerifySetup} disabled={setupBusy || !setupData}>
                  {setupBusy ? "Verifying…" : "Verify"}
                </Button>
              </div>
            )
          }>
          {alreadyEnabledError ? (
            <div role="alert" style={{ fontSize: 13, color: "var(--color-danger)" }}>
              Two-factor authentication is already active on this account.
            </div>
          ) : backupCodes ? (
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <div style={{ fontSize: 13, color: "var(--color-ink-2)" }}>
                Store these backup codes somewhere safe. They won't be shown again.
              </div>
              <div style={{
                display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8,
                fontFamily: "var(--font-mono)", fontSize: 14, padding: 12,
                background: "var(--color-bg-2, #f7f7f8)", borderRadius: 8,
              }}>
                {backupCodes.map((code) => <div key={code}>{code}</div>)}
              </div>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              {setupData ? (
                <>
                  <div style={{ fontSize: 13, color: "var(--color-ink-2)" }}>
                    Scan this with your authenticator app, or enter the setup key manually.
                  </div>
                  <img
                    alt="Scan this QR code with your authenticator app"
                    width={200}
                    height={200}
                    src={`https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(setupData.otpauthUri)}`}
                  />
                  <Field label="Setup key (manual entry)">
                    <Input type="text" readOnly value={setupData.setupKey} />
                  </Field>
                  <Field label="Authentication code" hint="Enter the 6-digit code shown by your authenticator app.">
                    <Input
                      type="text"
                      placeholder="123456"
                      value={setupCode}
                      onChange={(e) => setSetupCode(e.target.value)}
                      maxLength={6}
                      autoFocus
                      aria-label="Authentication code"
                    />
                  </Field>
                </>
              ) : (
                <div style={{ fontSize: 13, color: "var(--color-ink-3)" }}>Setting up…</div>
              )}
              {setupError && (
                <div role="alert" style={{ fontSize: 12, color: "var(--color-danger)", display: "flex", alignItems: "center", gap: 6 }}>
                  <Icon name="info" size={13} />{setupError}
                </div>
              )}
            </div>
          )}
        </UI.Modal>
      )}

      {mode === "disable" && (
        <UI.Modal title="Disable two-factor authentication" onClose={closeModal}
          footer={
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", width: "100%" }}>
              <Button variant="ghost" onClick={closeModal}>Cancel</Button>
              <Button variant="danger" onClick={handleDisable} disabled={disableBusy}>
                {disableBusy ? "Disabling…" : "Disable"}
              </Button>
            </div>
          }>
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div style={{ fontSize: 13, color: "var(--color-ink-2)" }}>
              Enter a code from your authenticator app (or a backup code) to confirm.
            </div>
            <Field label="Authentication code">
              <Input
                type="text"
                placeholder="123456"
                value={disableCode}
                onChange={(e) => setDisableCode(e.target.value)}
                maxLength={8}
                autoFocus
                aria-label="Authentication code"
              />
            </Field>
            {disableError && (
              <div role="alert" style={{ fontSize: 12, color: "var(--color-danger)", display: "flex", alignItems: "center", gap: 6 }}>
                <Icon name="info" size={13} />{disableError}
              </div>
            )}
          </div>
        </UI.Modal>
      )}
    </>
  );
}

function DeleteAccountRow() {
  const [stage, setStage] = React.useState("idle"); // idle | confirm | typing
  const [confirmText, setConfirmText] = React.useState("");
  const CONFIRM_PHRASE = "delete my account";
  const isMatch = confirmText.toLowerCase().trim() === CONFIRM_PHRASE;

  if (stage === "idle") {
    return (
      <div className="settings-row">
        <div className="lbl-block">
          <div className="name">Delete account</div>
          <div className="desc">Permanently removes your data within 30 days.</div>
        </div>
        <Button variant="ghost" style={{ color: "var(--color-danger)" }} onClick={() => setStage("confirm")}>Delete account</Button>
      </div>
    );
  }

  return (
    <div style={{ padding: 16, border: "1px solid var(--color-danger-border)", borderRadius: 10, background: "var(--color-danger-bg)", display: "flex", flexDirection: "column", gap: 12 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <Icon name="info" size={16} style={{ color: "var(--color-danger)", flexShrink: 0 }} />
        <div style={{ fontSize: 14, fontWeight: 600, color: "var(--color-danger)" }}>Delete your account?</div>
      </div>
      <div style={{ fontSize: 13, color: "var(--color-ink-2)", lineHeight: 1.5 }}>
        This will permanently delete all your applications, saved jobs, notes, and settings. This action cannot be undone.
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        <label style={{ fontSize: 12, fontWeight: 500, color: "var(--color-ink-2)" }}>
          Type <span style={{ fontFamily: "var(--font-mono)", fontWeight: 600, color: "var(--color-danger)" }}>{CONFIRM_PHRASE}</span> to confirm
        </label>
        <Input value={confirmText} onChange={(e) => setConfirmText(e.target.value)}
          placeholder={CONFIRM_PHRASE}
          style={isMatch ? { borderColor: "var(--color-danger)", boxShadow: "var(--ring-danger)" } : {}} />
      </div>
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
        <Button variant="ghost" size="sm" onClick={() => { setStage("idle"); setConfirmText(""); }}>Cancel</Button>
        <Button variant="danger" size="sm" icon="trash" disabled={!isMatch}>Delete permanently</Button>
      </div>
    </div>
  );
}

export { SavedScreen, SettingsScreen };
