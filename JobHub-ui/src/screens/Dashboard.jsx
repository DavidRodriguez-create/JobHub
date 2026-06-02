import React from "react";
import Icon from "../components/Icon.jsx";
import DATA from "../data/mockData.js";
import * as UI from "../components/ui.jsx";
// JobHub — Dashboard screen with statistics
const { Button, StatusPill, CoLogo, Card, Stat, Empty } = UI;

function DashboardScreen({ goto, openApp, openSearch, onAddApp, stats }) {
  const apps = DATA.applications;
  const urgent = apps.filter((a) => a.status === "offer");
  const upcoming = apps.filter((a) => ["interview", "screening"].includes(a.status) && a.nextStep && a.nextStep !== "—");
  const awaiting = apps.filter((a) => a.status === "applied");
  const stale = apps.filter((a) => a.status === "ghosted");

  const pipeline = [
    { label: "Applied",   status: "applied",   n: apps.filter(a => a.status === "applied").length,   color: "var(--status-applied-fg)" },
    { label: "Screening", status: "screening", n: apps.filter(a => a.status === "screening").length, color: "var(--status-screening-fg)" },
    { label: "Interview", status: "interview", n: apps.filter(a => a.status === "interview").length, color: "var(--status-interview-fg)" },
    { label: "Offer",     status: "offer",     n: apps.filter(a => a.status === "offer").length,     color: "var(--status-offer-fg)" },
    { label: "Rejected",  status: "rejected",  n: apps.filter(a => a.status === "rejected").length,  color: "var(--status-rejected-fg)" },
    { label: "Ghosted",   status: "ghosted",   n: apps.filter(a => a.status === "ghosted").length,   color: "var(--color-ink-4)" },
  ];
  const pipeTotal = pipeline.reduce((s, p) => s + p.n, 0);

  // Funnel chart data
  const funnelStages = [
    { label: "Applied", n: pipeTotal, color: "var(--status-applied-fg)" },
    { label: "Screen", n: apps.filter(a => ["screening","interview","offer"].includes(a.status)).length, color: "var(--status-screening-fg)" },
    { label: "Interview", n: apps.filter(a => ["interview","offer"].includes(a.status)).length, color: "var(--status-interview-fg)" },
    { label: "Offer", n: apps.filter(a => a.status === "offer").length, color: "var(--status-offer-fg)" },
  ];
  const funnelMax = Math.max(1, funnelStages[0].n);

  // Headline metrics come from the stats endpoint (GET /applications/stats), with a
  // fallback to client-side counts when stats haven't loaded (e.g. standalone mode).
  const fmtPct = (v) => (v == null ? "—" : `${Math.round(v)}%`);
  const fmtDays = (v) => (v == null ? "—" : `${Math.round(v)}d`);
  const fmtDate = (d) => {
    if (!d) return null;
    const dt = new Date(`${d}T00:00:00`);
    return Number.isNaN(dt.getTime()) ? null : dt.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  };

  const totalApplied = stats?.total ?? pipeTotal;
  const activeCount = stats?.activeCount ?? apps.filter((a) => !["rejected", "ghosted"].includes(a.status)).length;
  const interviews = stats?.byStatus?.interviewing ?? apps.filter((a) => a.status === "interview").length;
  const offers = stats?.byStatus?.offered ?? apps.filter((a) => a.status === "offer").length;
  const monthlyNew = stats?.monthlyNew ?? 0;
  const respondBy = fmtDate(stats?.nextDeadline?.date);

  const inlineStats = [
    { label: "Response rate", value: fmtPct(stats?.responseRate) },
    { label: "Avg reply", value: fmtDays(stats?.avgReplyDays) },
    { label: "Pass-through", value: fmtPct(stats?.passThrough) },
  ];

  const today = new Date().toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" });

  return (
    <>
      <UI.Topbar
        title="Dashboard"
        sub={today}
        searchLabel="Search applications…"
        onSearchClick={openSearch}
        actions={<Button variant="primary" icon="plus" onClick={onAddApp}>Add application</Button>}
      />
      <div className="content">

        {/* Urgent action cards */}
        {urgent.length > 0 && (
          <div style={{ marginBottom: 20 }}>
            {urgent.map((a) => {
              const j = DATA.byId(a.jobId); const c = DATA.coOf(j.co);
              return (
                <div key={a.id} onClick={() => openApp(a)} className="dash-urgent" style={{
                  display: "flex", alignItems: "center", gap: 16, padding: "14px 18px",
                  background: "var(--color-success-bg)", border: "1px solid var(--color-success-border)",
                  borderRadius: 12, cursor: "pointer",
                }}>
                  <div style={{ width: 40, height: 40, borderRadius: 10, background: "var(--color-success)", color: "#fff",
                    display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Icon name="check" size={20} />
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 14, fontWeight: 600, color: "var(--color-ink)", letterSpacing: "-0.012em" }}>
                      Offer from {c.name} — {j.title}
                    </div>
                    <div style={{ fontSize: 12, color: "var(--color-ink-2)", marginTop: 2 }}>{a.nextStep}</div>
                  </div>
                  <Button variant="primary" size="sm" onClick={(e) => { e.stopPropagation(); openApp(a); }}>Review offer</Button>
                </div>
              );
            })}
          </div>
        )}

        {/* Pipeline bar + inline stats */}
        <div className="dash-pipeline" style={{ display: "flex", alignItems: "center", gap: 24, marginBottom: 24, padding: "16px 20px",
          background: "var(--color-surface)", border: "1px solid var(--color-border)", borderRadius: 12 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--color-ink)", letterSpacing: "-0.006em" }}>Pipeline</span>
              <span style={{ fontSize: 11, color: "var(--color-ink-3)", fontFamily: "var(--font-mono)" }}>{pipeTotal} total</span>
            </div>
            <div style={{ display: "flex", height: 8, borderRadius: 4, overflow: "hidden", background: "var(--color-surface-2)", gap: 1 }}>
              {pipeline.filter(p => p.n > 0).map((p) => (
                <div key={p.status} title={`${p.label}: ${p.n}`}
                  style={{ flex: p.n, background: p.color, minWidth: 4, transition: "flex 300ms ease" }} />
              ))}
            </div>
            <div style={{ display: "flex", gap: 14, marginTop: 8, flexWrap: "wrap" }}>
              {pipeline.filter(p => p.n > 0).map((p) => (
                <div key={p.status} style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 11, color: "var(--color-ink-3)" }}>
                  <span style={{ width: 6, height: 6, borderRadius: "50%", background: p.color, flexShrink: 0 }} />
                  {p.label} <span className="mono" style={{ fontWeight: 600, color: "var(--color-ink-2)" }}>{p.n}</span>
                </div>
              ))}
            </div>
          </div>
          <div className="dash-pipe-divider" style={{ width: 1, height: 48, background: "var(--color-border)", flexShrink: 0 }} />
          <div style={{ display: "flex", gap: 20, flexShrink: 0 }}>
            {inlineStats.map((s) => (
              <div key={s.label} style={{ textAlign: "center" }}>
                <div style={{ fontSize: 20, fontWeight: 700, letterSpacing: "-0.022em", color: "var(--color-ink)", lineHeight: 1 }}>{s.value}</div>
                <div style={{ fontSize: 10, color: "var(--color-ink-3)", marginTop: 4, fontWeight: 500, textTransform: "uppercase", letterSpacing: "0.04em", whiteSpace: "nowrap" }}>{s.label}</div>
              </div>
            ))}
          </div>
        </div>

        {/* Stats row */}
        <div className="grid-4" style={{ marginBottom: 20 }}>
          <Stat label="Total applied" value={totalApplied} delta={monthlyNew > 0 ? `+${monthlyNew} this month` : undefined} deltaTone="up" />
          <Stat label="Active" value={activeCount} />
          <Stat label="Interviews" value={interviews} />
          <Stat label="Offers" value={offers} delta={respondBy ? `Respond by ${respondBy}` : undefined} />
        </div>

        {/* Main grid */}
        <div className="grid-2">

          {/* Upcoming */}
          <Card title="Coming up" sub={`${upcoming.length} scheduled`}
            action={<Button variant="ghost" size="sm" iconRight="chevron-right" onClick={() => goto("applications")}>View all</Button>}>
            {upcoming.length === 0 ? (
              <Empty title="Nothing scheduled" desc="You've got a quiet week." />
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 0 }}>
                {upcoming.map((a, i) => {
                  const j = DATA.byId(a.jobId); const c = DATA.coOf(j.co);
                  return (
                    <div key={a.id} onClick={() => openApp(a)} style={{
                      display: "grid", gridTemplateColumns: "32px 1fr auto",
                      gap: 12, alignItems: "center", padding: "10px 2px",
                      borderTop: i === 0 ? "none" : "1px solid var(--color-border)", cursor: "pointer",
                    }}>
                      <CoLogo co={j.co} />
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: 13, fontWeight: 500, color: "var(--color-ink)", letterSpacing: "-0.012em" }}>{j.title} · {c.name}</div>
                        <div style={{ fontSize: 12, color: "var(--color-ink-3)", marginTop: 2 }}>{a.nextStep}</div>
                      </div>
                      <StatusPill status={a.status} />
                    </div>
                  );
                })}
              </div>
            )}
          </Card>

          {/* Right column: Awaiting + Funnel */}
          <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <Card title="Awaiting reply" sub={`${awaiting.length} pending`}
              action={<Button variant="ghost" size="sm" iconRight="chevron-right" onClick={() => goto("applications")}>View all</Button>}>
              <div style={{ display: "flex", flexDirection: "column" }}>
                {awaiting.slice(0, 4).map((a, i) => {
                  const j = DATA.byId(a.jobId); const c = DATA.coOf(j.co);
                  const days = Math.round((Date.now() - new Date(a.appliedOn).getTime()) / 86400000);
                  return (
                    <div key={a.id} onClick={() => openApp(a)} style={{
                      display: "grid", gridTemplateColumns: "24px 1fr auto",
                      gap: 10, alignItems: "center", padding: "8px 2px",
                      borderTop: i === 0 ? "none" : "1px solid var(--color-border)", cursor: "pointer",
                    }}>
                      <CoLogo co={j.co} size="sm" />
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: 13, fontWeight: 500, color: "var(--color-ink)", letterSpacing: "-0.012em",
                          overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{c.name} · {j.title}</div>
                      </div>
                      <span className="mono" style={{ fontSize: 11, color: days > 10 ? "var(--color-warning)" : "var(--color-ink-3)", whiteSpace: "nowrap" }}>{days}d ago</span>
                    </div>
                  );
                })}
              </div>
            </Card>

            {/* Funnel visualization */}
            <Card title="Funnel" sub="conversion">
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                {funnelStages.map((s, i) => {
                  const pct = Math.round((s.n / funnelMax) * 100);
                  const convPct = i > 0 ? Math.round((s.n / Math.max(1, funnelStages[i - 1].n)) * 100) : 100;
                  return (
                    <div key={s.label}>
                      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 4 }}>
                        <span style={{ color: "var(--color-ink-2)", fontWeight: 500 }}>{s.label}</span>
                        <span className="mono" style={{ fontSize: 11, color: "var(--color-ink-3)" }}>
                          {s.n} {i > 0 && <span style={{ color: convPct >= 50 ? "var(--color-success)" : "var(--color-ink-4)" }}>({convPct}%)</span>}
                        </span>
                      </div>
                      <div style={{ height: 6, borderRadius: 3, background: "var(--color-surface-2)" }}>
                        <div style={{ height: "100%", borderRadius: 3, background: s.color, width: `${pct}%`, transition: "width 400ms ease", minWidth: s.n > 0 ? 4 : 0 }} />
                      </div>
                    </div>
                  );
                })}
              </div>
            </Card>

            {stale.length > 0 && (
              <Card title="Needs follow-up" sub={`${stale.length} stale`}>
                <div style={{ display: "flex", flexDirection: "column" }}>
                  {stale.map((a, i) => {
                    const j = DATA.byId(a.jobId); const c = DATA.coOf(j.co);
                    const days = Math.round((Date.now() - new Date(a.appliedOn).getTime()) / 86400000);
                    return (
                      <div key={a.id} onClick={() => openApp(a)} style={{
                        display: "grid", gridTemplateColumns: "24px 1fr auto",
                        gap: 10, alignItems: "center", padding: "8px 2px",
                        borderTop: i === 0 ? "none" : "1px solid var(--color-border)", cursor: "pointer",
                      }}>
                        <CoLogo co={j.co} size="sm" />
                        <div style={{ fontSize: 13, fontWeight: 500, color: "var(--color-ink)", letterSpacing: "-0.012em" }}>{c.name} · {j.title}</div>
                        <span className="mono" style={{ fontSize: 11, color: "var(--color-danger)", whiteSpace: "nowrap" }}>{days}d silent</span>
                      </div>
                    );
                  })}
                </div>
              </Card>
            )}
          </div>
        </div>
      </div>
    </>
  );
}

export { DashboardScreen };
