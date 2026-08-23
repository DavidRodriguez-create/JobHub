import React from "react";
import { APPLY_PROFILE_ORDERED_FIELDS, applyProfileFieldValue } from "./applyProfileFields.js";
import { ApplyProfileCopyButton } from "./ApplyProfileCopyButton.jsx";

/* ── Apply profile: read-only field list ──
   Used ONLY by the quick-access drawer (story #460). Renders one static
   label -> value row per POPULATED field, in the fixed order defined by
   APPLY_PROFILE_ORDERED_FIELDS (PDA spec #479 §3); unset fields are omitted
   entirely (BR-3), no blank row, no "Not set" placeholder. Every rendered
   row carries its own read-only ApplyProfileCopyButton; there is no editable
   input, toggle, or add/remove control anywhere in this component (BR-1). */
export function ApplyProfileReadOnlyList({ profile, copiedField, onCopy }) {
  const rows = APPLY_PROFILE_ORDERED_FIELDS
    .map((field) => ({ field, value: applyProfileFieldValue(field, profile) }))
    .filter((row) => row.value !== null);

  return (
    <div data-testid="apply-profile-readonly-list" style={{ display: "flex", flexDirection: "column", gap: 4 }}>
      {rows.map(({ field, value }) => (
        <div
          key={field.key}
          data-testid="apply-profile-field-row"
          style={{
            display: "flex", alignItems: "center", gap: 10, padding: "10px 0",
            borderBottom: "1px solid var(--color-border)",
          }}
        >
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 11, color: "var(--color-ink-3)", marginBottom: 2 }}>{field.label}</div>
            <div style={{ fontSize: 13, color: "var(--color-ink)", fontWeight: 500, wordBreak: "break-word" }}>{value}</div>
          </div>
          <ApplyProfileCopyButton fieldKey={field.key} label={field.label} value={value} copiedField={copiedField} onCopy={onCopy} />
        </div>
      ))}
    </div>
  );
}
