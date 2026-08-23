import React from "react";
import Icon from "./Icon.jsx";

// JobHub — Filter components: MultiSelect dropdown + Saved Filters dropdown
// Story #523: the compensation range control (DualRangeSlider) was removed from the
// job-search filter panel; git history holds the implementation if it ever returns.

/* ─── Symmetric difference size: how many values would change between two Sets ─── */
function symmetricDifferenceSize(a, b) {
  let count = 0;
  a.forEach((v) => { if (!b.has(v)) count++; });
  b.forEach((v) => { if (!a.has(v)) count++; });
  return count;
}

/* ─── Multi-select Dropdown ───
 * `applied` is the dimension's currently-applied Set (drives chips/results/facets).
 * Checkbox toggles inside an open dropdown mutate a local `pending` Set only.
 * `onApply(pendingSet)` commits pending → applied (one dimension) and closes the dropdown.
 * `onClearApplied()` is the trigger's "x clear" affordance: clears both pending and
 * applied for this dimension immediately (clear-and-apply).
 * Closing without Apply (click-outside / Esc / opening another dropdown) discards pending.
 */
function MultiSelect({ label, options, applied, onApply, onClearApplied, renderOption, maxDisplay = 2, resetSignal }) {
  const [open, setOpen] = React.useState(false);
  const [pending, setPending] = React.useState(() => new Set(applied));
  const [search, setSearch] = React.useState("");
  const wrapRef = React.useRef(null);
  const triggerRef = React.useRef(null);
  // Story #458: the dropdown popover is positioned with `position: fixed`, computed from
  // the trigger's own bounding rect, so it escapes the sticky `.search-filters` column's
  // `overflow-y: auto` clip (the column that hosts every filter, including this one, is
  // now a scroll/clip container). It stays a normal DOM child of wrapRef (not portaled),
  // so the existing click-outside `wrapRef.contains(e.target)` check keeps working
  // unchanged.
  const [dropdownRect, setDropdownRect] = React.useState(null);

  const updateDropdownRect = React.useCallback(() => {
    if (!triggerRef.current) return;
    const r = triggerRef.current.getBoundingClientRect();
    setDropdownRect({ top: r.bottom + 4, left: r.left, width: r.width });
  }, []);

  // Opening the dropdown (re-)initialises pending from the currently-applied state (BR-1/BR-7).
  // Closing without Apply simply leaves `pending` stale — it's overwritten on next open.
  const openDropdown = () => {
    setPending(new Set(applied));
    updateDropdownRect();
    setOpen(true);
  };

  const closeDiscard = () => setOpen(false);

  // "Clear all" / "Saved filters" overwrite applied state for all four dimensions in one
  // shot (E7/AC-F18/AC-F19): if this dropdown is open, discard its pending and close it too.
  const firstRender = React.useRef(true);
  React.useEffect(() => {
    if (firstRender.current) { firstRender.current = false; return; }
    if (open) closeDiscard();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resetSignal]);

  // Close on outside click — discards pending (AC-F04).
  React.useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) closeDiscard();
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // Esc discards pending like click-outside (AC-F06).
  React.useEffect(() => {
    if (!open) return;
    const handler = (e) => { if (e.key === "Escape") closeDiscard(); };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // Story #458: keep the fixed-position dropdown pinned under its trigger while open, even
  // when the user scrolls the sticky `.search-filters` column (a scroll event on that
  // container doesn't bubble, but a capture-phase window listener still sees it) or resizes
  // the window.
  React.useEffect(() => {
    if (!open) return;
    const handler = () => updateDropdownRect();
    window.addEventListener("scroll", handler, true);
    window.addEventListener("resize", handler);
    return () => {
      window.removeEventListener("scroll", handler, true);
      window.removeEventListener("resize", handler);
    };
  }, [open, updateDropdownRect]);

  const toggle = (val) => {
    setPending((prev) => {
      const next = new Set(prev);
      next.has(val) ? next.delete(val) : next.add(val);
      return next;
    });
  };

  const filtered = search
    ? options.filter((o) => (o.label || o.value).toLowerCase().includes(search.toLowerCase()))
    : options;

  // BR-10: a pending/applied value absent from `options` (zero-count, FE-F05) must still
  // render, checked, in the list. While open, merge pending values too (not just applied).
  const mergeSource = open ? pending : applied;
  const extraSelected = [...mergeSource].filter((v) => !options.some((o) => o.value === v));
  const displayOptions = extraSelected.length > 0
    ? [...extraSelected.map((v) => ({ value: v, label: v })), ...filtered]
    : filtered;

  const isChecked = (val) => (open ? pending.has(val) : applied.has(val));

  const pendingEqualsApplied = pending.size === applied.size && [...pending].every((v) => applied.has(v));
  const diffCount = symmetricDifferenceSize(applied, pending);
  const applyLabel = pendingEqualsApplied ? "Apply" : `Apply (${diffCount})`;

  const handleApply = () => {
    onApply(pending);
    setOpen(false);
  };

  const handleClearApplied = (e) => {
    e.stopPropagation();
    setPending(new Set());
    onClearApplied();
  };

  // Display text reflects APPLIED state (chips/trigger never show pending).
  let displayText = label;
  if (applied.size === 1) {
    const sel = options.find((o) => applied.has(o.value));
    displayText = sel ? sel.label : [...applied][0];
  } else if (applied.size > 1) {
    displayText = `${applied.size} selected`;
  }

  const msStyles = {
    trigger: {
      width: "100%", height: 34, padding: "0 10px", display: "flex", alignItems: "center", gap: 6,
      border: "1px solid var(--color-border-2)", borderRadius: 6, background: "var(--color-surface)",
      cursor: "pointer", fontSize: 13, color: applied.size > 0 ? "var(--color-ink)" : "var(--color-ink-4)",
      transition: "border-color 120ms", letterSpacing: "-0.006em",
    },
    dropdown: {
      // Story #458: fixed (not absolute) so the popover escapes the sticky
      // `.search-filters` column's `overflow-y: auto` clip. Coordinates come from the
      // trigger's own bounding rect (dropdownRect), recomputed on open/scroll/resize.
      position: "fixed",
      top: dropdownRect ? dropdownRect.top : 0,
      left: dropdownRect ? dropdownRect.left : 0,
      width: dropdownRect ? dropdownRect.width : undefined,
      background: "var(--color-surface)", border: "1px solid var(--color-border-2)",
      borderRadius: 8, boxShadow: "var(--shadow-md)", zIndex: 20, maxHeight: 280, overflow: "hidden",
      display: "flex", flexDirection: "column", animation: "fade-in 120ms ease",
    },
    searchInput: {
      margin: "8px 8px 4px", padding: "6px 8px", border: "1px solid var(--color-border)",
      borderRadius: 4, fontSize: 12, outline: "none", background: "var(--color-bg)",
      color: "var(--color-ink)",
    },
    list: { overflowY: "auto", maxHeight: 180, padding: "4px 0" },
    row: {
      display: "flex", alignItems: "center", gap: 8, padding: "6px 10px",
      fontSize: 13, color: "var(--color-ink-2)", cursor: "pointer", userSelect: "none",
    },
    count: {
      marginLeft: "auto", fontFamily: "var(--font-mono)", fontSize: 11, color: "var(--color-ink-4)",
    },
    applyRow: {
      padding: 8, borderTop: "1px solid var(--color-border)",
    },
    applyBtn: {
      width: "100%", height: 30, borderRadius: 6, border: "1px solid var(--color-border-2)",
      background: "var(--color-brand-600)", color: "#fff", fontSize: 12, fontWeight: 600,
      cursor: "pointer",
    },
    applyBtnDisabled: {
      width: "100%", height: 30, borderRadius: 6, border: "1px solid var(--color-border-2)",
      background: "var(--color-surface-2)", color: "var(--color-ink-4)", fontSize: 12, fontWeight: 600,
      cursor: "default",
    },
  };

  return (
    <div ref={wrapRef} style={{ position: "relative" }}>
      <div ref={triggerRef} style={msStyles.trigger} onClick={() => (open ? closeDiscard() : openDropdown())}>
        <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
          {displayText}
        </span>
        {applied.size > 0 && (
          <span onClick={handleClearApplied}
            style={{ color: "var(--color-ink-3)", display: "flex", cursor: "pointer" }}>
            <Icon name="x" size={12} />
          </span>
        )}
        <Icon name="chevron-down" size={14} style={{ color: "var(--color-ink-3)", flexShrink: 0 }} />
      </div>
      {open && (
        <div style={msStyles.dropdown} data-testid="multiselect-dropdown">
          {options.length > 5 && (
            <input style={msStyles.searchInput} placeholder="Search…" value={search}
              onChange={(e) => setSearch(e.target.value)} autoFocus />
          )}
          <div style={msStyles.list}>
            {displayOptions.map((o) => (
              <div key={o.value} style={{ ...msStyles.row, background: isChecked(o.value) ? "var(--color-brand-50)" : "transparent" }}
                onClick={() => toggle(o.value)}
                onMouseEnter={(e) => { if (!isChecked(o.value)) e.currentTarget.style.background = "var(--color-surface-2)"; }}
                onMouseLeave={(e) => { if (!isChecked(o.value)) e.currentTarget.style.background = "transparent"; }}>
                <input type="checkbox" checked={isChecked(o.value)} readOnly
                  style={{ accentColor: "var(--color-brand-600)", pointerEvents: "none" }} />
                <span style={{ flex: 1 }}>{renderOption ? renderOption(o) : o.label}</span>
                {o.count != null && <span style={msStyles.count}>{o.count}</span>}
              </div>
            ))}
            {displayOptions.length === 0 && (
              <div style={{ padding: "12px 10px", fontSize: 12, color: "var(--color-ink-4)", textAlign: "center" }}>No results</div>
            )}
          </div>
          <div style={msStyles.applyRow}>
            <button type="button" disabled={pendingEqualsApplied}
              style={pendingEqualsApplied ? msStyles.applyBtnDisabled : msStyles.applyBtn}
              onClick={handleApply}>
              {applyLabel}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/* ─── Saved Filters Dropdown ─── */
function SavedFiltersDropdown({ filters, onApply, onDelete }) {
  const [open, setOpen] = React.useState(false);
  const wrapRef = React.useRef(null);
  const triggerRef = React.useRef(null);
  // Story #458: same fixed-position-from-trigger-rect treatment as MultiSelect, so this
  // popover also escapes the sticky `.search-filters` column's `overflow-y: auto` clip.
  const [popoverRect, setPopoverRect] = React.useState(null);

  const updatePopoverRect = React.useCallback(() => {
    if (!triggerRef.current) return;
    const r = triggerRef.current.getBoundingClientRect();
    setPopoverRect({ top: r.bottom + 4, left: r.left, width: r.width });
  }, []);

  React.useEffect(() => {
    if (!open) return;
    const h = (e) => { if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false); };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, [open]);

  React.useEffect(() => {
    if (!open) return;
    const handler = () => updatePopoverRect();
    window.addEventListener("scroll", handler, true);
    window.addEventListener("resize", handler);
    return () => {
      window.removeEventListener("scroll", handler, true);
      window.removeEventListener("resize", handler);
    };
  }, [open, updatePopoverRect]);

  const toggleOpen = () => {
    if (!open) updatePopoverRect();
    setOpen(!open);
  };

  return (
    <div ref={wrapRef} style={{ position: "relative" }}>
      <div ref={triggerRef} onClick={toggleOpen}
        style={{ display: "flex", alignItems: "center", gap: 8, padding: "7px 10px",
          border: "1px solid var(--color-border-2)", borderRadius: 6, background: "var(--color-surface)",
          cursor: "pointer", fontSize: 13, color: "var(--color-ink-2)", transition: "background 120ms" }}
        onMouseEnter={(e) => e.currentTarget.style.background = "var(--color-surface-2)"}
        onMouseLeave={(e) => e.currentTarget.style.background = "var(--color-surface)"}>
        <Icon name="sliders-horizontal" size={14} style={{ color: "var(--color-ink-3)" }} />
        <span style={{ flex: 1 }}>Saved filters</span>
        <span style={{ fontSize: 11, fontFamily: "var(--font-mono)", color: "var(--color-ink-4)", background: "var(--color-surface-2)",
          padding: "1px 6px", borderRadius: 9999, minWidth: 18, textAlign: "center" }}>{filters.length}</span>
        <Icon name="chevron-down" size={14} style={{ color: "var(--color-ink-3)", transform: open ? "rotate(180deg)" : "none", transition: "transform 150ms" }} />
      </div>
      {open && (
        <div data-testid="saved-filters-dropdown" style={{
          position: "fixed",
          top: popoverRect ? popoverRect.top : 0,
          left: popoverRect ? popoverRect.left : 0,
          width: popoverRect ? popoverRect.width : undefined,
          background: "var(--color-surface)", border: "1px solid var(--color-border-2)",
          borderRadius: 8, boxShadow: "var(--shadow-md)", zIndex: 20, overflow: "hidden",
          animation: "fade-in 120ms ease" }}>
          {filters.map((sf, i) => (
            <div key={i} style={{ display: "flex", alignItems: "center", gap: 8, padding: "9px 12px",
              cursor: "pointer", fontSize: 13, color: "var(--color-ink-2)",
              borderBottom: i < filters.length - 1 ? "1px solid var(--color-border)" : "none" }}
              onClick={() => { onApply(sf.state); setOpen(false); }}
              onMouseEnter={(e) => e.currentTarget.style.background = "var(--color-surface-2)"}
              onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}>
              <Icon name="bookmark" size={13} style={{ color: "var(--color-brand-600)", flexShrink: 0 }} />
              <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{sf.name}</span>
              <span onClick={(e) => { e.stopPropagation(); onDelete(i); if (filters.length <= 1) setOpen(false); }}
                style={{ color: "var(--color-ink-4)", cursor: "pointer", display: "flex", padding: 2, borderRadius: 4 }}
                onMouseEnter={(e) => e.currentTarget.style.color = "var(--color-danger)"}
                onMouseLeave={(e) => e.currentTarget.style.color = "var(--color-ink-4)"}>
                <Icon name="trash" size={12} />
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export { MultiSelect, SavedFiltersDropdown, symmetricDifferenceSize };
