import React from "react";
import {
  DndContext, useDraggable, useDroppable, PointerSensor, TouchSensor, useSensor, useSensors,
  pointerWithin,
} from "@dnd-kit/core";
import Icon from "../components/Icon.jsx";
import DATA from "../data/mockData.js";
import * as UI from "../components/ui.jsx";
import { CustomReminderList } from "../components/CustomReminderList.jsx";
import { CustomReminderForm } from "../components/CustomReminderForm.jsx";
import { NotificationIdentity } from "../components/NotificationIdentity.jsx";
import { updateApplicationStatus } from "../api/applications.js";
import { statusToApi, statusToUi } from "../api/mappers.js";
import { ApiError } from "../api/client.js";
// JobHub — Applications screen (Kanban + List + Detail)
const { Button, Input, Field, Toggle, StatusPill, CoLogo, Avatar, Card, Stat, Modal, Empty, Tabs, STATUS_LABEL } = UI;

// Filter categories — one per kanban column. Each maps to the UI statuses it covers,
// so the chips, the Kanban columns and the List all stay in sync.
const CATEGORIES = [
  { id: "applied", label: "Applied", color: "applied", statuses: ["applied"] },
  { id: "screening", label: "Screening", color: "screening", statuses: ["screening"] },
  { id: "interview", label: "Interview", color: "interview", statuses: ["interview"] },
  { id: "offer", label: "Offer", color: "offer", statuses: ["offer"] },
  { id: "closed", label: "Closed", color: "ghosted", statuses: ["accepted", "rejected", "ghosted", "withdrawn"] },
];

const NOTES_MAX = 2000;

/* ─── Applications Screen ─── */
function ApplicationsScreen({ openApp, openSearch, onAddApp, onLogout }) {
  const STORAGE_KEY = "jobhub_app_view";
  const [view, setView] = React.useState(() => {
    try { return localStorage.getItem(STORAGE_KEY) || "kanban"; } catch { return "kanban"; }
  });
  // Multi-select category filter. Empty (or all selected) == "All" / no filtering.
  const [selected, setSelected] = React.useState(new Set());
  const [sortCol, setSortCol] = React.useState("appliedOn");
  const [sortDir, setSortDir] = React.useState("desc");
  // Bumped whenever the Kanban DnD path mutates an application's status in place
  // (optimistic update / rollback), so the memoized buckets below recompute even
  // though DATA.applications.length itself did not change.
  const [boardVersion, setBoardVersion] = React.useState(0);
  const bumpBoard = () => setBoardVersion((v) => v + 1);

  const changeView = (v) => { setView(v); try { localStorage.setItem(STORAGE_KEY, v); } catch {} };

  const isAll = selected.size === 0 || selected.size === CATEGORIES.length;

  const toggleCat = (id) => {
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      if (next.size === CATEGORIES.length) next.clear(); // every category selected → "All"
      return next;
    });
  };

  // Per-category counts (always from the full set, so chips show true totals).
  const counts = React.useMemo(() => {
    const c = { all: DATA.applications.length };
    CATEGORIES.forEach((cat) => { c[cat.id] = DATA.applications.filter((a) => cat.statuses.includes(a.status)).length; });
    return c;
  }, [DATA.applications.length, boardVersion]);

  // Apps visible under the current filter — drives BOTH the Kanban and the List.
  const visibleApps = React.useMemo(() => {
    if (isAll) return DATA.applications;
    const allowed = new Set(CATEGORIES.filter((c) => selected.has(c.id)).flatMap((c) => c.statuses));
    return DATA.applications.filter((a) => allowed.has(a.status));
  }, [selected, isAll, DATA.applications.length, boardVersion]);

  const inCat = (id) => (a) => CATEGORIES.find((c) => c.id === id).statuses.includes(a.status);
  const buckets = {
    applied:   visibleApps.filter(inCat("applied")),
    screening: visibleApps.filter(inCat("screening")),
    interview: visibleApps.filter(inCat("interview")),
    offer:     visibleApps.filter(inCat("offer")),
    closed:    visibleApps.filter(inCat("closed")),
  };

  const filtered = visibleApps;

  const sorted = React.useMemo(() => {
    const list = [...filtered];
    list.sort((a, b) => {
      let va, vb;
      const ja = DATA.byId(a.jobId), jb = DATA.byId(b.jobId);
      const ca = DATA.coOf(ja.co), cb = DATA.coOf(jb.co);
      switch (sortCol) {
        case "company": va = ca.name.toLowerCase(); vb = cb.name.toLowerCase(); break;
        case "role": va = ja.title.toLowerCase(); vb = jb.title.toLowerCase(); break;
        case "status": va = a.status; vb = b.status; break;
        case "appliedOn": va = a.appliedOn; vb = b.appliedOn; break;
        default: va = a.appliedOn; vb = b.appliedOn;
      }
      if (va < vb) return sortDir === "asc" ? -1 : 1;
      if (va > vb) return sortDir === "asc" ? 1 : -1;
      return 0;
    });
    return list;
  }, [filtered, sortCol, sortDir]);

  const toggleSort = (col) => {
    if (sortCol === col) setSortDir(d => d === "asc" ? "desc" : "asc");
    else { setSortCol(col); setSortDir("asc"); }
  };

  return (
    <>
      <UI.Topbar
        title="Applications"
        sub={`${DATA.applications.length} active · since Mar`}
        searchLabel="Search applications…"
        onSearchClick={openSearch}
        actions={<Button variant="primary" icon="plus" onClick={onAddApp}>Add application</Button>}
      />
      <div className="content">
        <div style={{ display: "flex", alignItems: "center", marginBottom: 14, gap: 14 }}>
          <Tabs value={view} onChange={changeView}
            tabs={[{ id: "kanban", label: "Kanban" }, { id: "list", label: "List" }]} />
          <div style={{ flex: 1 }} />
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
            <span className={"chip " + (isAll ? "active" : "")} onClick={() => setSelected(new Set())}>
              All <span className="mono" style={{ fontSize: 10, opacity: 0.65 }}>{counts.all}</span>
            </span>
            {CATEGORIES.map((c) => (
              <span key={c.id} className={"chip " + (!isAll && selected.has(c.id) ? "active" : "")} onClick={() => toggleCat(c.id)}>
                {c.label} <span className="mono" style={{ fontSize: 10, opacity: 0.65 }}>{counts[c.id]}</span>
              </span>
            ))}
          </div>
        </div>

        {view === "kanban" ? (
          <KanbanBoard
            categories={CATEGORIES.filter((c) => isAll || selected.has(c.id))}
            buckets={buckets}
            openApp={openApp}
            onLogout={onLogout}
            onBoardChange={bumpBoard}
          />
        ) : (
          <SortableListView apps={sorted} openApp={openApp} sortCol={sortCol} sortDir={sortDir} onSort={toggleSort} />
        )}
      </div>
    </>
  );
}

