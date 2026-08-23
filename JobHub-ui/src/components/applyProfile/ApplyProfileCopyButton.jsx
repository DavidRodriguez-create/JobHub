import React from "react";
import * as UI from "../ui.jsx";

const { Button } = UI;

/* ── Apply profile: shared per-field copy control ──
   Moved verbatim from screens/SavedSettings.jsx (story #336) so the editable
   Settings section and the read-only drawer (story #460) render the identical
   DOM/testids: field-copy-<key> / field-copied-<key>. */
export function ApplyProfileCopyButton({ fieldKey, label, value, copiedField, onCopy }) {
  const hasValue = value !== null && value !== undefined && value !== "";
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        icon="copy"
        disabled={!hasValue}
        aria-label={`Copy ${label}`}
        data-testid={`field-copy-${fieldKey}`}
        onClick={() => onCopy(fieldKey, value)}
      />
      {copiedField === fieldKey && (
        <span role="status" data-testid={`field-copied-${fieldKey}`} style={{ fontSize: 12, color: "var(--color-success)" }}>
          Copied
        </span>
      )}
    </div>
  );
}
