import React from "react";
import Icon from "./Icon.jsx";
import DATA from "../data/mockData.js";
import { accountName, accountInitials } from "../api/mappers.js";
// JobHub — Shared components
const { useState, useEffect, useMemo, useRef, useCallback } = React;

// Lets the Topbar trigger the mobile nav drawer without prop-threading through every screen.
const MobileNavContext = React.createContext(null);

// "Posted" age label: same-day reads as "Today", otherwise "Nd ago".
function postedLabel(days) {
  return days <= 0 ? "Today" : `${days}d ago`;
}

/* ─── Buttons / inputs ─── */
function Button({ variant = "secondary", size, icon, iconRight, children, className = "", ...rest }) {
  const cls = ["btn", variant, size, icon && !children ? "icon" : "", className].filter(Boolean).join(" ");
  return (
    <button className={cls} {...rest}>
      {icon && <Icon name={icon} size={size === "sm" ? 14 : 16} />}
      {children}
      {iconRight && <Icon name={iconRight} size={size === "sm" ? 14 : 16} />}
    </button>
  );
}

function Input({ leading, trailing, className = "", ...rest }) {
  if (!leading && !trailing) return <input className={"input " + className} {...rest} />;
  return (
    <div className="input-wrap">
      {leading && <span className="leading"><Icon name={leading} size={14} /></span>}
      <input className={"input " + (leading ? "with-leading " : "") + className} {...rest} />
      {trailing && <span className="trailing"><Icon name={trailing} size={14} /></span>}
    </div>
  );
}

function Field({ label, hint, error, children }) {
  return (
    <div className="field">
      {label && <label className="field-label">{label}</label>}
      {children}
      {error ? <div className="field-error">{error}</div> : hint && <div className="field-hint">{hint}</div>}
    </div>
  );
}

function Toggle({ on, onChange }) {
  return <div className={"toggle " + (on ? "on" : "")} onClick={() => onChange?.(!on)} role="switch" aria-checked={on} />;
}

/* ─── Status pill ─── */
const STATUS_LABEL = {
  saved: "Saved", applied: "Applied", screening: "Screening",
  interview: "Interview", offer: "Offer", accepted: "Accepted",
  rejected: "Rejected", ghosted: "Ghosted", withdrawn: "Withdrawn",
};
function StatusPill({ status }) {
  return <span className={"status " + status}><span className="dot" />{STATUS_LABEL[status]}</span>;
}

/* ─── Company logo chip ─── */
function CoLogo({ co, size }) {
  const c = DATA.coOf(co);
  return <div className={"cologo " + (size === "sm" ? "sm" : size === "lg" ? "lg" : "")} data-co={co}>{(c.name || "?").charAt(0)}</div>;
}

/* ─── Avatar ─── */
function Avatar({ initials = "", size = 28 }) {
  return (
    <div style={{ width: size, height: size, borderRadius: "50%", background: "var(--color-brand-600)", color: "#fff",
      display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 600, fontSize: size <= 24 ? 10 : 12,
    }}>{initials}</div>
  );
}

