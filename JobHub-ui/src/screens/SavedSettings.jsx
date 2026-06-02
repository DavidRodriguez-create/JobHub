import React from "react";
import Icon from "../components/Icon.jsx";
import DATA from "../data/mockData.js";
import * as UI from "../components/ui.jsx";
import { accountName, accountInitials } from "../api/mappers.js";
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
function SettingsScreen({ authed, account, onLogout, onLogin, openSearch }) {
  const [section, setSection] = React.useState("account");
  const [emailDigest, setEmailDigest] = React.useState(true);
  const [browserNotif, setBrowserNotif] = React.useState(false);
  const [interview, setInterview] = React.useState(true);
  const [ghostAlert, setGhostAlert] = React.useState(true);

  return (
    <>
      <UI.Topbar title="Settings" searchLabel="Search settings…" onSearchClick={openSearch} />
      <div className="content">
        <div className="settings-grid">
          <nav className="settings-nav">
            {[
              ["account", "Account"],
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
                          <div style={{ fontSize: 13, color: "var(--color-ink-3)", marginTop: 2 }}>{account?.email || ""}</div>
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
                    <ChangePasswordRow />
                    <SettingsRow name="Two-factor auth" desc="Add a second step at sign-in." action={<Toggle on={false} />} />
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

            {section === "notifications" && (
              <>
                <div><h3>Notifications</h3><p style={{ marginTop: 4 }}>What we tell you, and how.</p></div>
                <SettingsRow name="Weekly digest email" desc="A Monday summary of new jobs matching your filters." action={<Toggle on={emailDigest} onChange={setEmailDigest} />} />
                <SettingsRow name="Browser notifications" desc="Used for interview reminders and offer responses." action={<Toggle on={browserNotif} onChange={setBrowserNotif} />} />
                <SettingsRow name="Interview reminders" desc="Pinged 24 hours and 1 hour before scheduled events." action={<Toggle on={interview} onChange={setInterview} />} />
                <SettingsRow name="Ghosted alert" desc="Suggest a follow-up when silent for 14 days." action={<Toggle on={ghostAlert} onChange={setGhostAlert} />} />
              </>
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

function ChangePasswordRow() {
  const [open, setOpen] = React.useState(false);
  const [current, setCurrent] = React.useState("");
  const [newPw, setNewPw] = React.useState("");
  const [confirmPw, setConfirmPw] = React.useState("");
  const [error, setError] = React.useState("");
  const [success, setSuccess] = React.useState(false);

  const handleSave = () => {
    setError("");
    if (!current) { setError("Enter your current password."); return; }
    if (newPw.length < 8) { setError("New password must be at least 8 characters."); return; }
    if (newPw !== confirmPw) { setError("New passwords don't match."); return; }
    if (current === newPw) { setError("New password must be different from current."); return; }
    setSuccess(true);
    setTimeout(() => { setOpen(false); setSuccess(false); setCurrent(""); setNewPw(""); setConfirmPw(""); }, 1500);
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
              <Button variant="primary" onClick={handleSave}>Update password</Button>
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
              {error && <div style={{ fontSize: 12, color: "var(--color-danger)", display: "flex", alignItems: "center", gap: 6 }}>
                <Icon name="info" size={13} />{error}
              </div>}
            </div>
          )}
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
