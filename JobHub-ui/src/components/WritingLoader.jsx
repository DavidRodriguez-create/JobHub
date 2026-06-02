import React from "react";

// A playful list-loading indicator: a pencil scribbling out job-post lines in a
// hurry. Self-contained — injects its keyframes once (same pattern as the dual
// range slider) so it can be dropped into any slow-loading list.

const CSS = `
.wl-svg .wl-line {
  transform-box: fill-box; transform-origin: left center;
  animation: wl-line 1.6s ease-in-out infinite;
}
.wl-svg .wl-l2 { animation-delay: .18s; }
.wl-svg .wl-l3 { animation-delay: .36s; }
.wl-svg .wl-l4 { animation-delay: .54s; }
@keyframes wl-line {
  0%   { transform: scaleX(0); opacity: .35; }
  26%  { transform: scaleX(1); opacity: 1; }
  82%  { transform: scaleX(1); opacity: 1; }
  100% { transform: scaleX(0); opacity: .35; }
}
.wl-svg .wl-pen-move { animation: wl-pen-move 1.6s steps(1, end) infinite; }
@keyframes wl-pen-move {
  0%   { transform: translateY(0); }
  25%  { transform: translateY(16px); }
  50%  { transform: translateY(32px); }
  75%  { transform: translateY(48px); }
  100% { transform: translateY(0); }
}
.wl-svg .wl-pen-shake {
  transform-box: fill-box; transform-origin: 40px 32px;
  animation: wl-pen-shake .14s ease-in-out infinite;
}
@keyframes wl-pen-shake {
  0%, 100% { transform: rotate(-3deg) translateX(0); }
  50%      { transform: rotate(2deg) translateX(1px); }
}
@media (prefers-reduced-motion: reduce) {
  .wl-svg .wl-line, .wl-svg .wl-pen-move, .wl-svg .wl-pen-shake { animation: none; }
}
`;

export default function WritingLoader({ label = "Writing up the latest postings…", size = 132 }) {
  React.useEffect(() => {
    if (document.getElementById("writing-loader-css")) return;
    const style = document.createElement("style");
    style.id = "writing-loader-css";
    style.textContent = CSS;
    document.head.appendChild(style);
  }, []);

  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 14, padding: "56px 0", color: "var(--color-ink-3)" }}>
      <svg className="wl-svg" width={size} height={size * 0.8} viewBox="0 0 132 104" role="img" aria-label={label}>
        {/* sheet of paper */}
        <rect x="20" y="14" width="70" height="82" rx="7" fill="var(--color-surface)" stroke="var(--color-border-2)" strokeWidth="2" />
        {/* lines being written */}
        <rect className="wl-line wl-l1" x="32" y="32" width="46" height="5" rx="2.5" fill="var(--color-brand-300)" />
        <rect className="wl-line wl-l2" x="32" y="48" width="40" height="5" rx="2.5" fill="var(--color-ink-4)" />
        <rect className="wl-line wl-l3" x="32" y="64" width="44" height="5" rx="2.5" fill="var(--color-ink-4)" />
        <rect className="wl-line wl-l4" x="32" y="80" width="26" height="5" rx="2.5" fill="var(--color-ink-4)" />
        {/* pencil — moves down line by line, jittering to look hurried */}
        <g className="wl-pen-move">
          <g className="wl-pen-shake">
            <line x1="40" y1="34" x2="74" y2="6" stroke="var(--color-brand-600)" strokeWidth="5" strokeLinecap="round" />
            <line x1="74" y1="6" x2="80" y2="2" stroke="var(--color-ink-3)" strokeWidth="5" strokeLinecap="round" />
            <circle cx="39" cy="35" r="2.4" fill="var(--color-ink)" />
          </g>
        </g>
      </svg>
      <div style={{ fontSize: 13 }}>{label}</div>
    </div>
  );
}