/* ─── Kanban Drag-and-Drop board (story #152) ───
   Drop-target ids: a column's own id ("applied" | "screening" | "interview" | "offer" | "closed")
   or, only while a drag is hovering the Closed column, one of the 3 fanned-out sub-zone ids
   ("closed:rejected" | "closed:ghosted" | "closed:withdrawn"). The Closed column never renders
   the sub-zones outside of an active drag-over (3.2 in the spec): the fan-out is purely a
   render-time decision driven by drag state, not a DOM restructure of the column itself.
   `accepted` has no drop target here at all (AC-10) — StatusPicker remains the only path. */
function KanbanBoard({ categories, buckets, openApp, onLogout, onBoardChange }) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 150, tolerance: 8 } })
  );

  const [activeApp, setActiveApp] = React.useState(null);
  const [overId, setOverId] = React.useState(null);
  const [pendingMove, setPendingMove] = React.useState(null); // { app, fromStatus, targetUiStatus }
  const [errorMsg, setErrorMsg] = React.useState(null);

  const visibleColumnIds = new Set(categories.map((c) => c.id));
  const closedVisible = visibleColumnIds.has("closed");
  const closedFannedOut = closedVisible && (overId === "closed" || (typeof overId === "string" && overId.startsWith("closed:")));

  const resetDragUi = () => { setActiveApp(null); setOverId(null); };

  function resolveTargetUiStatus(dropId) {
    if (dropId === "closed:rejected") return "rejected";
    if (dropId === "closed:ghosted") return "ghosted";
    if (dropId === "closed:withdrawn") return "withdrawn";
    if (dropId === "closed") return null; // dropped on Closed's general area — rejected, not a status
    return dropId; // applied | screening | interview | offer
  }

  function isBackwardMove(fromUiStatus, toUiStatus) {
    const fromIsExit = STATUS_EXITS.some((s) => s.key === fromUiStatus);
    const toIsExit = STATUS_EXITS.some((s) => s.key === toUiStatus);
    if (fromIsExit) return true; // re-opening from Closed (pipeline or another exit) is always backward
    if (toIsExit) return false; // pipeline -> exit is a forward exit, never backward
    const fromIdx = FLOW_INDEX[fromUiStatus] ?? -1;
    const toIdx = FLOW_INDEX[toUiStatus] ?? -1;
    return fromIdx >= 0 && toIdx >= 0 && toIdx < fromIdx;
  }

  async function applyStatusChange(app, targetUiStatus) {
    const previousStatus = app.status;
    app.status = targetUiStatus; // optimistic
    onBoardChange();
    if (!app.apiId) return; // mock-mode entry: no backend to sync, optimistic value stands
    try {
      await updateApplicationStatus(app.apiId, statusToApi(targetUiStatus));
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        onLogout?.();
        return;
      }
      app.status = previousStatus; // rollback
      onBoardChange();
      setErrorMsg("Couldn't update status. Try again.");
    }
  }

  function handleDragStart(event) {
    const app = DATA.applications.find((a) => a.id === event.active.id);
    setActiveApp(app || null);
    setErrorMsg(null);
  }

  function handleDragOver(event) {
    setOverId(event.over ? event.over.id : null);
  }

  function handleDragCancel() {
    resetDragUi();
  }

  function handleDragEnd(event) {
    const app = activeApp;
    const dropId = event.over ? event.over.id : null;
    resetDragUi();
    if (!app || !dropId) return; // cancelled: released outside any drop target

    const targetUiStatus = resolveTargetUiStatus(dropId);
    if (!targetUiStatus) return; // Closed general area (no sub-zone resolved) — rejected drop
    if (targetUiStatus === app.status) return; // same-status no-op (incl. same column / same position)

    if (isBackwardMove(app.status, targetUiStatus)) {
      setPendingMove({ app, fromStatus: app.status, targetUiStatus });
      return;
    }
    applyStatusChange(app, targetUiStatus);
  }

  function confirmPendingMove() {
    if (!pendingMove) return;
    applyStatusChange(pendingMove.app, pendingMove.targetUiStatus);
    setPendingMove(null);
  }

  function cancelPendingMove() {
    setPendingMove(null);
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={pointerWithin}
      onDragStart={handleDragStart}
      onDragOver={handleDragOver}
      onDragCancel={handleDragCancel}
      onDragEnd={handleDragEnd}
    >
      <div className="kanban">
        {categories.map((c) => (
          <DroppableColumn
            key={c.id}
            id={c.id}
            name={c.label}
            color={c.color}
            apps={buckets[c.id]}
            openApp={openApp}
            isClosedColumn={c.id === "closed"}
            fannedOut={c.id === "closed" && closedFannedOut}
            overId={overId}
            activeApp={activeApp}
            allCategoryIds={categories.map((cat) => cat.id)}
            onKeyboardMove={(app, targetId) => {
              const targetUiStatus = resolveTargetUiStatus(targetId);
              if (!targetUiStatus || targetUiStatus === app.status) return;
              if (isBackwardMove(app.status, targetUiStatus)) {
                setPendingMove({ app, fromStatus: app.status, targetUiStatus });
              } else {
                applyStatusChange(app, targetUiStatus);
              }
            }}
          />
        ))}
      </div>

      {pendingMove && (
        <div role="alertdialog" aria-modal="true" aria-label="Confirm status change"
          style={{ position: "fixed", bottom: 24, left: "50%", transform: "translateX(-50%)", zIndex: 1000,
            background: "var(--color-surface)", border: "1px solid var(--color-warning-border)",
            borderRadius: 10, boxShadow: "var(--shadow-md)", padding: 14, minWidth: 320,
            display: "flex", flexDirection: "column", gap: 10 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <Icon name="info" size={14} style={{ color: "var(--color-warning)", flexShrink: 0 }} />
            <div style={{ fontSize: 13, fontWeight: 500, color: "var(--color-ink)" }}>
              Move back to {STATUS_LABEL[pendingMove.targetUiStatus]}?
            </div>
          </div>
          <div style={{ fontSize: 12, color: "var(--color-ink-2)", lineHeight: 1.5 }}>
            This will move the application from {STATUS_LABEL[pendingMove.fromStatus]} back to{" "}
            {STATUS_LABEL[pendingMove.targetUiStatus]}. Progress after this stage may be lost.
          </div>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <Button variant="ghost" size="sm" onClick={cancelPendingMove}>Cancel</Button>
            <Button variant="primary" size="sm" onClick={confirmPendingMove}>Confirm</Button>
          </div>
        </div>
      )}

      {errorMsg && (
        <div role="alert" style={{ position: "fixed", bottom: 24, right: 24, zIndex: 1000,
          background: "var(--color-danger-bg)", border: "1px solid var(--color-danger-border)",
          color: "var(--color-danger)", borderRadius: 8, padding: "10px 14px", fontSize: 12,
          display: "flex", alignItems: "center", gap: 8 }}>
          <Icon name="alert-circle" size={14} />
          {errorMsg}
        </div>
      )}
    </DndContext>
  );
}

// Targets a card can move to via the keyboard move-mode scheme: the visible pipeline columns,
// in order, followed by the 3 Closed sub-zones (AC-19, 3.6). Accepted is never included (AC-10).
function keyboardTargets(allCategoryIds) {
  const targets = [];
  ["applied", "screening", "interview", "offer"].forEach((id) => {
    if (allCategoryIds.includes(id)) targets.push({ id, label: CATEGORIES.find((c) => c.id === id).label });
  });
  if (allCategoryIds.includes("closed")) {
    targets.push({ id: "closed:rejected", label: "Rejected" });
    targets.push({ id: "closed:ghosted", label: "Ghosted" });
    targets.push({ id: "closed:withdrawn", label: "Withdrawn" });
  }
  return targets;
}

function DroppableColumn({ id, name, color, apps, openApp, isClosedColumn, fannedOut, overId, activeApp, allCategoryIds, onKeyboardMove }) {
  const { setNodeRef, isOver } = useDroppable({ id });

  return (
    <div ref={setNodeRef} className="kanban-col" data-over={isOver || undefined} data-rect-zone={id}>
      <div className="kanban-head">
        <span className={"status " + color} style={{ padding: 0, border: 0, background: "transparent" }}><span className="dot" /></span>
        <span className="name">{name}</span>
        <span className="count">{apps.length}</span>
      </div>

      {isClosedColumn && fannedOut ? (
        <div className="kanban-fanout" style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {STATUS_EXITS.map((exit) => (
            <ClosedSubZone key={exit.key} exit={exit} overId={overId} />
          ))}
        </div>
      ) : (
        <>
          {apps.map((a) => (
            <DraggableCard key={a.id} app={a} openApp={openApp} allCategoryIds={allCategoryIds} onKeyboardMove={onKeyboardMove} />
          ))}
          {apps.length === 0 && (
            <div style={{ padding: 20, textAlign: "center", color: "var(--color-ink-4)", fontSize: 12, border: "1px dashed var(--color-border-2)", borderRadius: 8 }}>
              Nothing here.
            </div>
          )}
        </>
      )}
    </div>
  );
}

function ClosedSubZone({ exit, overId }) {
  const dropId = "closed:" + exit.key;
  const { setNodeRef, isOver } = useDroppable({ id: dropId });
  const active = isOver || overId === dropId;
  return (
    <button
      ref={setNodeRef}
      type="button"
      data-active={active ? "true" : "false"}
      data-rect-zone={dropId}
      aria-label={exit.label}
      style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 12px", borderRadius: 8,
        border: "1px solid " + (active ? "var(--color-brand-400)" : "var(--color-border-2)"),
        background: active ? "var(--color-brand-50)" : "var(--color-surface)",
        cursor: "default", textAlign: "left", width: "100%" }}>
      <Icon name={exit.icon} size={14} />
      <span style={{ fontSize: 12, fontWeight: 500, color: "var(--color-ink)" }}>{exit.label}</span>
    </button>
  );
}

