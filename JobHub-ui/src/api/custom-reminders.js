// notification-service custom-reminders API client.
// Contract: api-contracts/openapi/notification-service.yaml (story #134, ADR 0013 / story #175)
// Paths:
//   POST   /notifications/custom-reminders                       -> createCustomReminder
//   GET    /notifications/custom-reminders                       -> listMyCustomReminders
//   GET    /notifications/custom-reminders?applicationId={id}    -> listCustomRemindersByApplication
//   GET    /notifications/custom-reminders/{id}                  -> getCustomReminder
//   PUT    /notifications/custom-reminders/{id}                  -> updateCustomReminder
//   DELETE /notifications/custom-reminders/{id}                  -> deleteCustomReminder
//
// ADR 0013 (story #175 / sub-issue #199): the formerly dedicated per-application path
// (`/applications/{id}/custom-reminders`) was removed. Both UI reverse proxies route the
// entire `/applications` prefix to application-service, which has no such route, so it
// always misrouted. The per-application filter is now the `applicationId` query parameter
// on the existing `/notifications/custom-reminders` collection above, which already proxies
// correctly through notification-service.
import { request } from "./client.js";

/**
 * POST /notifications/custom-reminders
 * @param {{ applicationId: string, title: string, triggerAtUtc: string, channels: string[], note?: string, stage?: string }} body
 * @returns {Promise<object>} CustomReminderResponse
 */
export async function createCustomReminder(body) {
  const { data } = await request("/notifications/custom-reminders", {
    method: "POST",
    auth: true,
    body,
  });
  return data;
}

/**
 * GET /notifications/custom-reminders/{id}
 * @param {string} id
 * @returns {Promise<object>} CustomReminderResponse
 */
export async function getCustomReminder(id) {
  const { data } = await request(`/notifications/custom-reminders/${id}`, {
    auth: true,
  });
  return data;
}

/**
 * PUT /notifications/custom-reminders/{id}
 * Partial-update: omitted fields retain current values.
 * @param {string} id
 * @param {{ title?: string, note?: string, triggerAtUtc?: string, channels?: string[], stage?: string }} patch
 * @returns {Promise<object>} CustomReminderResponse
 */
export async function updateCustomReminder(id, patch) {
  const { data } = await request(`/notifications/custom-reminders/${id}`, {
    method: "PUT",
    auth: true,
    body: patch,
  });
  return data;
}

/**
 * DELETE /notifications/custom-reminders/{id}
 * Soft-cancel: sets status to CANCELLED. Idempotent on already-CANCELLED.
 * Returns 409 if status is FIRED.
 * @param {string} id
 * @returns {Promise<void>}
 */
export async function deleteCustomReminder(id) {
  await request(`/notifications/custom-reminders/${id}`, {
    method: "DELETE",
    auth: true,
  });
}

/**
 * GET /notifications/custom-reminders
 * Default: SCHEDULED only, ascending by triggerAtUtc.
 * includeFired=true: all statuses, descending by triggerAtUtc.
 * @param {{ includeFired?: boolean }} [opts]
 * @returns {Promise<{ content: object[] }>} CustomReminderList
 */
export async function listMyCustomReminders({ includeFired = false } = {}) {
  const path = includeFired
    ? "/notifications/custom-reminders?includeFired=true"
    : "/notifications/custom-reminders";
  const { data } = await request(path, { auth: true });
  return data;
}

/**
 * GET /notifications/custom-reminders?applicationId={applicationId}
 * Same includeFired semantics as listMyCustomReminders, filtered to one application.
 * @param {string} applicationId
 * @param {{ includeFired?: boolean }} [opts]
 * @returns {Promise<{ content: object[] }>} CustomReminderList
 */
export async function listCustomRemindersByApplication(applicationId, { includeFired = false } = {}) {
  const base = `/notifications/custom-reminders?applicationId=${applicationId}`;
  const path = includeFired ? `${base}&includeFired=true` : base;
  const { data } = await request(path, { auth: true });
  return data;
}