/* ─── Sidebar ─── */
function Sidebar({ current, onNav, appCounts, savedCount, authed, account, mobileOpen, onClose }) {
  const items = [
    { id: "search", label: "Job search", icon: "search" },
    { id: "saved", label: "Saved", icon: "bookmark", count: savedCount, auth: true },
    { id: "applications", label: "Applications", icon: "briefcase", count: appCounts.total, auth: true },
    { id: "dashboard", label: "Dashboard", icon: "layout-dashboard", auth: true },
  ];

  return (
    <>
      {mobileOpen && <div className="sidebar-backdrop" onClick={onClose} />}
      <aside className={"sidebar " + (mobileOpen ? "mobile-open" : "")}>
      <div className="sidebar-brand">
        <img src="/assets/logo-mark.svg" width="22" height="22" alt="" />
        <span className="wordmark">JobHub</span>
        <div style={{ flex: 1 }} />
        <button className="sidebar-close" onClick={onClose} aria-label="Close menu">
          <Icon name="x" size={18} />
        </button>
      </div>

      <div className="sidebar-section">
        {items.map((it) => (
          <a key={it.id} className={"nav-item " + (current === it.id ? "active" : "")}
            onClick={() => onNav(it.id)} style={it.auth && !authed ? { opacity: 0.5 } : {}}>
            <span className="ico"><Icon name={it.icon} size={16} /></span>
            <span className="label">{it.label}</span>
            {it.count != null && <span className="count">{it.count}</span>}
          </a>
        ))}
      </div>

      {authed && (
        <div className="sidebar-section">
          <div className="eyebrow">In progress</div>
          <a className="nav-item" onClick={() => onNav("applications")}>
            <span className="status interview" style={{padding:0,border:0,background:"transparent"}}><span className="dot" /></span>
            <span className="label">Interviewing</span>
            <span className="count">{appCounts.interview}</span>
          </a>
          <a className="nav-item" onClick={() => onNav("applications")}>
            <span className="status applied" style={{padding:0,border:0,background:"transparent"}}><span className="dot" /></span>
            <span className="label">Awaiting reply</span>
            <span className="count">{appCounts.applied}</span>
          </a>
        </div>
      )}

      <div className="sidebar-footer">
        <a className={"nav-item " + (current === "settings" ? "active" : "")} onClick={() => onNav("settings")}>
          <span className="ico"><Icon name="settings" size={16} /></span>
          <span className="label">Settings</span>
        </a>
        {authed ? (
          <div className="user-pill" onClick={() => onNav("settings")}>
            <Avatar initials={accountInitials(account) || "?"} />
            <div className="who">
              <span className="name">{accountName(account) || "Account"}</span>
              <span className="email">{account?.email || ""}</span>
            </div>
          </div>
        ) : (
          <div className="user-pill" onClick={() => onNav("login")}>
            <div style={{ width: 28, height: 28, borderRadius: "50%", background: "var(--color-surface-2)", border: "1px solid var(--color-border)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <Icon name="user" size={14} style={{ color: "var(--color-ink-3)" }} />
            </div>
            <div className="who">
              <span className="name" style={{ color: "var(--color-brand-600)" }}>Sign in</span>
              <span className="email">Track your applications</span>
            </div>
          </div>
        )}
      </div>
      </aside>
    </>
  );
}

/* ─── Topbar ─── */
function Topbar({ title, sub, actions, searchLabel, onSearchClick }) {
  const mobileNav = React.useContext(MobileNavContext);
  return (
    <div className="topbar">
      {mobileNav && (
        <button className="topbar-menu" onClick={mobileNav.openNav} aria-label="Open menu">
          <Icon name="sliders-horizontal" size={18} />
        </button>
      )}
      <div className="topbar-titles">
        <div className="page-title">{title}</div>
        {sub && <div className="sub" style={{marginTop:2,fontSize:12}}>{sub}</div>}
      </div>
      <div className="spacer" />
      <div className="cmdk" tabIndex={0} onClick={onSearchClick}>
        <Icon name="search" size={14} />
        <span className="placeholder">{searchLabel || "Search…"}</span>
        <span className="kbd">⌘K</span>
      </div>
      {actions}
    </div>
  );
}

/* ─── Card ─── */
function Card({ title, sub, action, pad, children, className = "", style: cardStyle }) {
  return (
    <div className={"card " + className} style={cardStyle}>
      {title && (
        <div className="card-header">
          <span className="title">{title}</span>
          {sub && <span className="sub">{sub}</span>}
          {action}
        </div>
      )}
      <div className={pad === false ? "" : "card-pad"}>{children}</div>
    </div>
  );
}

/* ─── Stat card ─── */
function Stat({ label, value, delta, deltaTone = "neutral" }) {
  return (
    <div className="card card-pad">
      <div className="eyebrow">{label}</div>
      <div style={{ fontSize: 28, fontWeight: 600, color: "var(--color-ink)", letterSpacing: "-0.022em", marginTop: 8, lineHeight: 1 }}>{value}</div>
      {delta && (
        <div className="mono" style={{ fontSize: 11, marginTop: 6, color: deltaTone === "up" ? "var(--color-success)" : deltaTone === "down" ? "var(--color-danger)" : "var(--color-ink-3)" }}>
          {delta}
        </div>
      )}
    </div>
  );
}

/* ─── Modal ─── */
function Modal({ title, onClose, children, footer, wide }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === "Escape") onClose?.(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onClick={(e) => e.target === e.currentTarget && onClose?.()}>
      <div className="modal" role="dialog" aria-modal="true" style={wide ? { width: "min(680px, 92vw)" } : {}}>
        <div className="modal-head">
          <span className="title">{title}</span>
          <Button variant="ghost" size="sm" icon="x" onClick={onClose} aria-label="Close" />
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

/* ─── Toasts ─── */
function ToastTray({ toasts }) {
  return (
    <div className="toast-tray">
      {toasts.map((t) => (
        <div key={t.id} className="toast">
          <Icon name={t.icon || "check"} size={14} />
          <span style={{ flex: 1 }}>{t.text}</span>
          {t.action && (
            <span onClick={t.action.fn} style={{ color: "var(--color-brand-200)", fontWeight: 600, cursor: "pointer", fontSize: 12, whiteSpace: "nowrap", textDecoration: "underline", textUnderlineOffset: 2 }}>
              {t.action.label}
            </span>
          )}
        </div>
      ))}
    </div>
  );
}

/* ─── Empty state ─── */
function Empty({ icon = "briefcase", title, desc, cta }) {
  return (
    <div className="empty">
      <span className="ico"><Icon name={icon} size={28} /></span>
      <div className="ttl">{title}</div>
      {desc && <div className="desc">{desc}</div>}
      {cta && <div className="cta">{cta}</div>}
    </div>
  );
}

/* ─── Tabs ─── */
function Tabs({ value, onChange, tabs }) {
  return (
    <div className="tabs">
      {tabs.map((t) => (
        <div key={t.id} className={"tab " + (value === t.id ? "active" : "")} onClick={() => onChange(t.id)}>
          {t.label}{t.count != null && <span className="count">{t.count}</span>}
        </div>
      ))}
    </div>
  );
}

/* ─── Job row (search results) ─── */
function JobRow({ job, onSave, isSaved, onOpen, isApplied }) {
  const c = DATA.coOf(job.co);
  return (
    <div className="card card-pad job-row-card" style={{ display: "grid", gridTemplateColumns: "44px 1fr auto", gap: 14, alignItems: "center", cursor: "pointer" }}
      onClick={() => onOpen?.(job)}>
      <CoLogo co={job.co} size="lg" />
      <div style={{ display: "flex", flexDirection: "column", gap: 4, minWidth: 0 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: "var(--color-ink)", letterSpacing: "-0.012em" }}>{job.title}</div>
          {job.postedDays <= 3 && <span style={{ padding: "1px 7px", borderRadius: 4, fontSize: 10, fontWeight: 600, background: "var(--color-brand-50)", color: "var(--color-brand-700)" }}>NEW</span>}
          {isApplied && <span style={{ padding: "1px 7px", borderRadius: 4, fontSize: 10, fontWeight: 600, background: "var(--color-success-bg)", color: "var(--color-success)" }}>APPLIED</span>}
        </div>
        <div style={{ display: "flex", gap: 8, fontSize: 12, color: "var(--color-ink-3)", alignItems: "center", flexWrap: "wrap" }}>
          <span style={{ color: "var(--color-ink-2)", fontWeight: 500 }}>{c.name}</span>
          <span className="dot-sep" />
          <span>{job.location}</span>
          <span className="dot-sep" />
          <span className="mono">{job.comp}</span>
          <span className="dot-sep" />
          <span style={{ whiteSpace: "nowrap" }}>{job.postedDays <= 0 ? "Posted today" : `Posted ${job.postedDays}d ago`}</span>
        </div>
      </div>
      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <Button variant="ghost" size="sm" icon="bookmark"
          onClick={(e) => { e.stopPropagation(); onSave?.(job); }}
          style={isSaved ? { color: "var(--color-brand-600)" } : {}}
          aria-label={isSaved ? "Unsave" : "Save"} />
        <Button variant="secondary" size="sm" iconRight="chevron-right"
          onClick={(e) => { e.stopPropagation(); onOpen?.(job); }}>View</Button>
      </div>
    </div>
  );
}

export {
  Button, Input, Field, Toggle,
  StatusPill, CoLogo, Avatar,
  Sidebar, Topbar,
  Card, Stat, Modal, ToastTray, Empty, Tabs, JobRow,
  STATUS_LABEL,
  MobileNavContext,
  postedLabel,
};
