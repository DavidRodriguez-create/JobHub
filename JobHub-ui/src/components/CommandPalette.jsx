import React from "react";
import Icon from "./Icon.jsx";
import DATA from "../data/mockData.js";

// JobHub — Command palette / search overlay
import { Button, CoLogo, StatusPill } from "./ui.jsx";

// Static settings/navigation index (story #304). Client-side only: no API call, no
// backend contract. Each entry's `keywords` (plus its own label) forms the alias text
// matched against the trimmed, lower-cased query (BR-3). `section` targets a
// SettingsScreen section; `adminOnly` entries additionally target the top-level
// "admin" route instead of a Settings section (BR-6/BR-7).
const SETTINGS_INDEX = [
  { label: "Account settings", keywords: ["account", "profile", "name", "email", "photo", "sign out", "logout"], section: "account" },
  { label: "Change password", keywords: ["password", "change password", "pwd", "security"], section: "account" },
  { label: "Two-factor auth", keywords: ["two-factor", "two factor", "2fa", "totp", "authenticator", "mfa"], section: "account" },
  { label: "Notification preferences", keywords: ["notification", "notifications", "alerts", "reminders", "digest", "email preferences"], section: "notifications" },
  { label: "Sources & filters", keywords: ["sources", "filters", "boards", "greenhouse", "lever", "ashby", "linkedin"], section: "sources" },
  { label: "Integrations", keywords: ["integrations", "calendar", "gmail", "notion", "connect"], section: "integrations" },
  { label: "Billing", keywords: ["billing", "plan", "subscription", "upgrade", "pricing"], section: "billing" },
  { label: "Data & privacy", keywords: ["data", "privacy", "export", "delete account", "analytics"], section: "data" },
  { label: "Admin panel", keywords: ["admin", "admin panel", "trigger", "crawl"], route: "admin", adminOnly: true },
];

