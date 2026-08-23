// JobHub - shared notification row presentation (icon-per-type + relative time label).
// Used by the All-notifications page (Story #184) so its rows render consistently
// without duplicating this lookup table. The sidebar's redundant bell, which used to
// share this module too, was removed in story #206.
//
// Story #207 / Ticket #216: card identity (company + job title). NotificationResponse
// carries company/jobTitle, resolved server-side at read time (ADR 0014). Both fields
// are independently nullable and may be entirely absent from older API responses; this
// module's job is purely presentational, never throws, and never blocks rendering.
//
// Story #244 / Ticket #260 (BR-244-1): `resolved` now depends on jobTitle ALONE.
// A present jobTitle renders regardless of whether company is null (the gate-fix safety
// net for pre-capture crawled snapshots). companyLogoUrl is normalised (empty-string
// treated as null per EC-244-2) and exposed on the return value for the logo slot.

/** Fixed fallback label shown when the card's identity cannot be resolved (BR-1). */
export const FALLBACK_LABEL = "Application no longer available";

/**
 * Resolve a notification's card identity (company + job title + logo) defensively.
 *
 * Story #244 (BR-244-1) supersedes story #207's gate:
 *   - `resolved` is true when jobTitle is non-null and non-empty, regardless of company.
 *   - `companyLogoUrl` is normalised: empty-string is coerced to null (EC-244-2).
 *   - All fields degrade gracefully for null/undefined input.
 *
 * @param {object|null|undefined} notification
 * @returns {{ company: string|null, jobTitle: string|null, resolved: boolean, companyLogoUrl: string|null }}
 */
export function resolveCardIdentity(notification) {
  const company = notification?.company ?? null;
  const jobTitle = notification?.jobTitle ?? null;
  const rawLogoUrl = notification?.companyLogoUrl ?? null;
  // Normalise empty string to null (EC-244-2: some upstreams return "" instead of omitting)
  const companyLogoUrl = rawLogoUrl === "" ? null : rawLogoUrl;
  // BR-244-1: resolved gate depends on jobTitle alone (not company)
  const resolved = Boolean(jobTitle);
  return { company, jobTitle, resolved, companyLogoUrl };
}

// Story #439 / Ticket #535 (ADR 0031, BR-439-8): notification category gate.
// `category` is a required, server-derived field (APPLICATION | JOB_POST | ACCOUNT,
// see api-contracts/notification-service.yaml#NotificationCategory) that answers
// "what is this notification even about", independently of NotificationType. The
// UI's HARD INVARIANT: the application identity row renders only on a positive
// APPLICATION signal, never on the absence of one, so an unrecognised value, a
// null value, an absent field, or a null/undefined notification all resolve to
// the safe value, "ACCOUNT". Falling back to "APPLICATION" is forbidden. Never throws.
const RECOGNISED_CATEGORIES = new Set(["APPLICATION", "JOB_POST", "ACCOUNT"]);

/**
 * Resolve a notification's effective category defensively (BR-439-8).
 * @param {object|null|undefined} notification
 * @returns {"APPLICATION"|"JOB_POST"|"ACCOUNT"}
 */
export function categoryOf(notification) {
  const category = notification?.category;
  return RECOGNISED_CATEGORIES.has(category) ? category : "ACCOUNT";
}

export const TYPE_ICON = {
  INTERVIEW_REMINDER: "calendar",
  GHOSTED_ALERT: "alert-circle",
  APPLICATION_UPDATE: "briefcase",
  CUSTOM_REMINDER: "clock",
  SYSTEM: "info",
};

export function iconForType(type) {
  return TYPE_ICON[type] || "info";
}

/** Relative time label: "just now", "5m ago", "2h ago", "1d ago", "3d ago". */
export function timeAgo(isoString) {
  const then = new Date(isoString).getTime();
  if (Number.isNaN(then)) return "";
  const diffMs = Date.now() - then;
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}
