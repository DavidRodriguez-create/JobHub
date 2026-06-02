// application-service (/applications) — all require a JWT (role "user").
// Mirrors api-contracts/openapi/application-service.yaml.
//
// Manual ("user-entered") job applications are created by passing `jobDetails`
// to POST /applications and edited via PATCH /applications/{id}/job — there is
// no separate /user-job-posts resource.
import { request } from "./client.js";

/* ── Applications ── */

// status one of: applied | screening | interviewing | offered | rejected | accepted | withdrawn | ghosted
export async function listApplications({ status, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams();
  if (status) params.set("status", status);
  params.set("page", String(page));
  params.set("size", String(size));
  const { data } = await request(`/applications?${params.toString()}`, { auth: true });
  // ApplicationPage: { content, page, size, totalElements, totalPages }
  return {
    items: (data && data.content) || [],
    total: data && data.totalElements != null ? Number(data.totalElements) : 0,
    page: data && data.page != null ? Number(data.page) : 0,
    totalPages: data && data.totalPages != null ? Number(data.totalPages) : 0,
  };
}

export async function getApplication(id) {
  const { data } = await request(`/applications/${id}`, { auth: true });
  return data; // ApplicationResponse (includes timeline on the detail view)
}

/**
 * Create an application. Supply EITHER `jobPostId` (a crawled job) OR
 * `jobDetails` (a manual entry) — exactly one.
 * @param {{ jobPostId?: string, jobDetails?: { title?, company?, url?, location? } }} payload
 */
export async function createApplication({ jobPostId, jobDetails } = {}) {
  const body = jobPostId ? { jobPostId } : { jobDetails };
  const { data } = await request("/applications", { method: "POST", auth: true, body });
  return data; // ApplicationResponse
}

/**
 * Partial update of editable fields (notes, appliedAt, contact, portalUrl, nextStep).
 * Status changes go through updateApplicationStatus; job details through updateApplicationJob.
 */
export async function updateApplication(id, { notes, appliedAt, contact, portalUrl, nextStep } = {}) {
  const body = {};
  if (notes !== undefined) body.notes = notes;
  if (appliedAt !== undefined) body.appliedAt = appliedAt;
  if (contact !== undefined) body.contact = contact;
  if (portalUrl !== undefined) body.portalUrl = portalUrl;
  if (nextStep !== undefined) body.nextStep = nextStep; // { label, date?, reminderAt? }
  const { data } = await request(`/applications/${id}`, { method: "PATCH", auth: true, body });
  return data;
}

export async function updateApplicationStatus(id, status) {
  const { data } = await request(`/applications/${id}/status`, {
    method: "PATCH",
    auth: true,
    body: { status },
  });
  return data;
}

// Edit the job details of a manual-entry application (409 for crawled-job applications).
export async function updateApplicationJob(id, { title, company, url, location } = {}) {
  const { data } = await request(`/applications/${id}/job`, {
    method: "PATCH",
    auth: true,
    body: { title, company, url, location },
  });
  return data;
}

export async function deleteApplication(id) {
  await request(`/applications/${id}`, { method: "DELETE", auth: true });
}

/**
 * Delete ALL of the user's applications. Requires a verification code obtained
 * from auth-service (POST /auth/account/verifications, action delete-all-applications).
 */
export async function deleteAllApplications({ verificationId, code }) {
  await request("/applications", {
    method: "DELETE",
    auth: true,
    body: { verificationId, code },
  });
}

/* ── Stats ── */

// Dashboard summary: { total, activeCount, byStatus, monthlyNew, responseRate, avgReplyDays, passThrough, nextDeadline }
export async function applicationStats() {
  const { data } = await request(`/applications/stats`, { auth: true });
  return data;
}

// Monthly trend chart: MonthlyCount[] = [{ year, month, byStatus }]
export async function applicationStatsHistory(months = 6) {
  const { data } = await request(`/applications/stats/history?months=${months}`, { auth: true });
  return data || [];
}