function CommandPalette({ mode, onClose, onSelectJob, onSelectApp, onSelectSettings, isAdmin }) {
  const [query, setQuery] = React.useState("");
  const inputRef = React.useRef(null);
  const [selectedIdx, setSelectedIdx] = React.useState(0);

  React.useEffect(() => {
    inputRef.current?.focus();
  }, []);

  React.useEffect(() => {
    const h = (e) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [onClose]);

  // Reset selection when query changes
  React.useEffect(() => { setSelectedIdx(0); }, [query]);

  const isSettingsMode = mode === "settings";
  const isAppMode = mode === "applications" || mode === "dashboard" || mode === "application";

  // Search results
  const results = React.useMemo(() => {
    const q = query.toLowerCase().trim();
    if (isSettingsMode) {
      // Settings/navigation index (BR-1..BR-6): a static, client-side catalogue in
      // place of the job/application search while on the Settings screen.
      // Result cap (BR-5): consistent with the existing modes' `.slice(0, 8)`, sized up
      // to the full catalogue length so the 9th (admin-only) entry is never trimmed
      // purely because of cap arithmetic (AC-1/TC-304-02: 9 entries for an admin).
      return SETTINGS_INDEX
        .filter((entry) => !entry.adminOnly || isAdmin)
        .filter((entry) => {
          if (!q) return true;
          return `${entry.label} ${entry.keywords.join(" ")}`.toLowerCase().includes(q);
        })
        .slice(0, SETTINGS_INDEX.length)
        .map((entry) => ({ type: "settings", id: entry.label, entry }));
    } else if (isAppMode) {
      // Search applications
      return DATA.applications
        .filter((a) => {
          const j = DATA.byId(a.jobId);
          const c = DATA.coOf(j.co);
          if (!q) return true;
          return `${j.title} ${c.name} ${a.status} ${a.id}`.toLowerCase().includes(q);
        })
        .slice(0, 8)
        .map((a) => {
          const j = DATA.byId(a.jobId);
          const c = DATA.coOf(j.co);
          return { type: "app", id: a.id, app: a, job: j, company: c };
        });
    } else {
      // Search jobs
      return DATA.jobs
        .filter((j) => {
          const c = DATA.coOf(j.co);
          if (!q) return true;
          return `${j.title} ${c.name} ${j.location} ${j.tags.join(" ")}`.toLowerCase().includes(q);
        })
        .slice(0, 8)
        .map((j) => {
          const c = DATA.coOf(j.co);
          return { type: "job", id: j.id, job: j, company: c };
        });
    }
  }, [query, isAppMode, isSettingsMode, isAdmin]);

  const handleSelect = (item) => {
    if (item.type === "settings") onSelectSettings?.(item.entry);
    else if (item.type === "app") onSelectApp?.(item.app);
    else onSelectJob?.(item.job);
    onClose();
  };

  const handleKeyDown = (e) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelectedIdx((i) => Math.min(i + 1, results.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelectedIdx((i) => Math.max(i - 1, 0));
    } else if (e.key === "Enter" && results[selectedIdx]) {
      e.preventDefault();
      handleSelect(results[selectedIdx]);
    }
  };

  const cmdStyles = {
    backdrop: {
      position: "fixed", inset: 0, background: "rgba(11, 18, 32, 0.32)",
      display: "flex", alignItems: "flex-start", justifyContent: "center",
      paddingTop: "min(20vh, 140px)", zIndex: 60, animation: "fade-in 120ms ease",
    },
    panel: {
      width: "min(560px, 92vw)", background: "var(--color-surface)",
      border: "1px solid var(--color-border)", borderRadius: 14,
      boxShadow: "var(--shadow-pop)", overflow: "hidden",
      animation: "modal-in 150ms ease",
    },
    inputWrap: {
      display: "flex", alignItems: "center", gap: 10, padding: "14px 18px",
      borderBottom: "1px solid var(--color-border)",
    },
    input: {
      flex: 1, border: "none", outline: "none", background: "transparent",
      fontSize: 15, color: "var(--color-ink)", fontFamily: "var(--font-sans)",
      letterSpacing: "-0.012em",
    },
    hint: {
      fontSize: 11, color: "var(--color-ink-4)", padding: "8px 18px",
      borderBottom: "1px solid var(--color-border)", fontWeight: 500,
      textTransform: "uppercase", letterSpacing: "0.06em",
    },
    list: { maxHeight: 360, overflowY: "auto" },
    row: (active) => ({
      display: "flex", alignItems: "center", gap: 12, padding: "10px 18px",
      cursor: "pointer", transition: "background 80ms",
      background: active ? "var(--color-surface-2)" : "transparent",
    }),
    empty: {
      padding: "32px 18px", textAlign: "center", color: "var(--color-ink-3)", fontSize: 13,
    },
  };

  return (
    <div style={cmdStyles.backdrop} onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div style={cmdStyles.panel}>
        <div style={cmdStyles.inputWrap}>
          <Icon name="search" size={18} style={{ color: "var(--color-ink-3)", flexShrink: 0 }} />
          <input ref={inputRef} style={cmdStyles.input}
            placeholder={isSettingsMode ? "Search settings…" : isAppMode ? "Search applications…" : "Search jobs…"}
            value={query} onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown} />
          <span style={{ fontSize: 11, color: "var(--color-ink-4)", fontFamily: "var(--font-mono)",
            padding: "2px 6px", border: "1px solid var(--color-border-2)", borderRadius: 4,
            background: "var(--color-surface-2)" }}>ESC</span>
        </div>

        <div style={cmdStyles.hint}>
          {isSettingsMode ? "Settings" : isAppMode ? "Applications" : "Jobs"}
        </div>

        <div style={cmdStyles.list}>
          {results.length === 0 ? (
            <div style={cmdStyles.empty}>
              No {isSettingsMode ? "settings" : isAppMode ? "applications" : "jobs"} found for "{query}"
            </div>
          ) : (
            results.map((item, i) => (
              item.type === "settings" ? (
                <div key={item.id} data-testid="settings-result-row" style={cmdStyles.row(i === selectedIdx)}
                  onClick={() => handleSelect(item)}
                  onMouseEnter={() => setSelectedIdx(i)}>
                  <Icon name="settings" size={18} style={{ color: "var(--color-ink-3)", flexShrink: 0 }} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 500, color: "var(--color-ink)", letterSpacing: "-0.012em",
                      overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {item.entry.label}
                    </div>
                  </div>
                </div>
              ) : (
                <div key={item.id} style={cmdStyles.row(i === selectedIdx)}
                  onClick={() => handleSelect(item)}
                  onMouseEnter={() => setSelectedIdx(i)}>
                  <CoLogo co={item.job.co} size="sm" />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 500, color: "var(--color-ink)", letterSpacing: "-0.012em",
                      overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {item.job.title}
                    </div>
                    <div style={{ fontSize: 12, color: "var(--color-ink-3)", marginTop: 1 }}>
                      {item.company.name} · {item.job.location}
                    </div>
                  </div>
                  {item.type === "app" && <StatusPill status={item.app.status} />}
                  {item.type === "job" && (
                    <span className="mono" style={{ fontSize: 11, color: "var(--color-ink-3)" }}>{item.job.comp}</span>
                  )}
                </div>
              )
            ))
          )}
        </div>
      </div>
    </div>
  );
}

export { CommandPalette };
