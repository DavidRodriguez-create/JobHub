// notification-service (/notifications) — per-user notification preference toggles.
// Mirrors api-contracts/openapi/notification-service.yaml.
// All endpoints require a JWT (user identity is derived from the token).
import { request } from "./client.js";

// ── SWR session cache for notification preferences ──
// Keyed in module scope (cleared on page unload / fresh session = module re-execution).
// Only successful GET responses are cached; errors are never stored.
// PUT responses write through to update the cache entry without a follow-up GET.
let _prefsCache = null; // { data: NotificationPreferencesResponse } | null

/** Expose cache setter for testing and for the PUT write-through path. */
export function _setPrefsCache(data) {
  _prefsCache = data != null ? { data } : null;
}

/** Clear the preferences cache (called on logout). */
export function clearNotificationPrefsCache() {
  _prefsCache = null;
}

// GET /notifications/preferences -> NotificationPreferencesResponse
// { weeklyDigestEmail, inAppNotificationsEnabled, interviewReminders,
//   interviewReminderEmail, ghostedAlert }
// Returns contract defaults for a user with no stored row (does not create one).
// SWR: returns cached response synchronously on cache-hit; issues network call otherwise.
export async function getNotificationPreferences() {
  if (_prefsCache !== null) {
    return _prefsCache.data;
  }
  const { data } = await request("/notifications/preferences", { auth: true });
  // Only cache on success (error throws before reaching here)
  _prefsCache = { data };
  return data;
}

/**
 * Partial or full update of the user's notification preferences (upsert semantics).
 * Supply only the fields to change, or all four for a full replacement.
 * Write-through: on 200 the response body is stored in the SWR cache so a
 * subsequent GET returns updated values without a network round-trip.
 * @param {{ weeklyDigestEmail?: boolean, inAppNotificationsEnabled?: boolean, interviewReminders?: boolean, interviewReminderEmail?: boolean, ghostedAlert?: boolean }} [prefs]
 * @returns {Promise<object>} the full, post-update NotificationPreferencesResponse
 */
export async function updateNotificationPreferences(prefs = {}) {
  const { data } = await request("/notifications/preferences", {
    method: "PUT",
    auth: true,
    body: { ...prefs },
  });
  // Write-through: update cache with server response
  _prefsCache = { data };
  return data;
}

/* ── Notification center (bell icon + dropdown) ── */

/**
 * GET /notifications — page of the authenticated user's notifications, newest first.
 *
 * Each content[] entry mirrors NotificationResponse (architect #185 / story #182):
 * { id, type, category, title, message, read, createdAt, applicationId }.
 * applicationId is an additive nullable uuid (null for SYSTEM / SECURITY_RECOMMENDATION);
 * this passes the raw response straight through with no field allowlist, so each field
 * reaches the UI unmodified as soon as the backend sends it.
 *
 * `category` (story #439 / ADR 0031, backend ticket #534): required, server-derived
 * enum APPLICATION | JOB_POST | ACCOUNT, coarser-grained than `type`. Backend ticket
 * #534 has not shipped on every environment yet, so a response may still omit it; the
 * UI never assumes its presence, it resolves an effective category defensively via
 * `categoryOf()` in components/notificationPresentation.js (unrecognised/absent value
 * -> "ACCOUNT", never "APPLICATION").
 * @param {{ page?: number, size?: number, readStatus?: "all"|"read"|"unread" }} [opts]
 * @returns {Promise<{ content: object[], page: number, size: number, totalElements: number, totalPages: number }>}
 */
export async function listNotifications({ page = 0, size = 20, readStatus = "all" } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size), readStatus });
  const { data } = await request(`/notifications?${params.toString()}`, { auth: true });
  return data;
}

/**
 * GET /notifications/unread-count -> { count }
 * @returns {Promise<{ count: number }>}
 */
export async function getUnreadCount() {
  const { data } = await request("/notifications/unread-count", { auth: true });
  return data;
}

/** PATCH /notifications/{id}/read — mark a single notification as read. Idempotent. */
export async function markNotificationRead(id) {
  await request(`/notifications/${id}/read`, { method: "PATCH", auth: true });
}

/** PATCH /notifications/read-all — mark all of the user's notifications as read. Idempotent. */
export async function markAllNotificationsRead() {
  await request("/notifications/read-all", { method: "PATCH", auth: true });
}

/**
 * DELETE /notifications/{id} — permanently delete a single notification (story #206 /
 * architect ticket #230). Hard delete, not idempotent: a second delete of the same id
 * returns 404. Resolves on 204; rejects with an ApiError-shaped error (`.status`) on
 * 401/404/500, mirroring the other functions in this file with no status special-casing.
 */
export async function deleteNotification(id) {
  await request(`/notifications/${id}`, { method: "DELETE", auth: true });
}
