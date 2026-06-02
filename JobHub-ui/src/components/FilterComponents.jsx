import React from "react";
import Icon from "./Icon.jsx";

// JobHub — Filter components: DualRange slider + MultiSelect dropdown

/* ─── Dual-thumb Range Slider ─── */
function DualRangeSlider({ min, max, step = 10, valueMin, valueMax, onChange, formatLabel }) {
  const trackRef = React.useRef(null);
  const fmt = formatLabel || ((v) => v);

  const pctMin = ((valueMin - min) / (max - min)) * 100;
  const pctMax = ((valueMax - min) / (max - min)) * 100;

  const dualRangeStyles = {
    wrap: { position: "relative", height: 20, userSelect: "none" },
    track: {
      position: "absolute", top: 7, left: 0, right: 0, height: 6,
      borderRadius: 3, background: "var(--color-surface-3)",
    },
    fill: {
      position: "absolute", top: 7, height: 6, borderRadius: 3,
      background: "var(--color-brand-600)",
      left: `${pctMin}%`, right: `${100 - pctMax}%`,
    },
    input: {
      position: "absolute", top: -1, left: 0, width: "100%", height: 22,
      margin: 0, appearance: "none", WebkitAppearance: "none",
      background: "transparent", pointerEvents: "none", zIndex: 2, outline: "none",
    },
    labels: {
      display: "flex", justifyContent: "space-between", marginTop: 6,
      fontSize: 11, fontFamily: "var(--font-mono)", color: "var(--color-ink-2)",
    },
  };

  // Thumb styling via CSS class (injected once)
  React.useEffect(() => {
    if (document.getElementById("dual-range-css")) return;
    const style = document.createElement("style");
    style.id = "dual-range-css";
    style.textContent = `
      .dual-range::-webkit-slider-thumb {
        -webkit-appearance: none; appearance: none;
        width: 18px; height: 18px; border-radius: 50%;
        background: #fff; border: 2px solid var(--color-brand-600);
        box-shadow: 0 1px 3px rgba(0,0,0,0.15);
        pointer-events: auto; cursor: grab; position: relative; z-index: 3;
        margin-top: -6px;
      }
      .dual-range::-moz-range-thumb {
        width: 18px; height: 18px; border-radius: 50%;
        background: #fff; border: 2px solid var(--color-brand-600);
        box-shadow: 0 1px 3px rgba(0,0,0,0.15);
        pointer-events: auto; cursor: grab;
      }
      .dual-range::-webkit-slider-runnable-track { background: transparent; height: 6px; }
      .dual-range::-moz-range-track { background: transparent; height: 6px; }
    `;
    document.head.appendChild(style);
  }, []);

  return (
    <div>
      <div style={dualRangeStyles.wrap}>
        <div style={dualRangeStyles.track} ref={trackRef} />
        <div style={dualRangeStyles.fill} />
        <input type="range" className="dual-range" min={min} max={max} step={step} value={valueMin}
          onChange={(e) => {
            const v = +e.target.value;
            if (v <= valueMax - step) onChange(v, valueMax);
          }}
          style={{ ...dualRangeStyles.input, zIndex: pctMin > 50 ? 3 : 2 }} />
        <input type="range" className="dual-range" min={min} max={max} step={step} value={valueMax}
          onChange={(e) => {
            const v = +e.target.value;
            if (v >= valueMin + step) onChange(valueMin, v);
          }}
          style={dualRangeStyles.input} />
      </div>
      <div style={dualRangeStyles.labels}>
        <span>{fmt(valueMin)}</span>
        <span>{fmt(valueMax)}</span>
      </div>
    </div>
  );
}

/* ─── Multi-select Dropdown ─── */
function MultiSelect({ label, options, selected, onChange, renderOption, maxDisplay = 2 }) {
  const [open, setOpen] = React.useState(false);
  const [search, setSearch] = React.useState("");
  const wrapRef = React.useRef(null);

  // Close on outside click
  React.useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  const toggle = (val) => {
    const next = new Set(selected);
    next.has(val) ? next.delete(val) : next.add(val);
    onChange(next);
  };

  const filtered = search
    ? options.filter((o) => (o.label || o.value).toLowerCase().includes(search.toLowerCase()))
    : options;

  // Display text
  let displayText = label;
  if (selected.size === 1) {
    const sel = options.find((o) => selected.has(o.value));
    displayText = sel ? sel.label : label;
  } else if (selected.size > 1) {
    displayText = `${selected.size} selected`;
  }

  const msStyles = {
    trigger: {
      width: "100%", height: 34, padding: "0 10px", display: "flex", alignItems: "center", gap: 6,
      border: "1px solid var(--color-border-2)", borderRadius: 6, background: "var(--color-surface)",
      cursor: "pointer", fontSize: 13, color: selected.size > 0 ? "var(--color-ink)" : "var(--color-ink-4)",
      transition: "border-color 120ms", letterSpacing: "-0.006em",
    },
    dropdown: {
      position: "absolute", top: "calc(100% + 4px)", left: 0, right: 0,
      background: "var(--color-surface)", border: "1px solid var(--color-border-2)",
      borderRadius: 8, boxShadow: "var(--shadow-md)", zIndex: 20, maxHeight: 240, overflow: "hidden",
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
  };

  return (
    <div ref={wrapRef} style={{ position: "relative" }}>
      <div style={msStyles.trigger} onClick={() => setOpen(!open)}>
        <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
          {displayText}
        </span>
        {selected.size > 0 && (
          <span onClick={(e) => { e.stopPropagation(); onChange(new Set()); }}
            style={{ color: "var(--color-ink-3)", display: "flex", cursor: "pointer" }}>
            <Icon name="x" size={12} />
          </span>
        )}
        <Icon name="chevron-down" size={14} style={{ color: "var(--color-ink-3)", flexShrink: 0 }} />
      </div>
      {open && (
        <div style={msStyles.dropdown}>
          {options.length > 5 && (
            <input style={msStyles.searchInput} placeholder="Search…" value={search}
              onChange={(e) => setSearch(e.target.value)} autoFocus />
          )}
          <div style={msStyles.list}>
            {filtered.map((o) => (
              <div key={o.value} style={{ ...msStyles.row, background: selected.has(o.value) ? "var(--color-brand-50)" : "transparent" }}
                onClick={() => toggle(o.value)}
                onMouseEnter={(e) => { if (!selected.has(o.value)) e.currentTarget.style.background = "var(--color-surface-2)"; }}
                onMouseLeave={(e) => { if (!selected.has(o.value)) e.currentTarget.style.background = "transparent"; }}>
                <input type="checkbox" checked={selected.has(o.value)} readOnly
                  style={{ accentColor: "var(--color-brand-600)", pointerEvents: "none" }} />
                <span style={{ flex: 1 }}>{renderOption ? renderOption(o) : o.label}</span>
                {o.count != null && <span style={msStyles.count}>{o.count}</span>}
              </div>
            ))}
            {filtered.length === 0 && (
              <div style={{ padding: "12px 10px", fontSize: 12, color: "var(--color-ink-4)", textAlign: "center" }}>No results</div>
            )}
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

  React.useEffect(() => {
    if (!open) return;
    const h = (e) => { if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false); };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, [open]);

  return (
    <div ref={wrapRef} style={{ position: "relative" }}>
      <div onClick={() => setOpen(!open)}
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
        <div style={{ position: "absolute", top: "calc(100% + 4px)", left: 0, right: 0,
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

export { DualRangeSlider, MultiSelect, SavedFiltersDropdown };
