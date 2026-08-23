/* ── Apply profile: shared field model + copy-value helpers ──
   Single source of truth for the field set/order/labels used by BOTH the editable
   Settings section (ApplyProfileSection, screens/SavedSettings.jsx) and the
   read-only quick-access drawer (components/applyProfile/ApplyProfileDrawer.jsx).
   Story #336 (bank) / #460 (drawer), architect design note #478.

   Pure module: no JSX, no React import, so it is trivially unit-testable
   (src/test/unit/applyProfileFields.test.js). */

export const APPLY_PROFILE_TEXT_FIELDS = [
  { key: "workAuthorization", label: "Work authorization", maxLength: 200, placeholder: "e.g. US Citizen" },
  { key: "noticePeriod", label: "Notice period", maxLength: 100, placeholder: "e.g. 2 weeks" },
  { key: "salaryExpectation", label: "Salary expectation", maxLength: 100, placeholder: "e.g. $120k - $140k" },
  { key: "currentLocation", label: "Current location", maxLength: 200, placeholder: "e.g. Madrid, Spain" },
  { key: "linkedinUrl", label: "LinkedIn URL", maxLength: 500, placeholder: "https://linkedin.com/in/…" },
  { key: "githubUrl", label: "GitHub URL", maxLength: 500, placeholder: "https://github.com/…" },
  { key: "portfolioUrl", label: "Portfolio URL", maxLength: 500, placeholder: "https://…" },
];

export const APPLY_PROFILE_BOOL_FIELDS = ["requiresSponsorship", "willingToRelocate"];

export const APPLY_PROFILE_FIELD_LABELS = {
  workAuthorization: "Work authorization",
  noticePeriod: "Notice period",
  salaryExpectation: "Salary expectation",
  currentLocation: "Current location",
  requiresSponsorship: "Requires sponsorship",
  willingToRelocate: "Willing to relocate",
  linkedinUrl: "LinkedIn URL",
  githubUrl: "GitHub URL",
  portfolioUrl: "Portfolio URL",
  languages: "Languages",
  roomToGrow: "Room to grow",
};

// Fixed display order for the read-only drawer (PDA spec #479 §3): text fields 1-4,
// the two booleans, text fields 5-7 (the URL trio), Languages, then Room to grow.
// This mirrors ApplyProfileSection's own JSX render order exactly (BR-2).
export const APPLY_PROFILE_ORDERED_FIELDS = [
  { key: "workAuthorization", label: APPLY_PROFILE_FIELD_LABELS.workAuthorization, type: "text" },
  { key: "noticePeriod", label: APPLY_PROFILE_FIELD_LABELS.noticePeriod, type: "text" },
  { key: "salaryExpectation", label: APPLY_PROFILE_FIELD_LABELS.salaryExpectation, type: "text" },
  { key: "currentLocation", label: APPLY_PROFILE_FIELD_LABELS.currentLocation, type: "text" },
  { key: "requiresSponsorship", label: APPLY_PROFILE_FIELD_LABELS.requiresSponsorship, type: "bool" },
  { key: "willingToRelocate", label: APPLY_PROFILE_FIELD_LABELS.willingToRelocate, type: "bool" },
  { key: "linkedinUrl", label: APPLY_PROFILE_FIELD_LABELS.linkedinUrl, type: "text" },
  { key: "githubUrl", label: APPLY_PROFILE_FIELD_LABELS.githubUrl, type: "text" },
  { key: "portfolioUrl", label: APPLY_PROFILE_FIELD_LABELS.portfolioUrl, type: "text" },
  { key: "languages", label: APPLY_PROFILE_FIELD_LABELS.languages, type: "languages" },
  { key: "roomToGrow", label: APPLY_PROFILE_FIELD_LABELS.roomToGrow, type: "text" },
];

// Formats an ISO datetime string to a locale-friendly short form, e.g. "20 Jul 2026, 10:00".
export function formatApplyProfileSavedAt(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, {
    day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit",
  });
}

// Copy value for a tri-state boolean field: "Yes" / "No" / null (never the raw
// boolean, never the strings "true"/"false"). Mirrors ApplyProfileBoolField's
// inline copyValue derivation.
export function boolCopyValue(value) {
  if (value === true) return "Yes";
  if (value === false) return "No";
  return null;
}

// Copy value for the Languages field: all non-blank entries joined ", ", or null
// when the list is absent/empty/blank-only. Mirrors ApplyProfileLanguagesField's
// inline copyValue derivation.
export function languagesCopyValue(languages) {
  if (!Array.isArray(languages)) return null;
  const nonEmpty = languages.filter((l) => l && l.trim() !== "");
  return nonEmpty.length > 0 ? nonEmpty.join(", ") : null;
}

// The read surface's on-screen value for one ordered field, or null when
// empty/unset (PDA spec #479 §3: the copy value IS the display value for
// every field type). Shared by ApplyProfileReadOnlyList (per-row rendering)
// and ApplyProfileDrawer (the all-empty-state check).
export function applyProfileFieldValue(field, profile) {
  if (!profile) return null;
  if (field.type === "bool") return boolCopyValue(profile[field.key]);
  if (field.type === "languages") return languagesCopyValue(profile.languages);
  const v = profile[field.key];
  return v !== null && v !== undefined && v !== "" ? v : null;
}

// True when every field in APPLY_PROFILE_ORDERED_FIELDS is empty/unset (BR-4:
// the all-empty state is dedicated, never rendered as an empty list).
export function isApplyProfileEmpty(profile) {
  if (!profile) return true;
  return APPLY_PROFILE_ORDERED_FIELDS.every((field) => applyProfileFieldValue(field, profile) === null);
}