// Card pickup/move via keyboard (AC-19, AC-20): Enter/Space enters move mode, Left/Right (or
// Up/Down) cycles the candidate target with a live aria-live announcement, Enter/Space confirms,
// Escape cancels. This keyboard path is independent of @dnd-kit's KeyboardSensor — it drives the
// exact same applyStatusChange/backward-confirm logic as a pointer drop via onKeyboardMove.
function DraggableCard({ app, openApp, allCategoryIds, onKeyboardMove }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: app.id });
  const [moveMode, setMoveMode] = React.useState(false);
  const [targetIdx, setTargetIdx] = React.useState(0);
  const targets = React.useMemo(() => keyboardTargets(allCategoryIds), [allCategoryIds]);

  const j = DATA.byId(app.jobId);
  const c = DATA.coOf(j.co);

  const style = transform
    ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`, opacity: isDragging ? 0.5 : 1, zIndex: isDragging ? 50 : "auto" }
    : undefined;

  const enterMoveMode = () => { setMoveMode(true); setTargetIdx(0); };
  const exitMoveMode = () => setMoveMode(false);

  const onKeyDown = (e) => {
    if (!moveMode) {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); enterMoveMode(); }
      return;
    }
    if (e.key === "Escape") { e.preventDefault(); exitMoveMode(); return; }
    if (e.key === "ArrowRight" || e.key === "ArrowDown") {
      e.preventDefault();
      setTargetIdx((i) => Math.min(i + 1, targets.length - 1));
      return;
    }
    if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
      e.preventDefault();
      setTargetIdx((i) => Math.max(i - 1, 0));
      return;
    }
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      const target = targets[targetIdx];
      exitMoveMode();
      if (target) onKeyboardMove(app, target.id);
    }
  };

  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      className="kanban-card"
      style={style}
      tabIndex={0}
      role="button"
      aria-pressed={moveMode}
      aria-label={c.name + " — " + j.title}
      onKeyDown={onKeyDown}
      onClick={() => { if (!moveMode) openApp(app); }}
    >
      <div className="co"><CoLogo co={j.co} size="sm" /><span style={{ color: "var(--color-ink-2)", fontWeight: 500 }}>{c.name}</span></div>
      <div className="role">{j.title}</div>
      <div className="foot">
        <span className="when">Applied {app.appliedOn.slice(5)}</span>
        {app.nextStep && app.nextStep !== "—" && (
          <span style={{ fontSize: 10, color: "var(--color-brand-700)", fontWeight: 500 }}>
            <Icon name="calendar" size={10} /> {app.nextStep.split("·").slice(-1)[0].trim()}
          </span>
        )}
      </div>
      {moveMode && targets[targetIdx] && (
        <div role="status" aria-live="polite" style={{ fontSize: 11, color: "var(--color-brand-700)", fontWeight: 500, marginTop: 4 }}>
          Move to {targets[targetIdx].label}? Use arrow keys, Enter to confirm, Escape to cancel.
        </div>
      )}
    </div>
  );
}

function SortableListView({ apps, openApp, sortCol, sortDir, onSort }) {
  const SortHead = ({ col, children, style }) => {
    const active = sortCol === col;
    return (
      <div onClick={() => onSort(col)} style={{ cursor: "pointer", userSelect: "none", display: "flex", alignItems: "center", gap: 4, ...style }}>
        {children}
        {active && <span style={{ fontSize: 10, lineHeight: 1, color: "var(--color-brand-600)" }}>{sortDir === "asc" ? "↑" : "↓"}</span>}
      </div>
    );
  };
  return (
    <div className="tbl">
      <div className="tbl-row head" style={{ gridTemplateColumns: "32px 1.4fr 1fr 110px 84px 110px 24px" }}>
        <div></div>
        <SortHead col="company">Company · Role</SortHead>
        <SortHead col="source">Source</SortHead>
        <SortHead col="status">Status</SortHead>
        <SortHead col="appliedOn">Applied</SortHead>
        <div>Next step</div>
        <div></div>
      </div>
      {apps.map((a) => {
        const j = DATA.byId(a.jobId); const c = DATA.coOf(j.co);
        return (
          <div className="tbl-row" key={a.id} style={{ gridTemplateColumns: "32px 1.4fr 1fr 110px 84px 110px 24px" }} onClick={() => openApp(a)}>
            <CoLogo co={j.co} size="sm" />
            <div>
              <div style={{ fontWeight: 500, color: "var(--color-ink)", letterSpacing: "-0.012em" }}>{c.name} — {j.title}</div>
              <div style={{ fontSize: 12, color: "var(--color-ink-3)", marginTop: 1 }}>{j.location} · {j.comp}</div>
            </div>
            <div style={{ color: "var(--color-ink-3)", fontSize: 12 }}>{j.source}</div>
            <div><StatusPill status={a.status} /></div>
            <div className="mono" style={{ fontSize: 12, color: "var(--color-ink-3)" }}>{a.appliedOn.slice(5)}</div>
            <div className="mono" style={{ fontSize: 12, color: a.nextStep && a.nextStep !== "—" ? "var(--color-ink-2)" : "var(--color-ink-4)" }}>
              {a.nextStep || "—"}
            </div>
            <Icon name="chevron-right" size={14} style={{ color: "var(--color-ink-4)" }} />
          </div>
        );
      })}
    </div>
  );
}

/* ─── Application Detail Screen ─── */
function ApplicationDetailScreen({ app, goto, onBack, openSearch, onDelete, onStatusChange, onNotesSave, onEditSave, onLogout }) {
  const j = DATA.byId(app.jobId); const c = DATA.coOf(j.co);
  const [notes, setNotes] = React.useState(app.notes);
  const [notesEditing, setNotesEditing] = React.useState(false);
  const [confirmDelete, setConfirmDelete] = React.useState(false);
  const [editMode, setEditMode] = React.useState(false);
  const [timelineVersion, setTimelineVersion] = React.useState(0);
  const [statusVersion, setStatusVersion] = React.useState(0);

  const handleStatusChange = (newStatus) => {
    // Force re-render of timeline and status pill
    setTimelineVersion((v) => v + 1);
    setStatusVersion((v) => v + 1);
    onStatusChange?.(app, newStatus); // persist to the backend
  };

  const saveNotes = () => {
    app.notes = notes;
    setNotesEditing(false);
    onNotesSave?.(app, notes); // persist to the backend
  };

  return (
    <>
      <UI.Topbar
        title={c.name} sub={j.title}
        searchLabel="Search applications…"
        onSearchClick={openSearch}
        actions={
          <>
            <Button variant="ghost" icon="arrow-left" onClick={onBack}>Applications</Button>
            <Button variant="secondary" icon="notebook-pen" onClick={() => setEditMode(true)}>Edit details</Button>
          </>
        }
      />
      <div className="content">
        <div style={{ marginBottom: 20, display: "flex", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
          <CoLogo co={j.co} size="lg" />
          <div style={{ minWidth: 0 }}>
            <h2 style={{ fontSize: 24, fontWeight: 600 }}>{j.title}</h2>
            <div style={{ color: "var(--color-ink-3)", fontSize: 13, marginTop: 4, display: "flex", gap: 10, alignItems: "center" }}>
              <span style={{ color: "var(--color-ink-2)", fontWeight: 500 }}>{c.name}</span>
              <span className="dot-sep" /><span>{j.location}</span>
              <span className="dot-sep" /><span className="mono">{j.comp}</span>
              <span className="dot-sep" /><span>{j.type}</span>
            </div>
          </div>
          <div style={{ flex: 1 }} />
          <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 8 }}>
            <StatusPill key={statusVersion} status={app.status} />
            <span className="mono" style={{ fontSize: 11, color: "var(--color-ink-3)" }}>Applied {app.appliedOn}</span>
          </div>
        </div>

        <div className="detail">
          <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            {app.nextStep && app.nextStep !== "—" && (
              <Card>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <div style={{ width: 36, height: 36, borderRadius: 8, background: "var(--color-brand-50)", color: "var(--color-brand-700)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                    <Icon name="calendar" size={18} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 12, color: "var(--color-ink-3)", fontWeight: 500 }}>Next step</div>
                    <div style={{ fontSize: 14, fontWeight: 600, color: "var(--color-ink)", letterSpacing: "-0.012em", marginTop: 2 }}>{app.nextStep}</div>
                  </div>
                  <Button variant="primary" size="sm">Mark done</Button>
                </div>
              </Card>
            )}
            <Card title="Notes" sub="visible only to you"
              action={notesEditing
                ? <Button variant="primary" size="sm" onClick={saveNotes}>Save</Button>
                : <Button variant="ghost" size="sm" icon="notebook-pen" onClick={() => setNotesEditing(true)}>Edit</Button>
              }>
              {notesEditing ? (
                <>
                  <textarea className="input" value={notes}
                    onChange={(e) => setNotes(e.target.value.slice(0, NOTES_MAX))}
                    maxLength={NOTES_MAX} rows={3}
                    style={{ resize: "vertical", width: "100%", overflowWrap: "anywhere", wordBreak: "break-word" }}
                    placeholder="Add notes about this application…" autoFocus />
                  <div className="mono" style={{ marginTop: 6, textAlign: "right", fontSize: 11,
                    color: notes.length >= NOTES_MAX ? "var(--color-danger)" : "var(--color-ink-4)" }}>
                    {notes.length} / {NOTES_MAX}
                  </div>
                </>
              ) : (
                <div style={{ fontSize: 13, color: notes ? "var(--color-ink-2)" : "var(--color-ink-4)", lineHeight: 1.6,
                  whiteSpace: "pre-wrap", overflowWrap: "anywhere", wordBreak: "break-word", minHeight: 40 }}>
                  {notes || "No notes yet. Click Edit to add some."}
                </div>
              )}
            </Card>
            <Card title="Timeline" sub={`${app.timeline.length} events`} key={"tl-" + timelineVersion}>
              <div className="timeline">
                {app.timeline.map((t, i) => (
                  <div className="timeline-item" key={i}>
                    <div className={"timeline-dot " + (i === 0 ? "done" : "")}><Icon name={i === 0 ? "check" : "calendar"} size={12} /></div>
                    <div>
                      <div className="what">{t.what}</div>
                      <div className="when">{t.date}</div>
                      {t.note && <div className="note">{t.note}</div>}
                    </div>
                  </div>
                ))}
              </div>
            </Card>
            <RemindersCard app={app} job={j} company={c} onLogout={onLogout} />
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <StatusPicker currentStatus={app.status} lastUpdate={app.lastUpdate} app={app}
              onChangeStatus={handleStatusChange} />
            <Card title="Links">
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {app.postUrl && <LinkRow icon="briefcase" label="Job post" value={app.postUrl} href={app.postUrl} />}
                {app.portalUrl && <LinkRow icon="building" label="Career portal" value={app.portalUrl} href={app.portalUrl} />}
                {app.contact && <LinkRow icon="user" label="Contact" value={app.contact} />}
                {!app.postUrl && !app.portalUrl && !app.contact && (
                  <span style={{ fontSize: 12, color: "var(--color-ink-3)" }}>No links yet — add a job post or portal URL.</span>
                )}
              </div>
            </Card>
            <Card title={c.name || "Company"} className="company-card">
              <div style={{ display: "flex", flexDirection: "column", gap: 10, fontSize: 13 }}>
                <KV k="Industry" v={c.industry} />
                <KV k="Size" v={c.size} />
                <KV k="HQ" v={c.hq} />
                {/* Website (story #486): reuses the Links card's LinkRow so the two
                    external-link surfaces behave identically. `tags` stays out of this
                    card per story #427's decision (regression-locked). */}
                {c.website && <LinkRow icon="globe" label="Website" value={c.website} href={c.website} />}
              </div>
            </Card>
            {!confirmDelete ? (
              <Button variant="ghost" icon="trash" style={{ color: "var(--color-danger)" }}
                onClick={() => setConfirmDelete(true)}>Delete application</Button>
            ) : (
              <div style={{ padding: 14, border: "1px solid var(--color-danger-border)", borderRadius: 8, background: "var(--color-danger-bg)", display: "flex", flexDirection: "column", gap: 8 }}>
                <div style={{ fontSize: 13, fontWeight: 500, color: "var(--color-danger)" }}>Delete this application?</div>
                <div style={{ fontSize: 12, color: "var(--color-ink-3)" }}>This cannot be undone.</div>
                <div style={{ display: "flex", gap: 8 }}>
                  <Button variant="ghost" size="sm" onClick={() => setConfirmDelete(false)}>Cancel</Button>
                  <Button variant="danger" size="sm" icon="trash" onClick={() => onDelete(app)}>Delete</Button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Edit details modal */}
      {editMode && (
        <EditApplicationModal app={app} job={j} company={c}
          onClose={() => setEditMode(false)}
          onSave={(data) => {
            j.title = data.title; j.location = data.location; j.comp = data.comp;
            app.postUrl = data.postUrl; app.portalUrl = data.portalUrl;
            app.contact = data.contact; app.appliedOn = data.appliedOn;
            app.lastUpdate = new Date().toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
            setEditMode(false);
            onEditSave?.(app, data); // persist to the backend
          }} />
      )}
    </>
  );
}

/* ─── Reminders Card (story #163 — mounts the existing CustomReminderList/Form) ─── */
// Mock-mode / no-apiId guard (AC-11, PDA spec §3): reminders are inherently tied to a real
// backend application id, so when app.apiId is absent (manual/mock entry, or USE_API off)
// this renders an inactive state and never imports/calls the custom-reminders API at all.
function RemindersCard({ app, job, company, onLogout }) {
  const [formOpen, setFormOpen] = React.useState(false);
  const [editingReminder, setEditingReminder] = React.useState(null);
  const [listVersion, setListVersion] = React.useState(0);
  const footerRef = React.useRef(null);
  const [footerMounted, setFooterMounted] = React.useState(false);

  const apiId = app?.apiId;
  // Req-4 (story #211, AC-211-4.x): the add/edit-reminder modal mirrors story #207's card
  // identity (company icon + job title). NotificationIdentity already degrades gracefully
  // (generic fallback icon + label) when either half of the identity is missing/blank.
  const reminderIdentity = { company: company?.name, jobTitle: job?.title };

  const openAdd = () => { setEditingReminder(null); setFormOpen(true); };
  const openEdit = (reminder) => { setEditingReminder(reminder); setFormOpen(true); };
  const closeForm = () => { setFormOpen(false); setEditingReminder(null); };
  const handleSuccess = () => {
    setFormOpen(false);
    setEditingReminder(null);
    setListVersion((v) => v + 1); // force CustomReminderList to refetch
  };

  if (!apiId) {
    return (
      <Card title="Reminders">
        <div data-testid="reminders-inactive" style={{ fontSize: 13, color: "var(--color-ink-3)" }}>
          Reminders are available once this application is synced.
        </div>
      </Card>
    );
  }

  return (
    <>
      <Card title="Reminders"
        action={<Button variant="ghost" size="sm" icon="plus" onClick={openAdd}>Add reminder</Button>}>
        <CustomReminderList
          key={listVersion}
          applicationId={apiId}
          onLogout={onLogout}
          onAddReminder={openAdd}
          onEditReminder={openEdit}
        />
      </Card>

      {formOpen && (
        <UI.Modal
          title={<NotificationIdentity notification={reminderIdentity} />}
          onClose={closeForm}
          footer={
            <div
              ref={(node) => { footerRef.current = node; if (node && !footerMounted) setFooterMounted(true); }}
              style={{ display: "flex", gap: 8, justifyContent: "flex-end", width: "100%" }}
            />
          }
        >
          <CustomReminderForm
            applicationId={apiId}
            reminder={editingReminder}
            onSuccess={handleSuccess}
            onCancel={closeForm}
            footerRef={footerRef}
          />
        </UI.Modal>
      )}
    </>
  );
}

/* ─── Edit Application Modal ─── */
function EditApplicationModal({ app, job, company, onClose, onSave }) {
  const [title, setTitle] = React.useState(job.title);
  const [location, setLocation] = React.useState(job.location);
  const [comp, setComp] = React.useState(job.comp);
  const [postUrl, setPostUrl] = React.useState(app.postUrl || "");
  const [portalUrl, setPortalUrl] = React.useState(app.portalUrl || "");
  const [contact, setContact] = React.useState(app.contact || "");
  const [appliedOn, setAppliedOn] = React.useState(app.appliedOn || "");

  const handleSave = () => {
    onSave({ title: title.trim(), location: location.trim(), comp: comp.trim(),
      postUrl: postUrl.trim(), portalUrl: portalUrl.trim(), contact: contact.trim(),
      appliedOn: appliedOn });
  };

  return (
    <UI.Modal title={"Edit — " + company.name} onClose={onClose} wide
      footer={
        <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", width: "100%" }}>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant="primary" onClick={handleSave}>Save changes</Button>
        </div>
      }>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <Field label="Job title">
            <Input value={title} onChange={(e) => setTitle(e.target.value)} />
          </Field>
          <Field label="Company">
            <Input value={company.name} disabled style={{ opacity: 0.6 }} />
          </Field>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <Field label="Location">
            <Input value={location} onChange={(e) => setLocation(e.target.value)} />
          </Field>
          <Field label="Compensation">
            <Input value={comp} onChange={(e) => setComp(e.target.value)} />
          </Field>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
          <Field label="Date applied">
            <Input type="date" value={appliedOn} onChange={(e) => setAppliedOn(e.target.value)} />
          </Field>
          <Field label="Contact">
            <Input placeholder="e.g. Priya M. · Recruiter" value={contact} onChange={(e) => setContact(e.target.value)} />
          </Field>
        </div>
        <Field label="Job post URL">
          <Input type="url" placeholder="https://…" leading="link" value={postUrl} onChange={(e) => setPostUrl(e.target.value)} />
        </Field>
        <Field label="Career portal URL">
          <Input type="url" placeholder="https://…" leading="building" value={portalUrl} onChange={(e) => setPortalUrl(e.target.value)} />
        </Field>
      </div>
    </UI.Modal>
  );
}

function LinkRow({ icon, label, value, href }) {
  const url = href ? normalizeUrl(href) : null;
  const body = (
    <>
      <Icon name={icon} size={14} style={{ color: "var(--color-ink-3)" }} />
      <div style={{ display: "flex", flexDirection: "column", minWidth: 0, flex: 1 }}>
        <span style={{ fontSize: 11, color: "var(--color-ink-3)", fontWeight: 500 }}>{label}</span>
        <span style={{ fontSize: 12, color: "var(--color-ink-2)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{value}</span>
      </div>
      {url && <Icon name="external-link" size={12} style={{ color: "var(--color-ink-4)" }} />}
    </>
  );
  const baseStyle = { display: "flex", alignItems: "center", gap: 10, padding: "6px 8px", borderRadius: 6 };
  if (url) {
    return (
      <a href={url} target="_blank" rel="noopener noreferrer"
        style={{ ...baseStyle, cursor: "pointer", textDecoration: "none", color: "inherit" }}>
        {body}
      </a>
    );
  }
  return <div style={baseStyle}>{body}</div>;
}

// Ensure a value entered as "careers.example.com/x" still opens as an absolute URL.
function normalizeUrl(raw) {
  const s = String(raw || "").trim();
  if (!s) return null;
  return /^[a-z][a-z0-9+.-]*:\/\//i.test(s) ? s : "https://" + s;
}

function KV({ k, v }) {
  if (v === null || v === undefined || String(v).trim() === "") return null;
  return (
    <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
      <span style={{ color: "var(--color-ink-3)" }}>{k}</span>
      <span style={{ color: "var(--color-ink)", fontWeight: 500, textAlign: "right" }}>{v}</span>
    </div>
  );
}

/* ─── Status Picker ─── */
const STATUS_FLOW = [
  { key: "applied", label: "Applied", icon: "send", desc: "Application submitted", idx: 0 },
  { key: "screening", label: "Screening", icon: "eye", desc: "Recruiter reviewing", idx: 1 },
  { key: "interview", label: "Interview", icon: "phone-call", desc: "Interview rounds", idx: 2 },
  { key: "offer", label: "Offer", icon: "party-popper", desc: "Offer received", idx: 3 },
  { key: "accepted", label: "Accepted", icon: "check", desc: "Offer accepted", idx: 4 },
];
const STATUS_EXITS = [
  { key: "rejected", label: "Rejected", icon: "x", desc: "Not moving forward" },
  { key: "ghosted", label: "Ghosted", icon: "ghost", desc: "No response" },
  { key: "withdrawn", label: "Withdrawn", icon: "arrow-left", desc: "You withdrew" },
];

const FLOW_INDEX = {};
STATUS_FLOW.forEach((s) => { FLOW_INDEX[s.key] = s.idx; });

function StatusPicker({ currentStatus, lastUpdate, onChangeStatus, app }) {
  const [open, setOpen] = React.useState(false);
  const [pending, setPending] = React.useState(null);
  const [localStatus, setLocalStatus] = React.useState(currentStatus);
  const [showBackwardWarning, setShowBackwardWarning] = React.useState(false);
  const wrapRef = React.useRef(null);

  React.useEffect(() => {
    if (!open) return;
    const h = (e) => { if (wrapRef.current && !wrapRef.current.contains(e.target)) { setOpen(false); setPending(null); setShowBackwardWarning(false); } };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, [open]);

  const currentFlowIdx = FLOW_INDEX[localStatus] ?? -1;
  const isExit = STATUS_EXITS.some((s) => s.key === localStatus);

  const selectStatus = (key) => {
    if (key === localStatus) return;
    const targetFlowIdx = FLOW_INDEX[key] ?? -1;
    const isBackward = !isExit && currentFlowIdx >= 0 && targetFlowIdx >= 0 && targetFlowIdx < currentFlowIdx;
    setPending(key);
    setShowBackwardWarning(isBackward);
  };

  const confirmChange = () => {
    if (!pending) return;
    const now = new Date();
    const today = now.toISOString().slice(0, 10);
    const todayFull = now.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
    const label = STATUS_LABEL[pending] || pending;
    const pendingFlowIdx = FLOW_INDEX[pending] ?? -1;
    const isBackward = showBackwardWarning;

    if (app && app.timeline) {
      if (isBackward) {
        // Remove timeline entries for statuses being lost (those after the target)
        app.timeline = app.timeline.filter((entry) => {
          // Match both "Status changed to X" and "Status reverted to X"
          const match = entry.what.match(/^Status (?:changed|reverted) to (.+)$/);
          if (!match) return true;
          const entryStatus = Object.entries(STATUS_LABEL).find(([k, v]) => v === match[1]);
          if (!entryStatus) return true;
          const entryIdx = FLOW_INDEX[entryStatus[0]] ?? -1;
          // Remove if it's a pipeline status after our target
          if (entryIdx >= 0 && entryIdx > pendingFlowIdx) return false;
          // Also remove exit status entries (rejected/ghosted/withdrawn) when reverting to pipeline
          if (entryIdx < 0 && STATUS_EXITS.some((s) => s.key === entryStatus[0])) return false;
          return true;
        });
        app.timeline.unshift({ date: today, what: "Status reverted to " + label });
      } else {
        app.timeline.unshift({ date: today, what: "Status changed to " + label });
      }
    }
    if (app) { app.status = pending; app.lastUpdate = todayFull; }
    setLocalStatus(pending);
    onChangeStatus(pending);
    setPending(null);
    setShowBackwardWarning(false);
    setOpen(false);
  };

  return (
    <div className="card" ref={wrapRef} style={{ position: "relative" }}>
      <div className="card-header">
        <span className="title">Status</span>
      </div>
      <div className="card-pad">
        <div onClick={() => { setOpen(!open); setPending(null); setShowBackwardWarning(false); }}
          style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 12px",
          borderRadius: 8, border: "1px solid var(--color-border)", cursor: "pointer",
          background: open ? "var(--color-surface-2)" : "var(--color-surface)",
          transition: "background 120ms" }}>
          <StatusPill status={localStatus} />
          <div style={{ flex: 1 }} />
          <Icon name="chevron-down" size={14} style={{ color: "var(--color-ink-3)", transform: open ? "rotate(180deg)" : "none", transition: "transform 150ms" }} />
        </div>

        <div style={{ fontSize: 12, color: "var(--color-ink-3)", lineHeight: 1.5, marginTop: 8 }}>
          Last updated <span className="mono" style={{ color: "var(--color-ink-2)" }}>{lastUpdate}</span>.
        </div>

        {open && (
          <div style={{ marginTop: 10, border: "1px solid var(--color-border)", borderRadius: 10,
            background: "var(--color-surface)", boxShadow: "var(--shadow-md)", overflow: "hidden",
            animation: "fade-in 120ms ease" }}>

            <div style={{ padding: "12px 14px 8px" }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: "var(--color-ink-3)", textTransform: "uppercase",
                letterSpacing: "0.06em", marginBottom: 8 }}>Pipeline</div>
              <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                {STATUS_FLOW.map((s) => {
                  const isSelected = s.key === (pending || localStatus);
                  const isPast = !isExit && currentFlowIdx >= 0 && s.idx <= currentFlowIdx && !pending;
                  return (
                    <div key={s.key} onClick={() => selectStatus(s.key)}
                      style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 10px",
                        borderRadius: 6, cursor: "pointer",
                        background: isSelected ? "var(--color-brand-50)" : "transparent",
                        border: isSelected ? "1px solid var(--color-brand-200)" : "1px solid transparent",
                        transition: "background 80ms" }}
                      onMouseEnter={(e) => { if (!isSelected) e.currentTarget.style.background = "var(--color-surface-2)"; }}
                      onMouseLeave={(e) => { if (!isSelected) e.currentTarget.style.background = "transparent"; }}>
                      <div style={{ width: 24, height: 24, borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center",
                        background: isPast || isSelected ? "var(--color-brand-600)" : "var(--color-surface-2)",
                        color: isPast || isSelected ? "#fff" : "var(--color-ink-4)",
                        border: isPast || isSelected ? "none" : "1px solid var(--color-border)", flexShrink: 0 }}>
                        {isPast && !isSelected ? <Icon name="check" size={12} /> : <Icon name={s.icon} size={12} />}
                      </div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: 13, fontWeight: 500, color: isSelected ? "var(--color-brand-700)" : "var(--color-ink)" }}>{s.label}</div>
                        <div style={{ fontSize: 11, color: "var(--color-ink-3)" }}>{s.desc}</div>
                      </div>
                      {s.key === localStatus && !pending && (
                        <span style={{ fontSize: 10, fontWeight: 600, color: "var(--color-brand-600)", textTransform: "uppercase", letterSpacing: "0.04em" }}>Current</span>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            <div style={{ padding: "8px 14px 12px", borderTop: "1px solid var(--color-border)" }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: "var(--color-ink-3)", textTransform: "uppercase",
                letterSpacing: "0.06em", marginBottom: 6 }}>Closed</div>
              <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                {STATUS_EXITS.map((s) => {
                  const isSelected = s.key === (pending || localStatus);
                  return (
                    <div key={s.key} onClick={() => selectStatus(s.key)}
                      style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 10px",
                        borderRadius: 6, cursor: "pointer",
                        background: isSelected ? (s.key === "rejected" ? "var(--color-danger-bg)" : "var(--color-surface-2)") : "transparent",
                        border: isSelected ? "1px solid " + (s.key === "rejected" ? "var(--color-danger-border)" : "var(--color-border-2)") : "1px solid transparent",
                      }}
                      onMouseEnter={(e) => { if (!isSelected) e.currentTarget.style.background = "var(--color-surface-2)"; }}
                      onMouseLeave={(e) => { if (!isSelected) e.currentTarget.style.background = isSelected ? "" : "transparent"; }}>
                      <div style={{ width: 24, height: 24, borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center",
                        background: isSelected ? (s.key === "rejected" ? "var(--color-danger)" : "var(--color-ink-3)") : "var(--color-surface-2)",
                        color: isSelected ? "#fff" : "var(--color-ink-4)",
                        border: isSelected ? "none" : "1px solid var(--color-border)", flexShrink: 0 }}>
                        <Icon name={s.icon} size={12} />
                      </div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: 13, fontWeight: 500, color: "var(--color-ink)" }}>{s.label}</div>
                        <div style={{ fontSize: 11, color: "var(--color-ink-3)" }}>{s.desc}</div>
                      </div>
                      {s.key === localStatus && !pending && (
                        <span style={{ fontSize: 10, fontWeight: 600, color: "var(--color-ink-3)", textTransform: "uppercase", letterSpacing: "0.04em" }}>Current</span>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            {showBackwardWarning && pending && (
              <div style={{ padding: "12px 14px", borderTop: "1px solid var(--color-warning-border)", background: "var(--color-warning-bg)",
                display: "flex", flexDirection: "column", gap: 8 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <Icon name="info" size={14} style={{ color: "var(--color-warning)", flexShrink: 0 }} />
                  <div style={{ fontSize: 12, color: "var(--color-ink)", fontWeight: 500 }}>Moving back to {STATUS_LABEL[pending]}</div>
                </div>
                <div style={{ fontSize: 12, color: "var(--color-ink-2)", lineHeight: 1.5 }}>
                  This will set the status back from {STATUS_LABEL[localStatus]} to {STATUS_LABEL[pending]}. Progress after this stage may be lost.
                </div>
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                  <Button variant="ghost" size="sm" onClick={() => { setPending(null); setShowBackwardWarning(false); }}>Cancel</Button>
                  <Button variant="primary" size="sm" onClick={confirmChange}>Confirm change</Button>
                </div>
              </div>
            )}

            {pending && pending !== localStatus && !showBackwardWarning && (
              <div style={{ padding: "10px 14px", borderTop: "1px solid var(--color-border)", background: "var(--color-bg)",
                display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ flex: 1, fontSize: 12, color: "var(--color-ink-2)" }}>
                  Change to <span style={{ fontWeight: 600 }}>{STATUS_LABEL[pending]}</span>?
                </div>
                <Button variant="ghost" size="sm" onClick={() => setPending(null)}>Cancel</Button>
                <Button variant="primary" size="sm" onClick={confirmChange}>Confirm</Button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export { ApplicationsScreen, ApplicationDetailScreen };