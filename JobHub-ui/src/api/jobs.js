// job-service (/jobs) — public job search/detail + per-user saved jobs and saved filters.
// Mirrors api-contracts/openapi/job-service.yaml.
import { request } from "./client.js";

/* ── Job search & detail (public) ── */

/**
 * Search job postings. All filters are optional and combinable; multi-value
 * filters (location/language/company/employmentType) are repeated query params.
 * Returns the JobSearchPage wrapper unwrapped to { items, total, page, totalPages }.
 *
 * @param {object} f
 * @param {string} [f.keyword]
 * @param {string[]} [f.location]        e.g. ["Spain", "Remote"]
 * @param {string[]} [f.language]        e.g. ["English"]
 * @param {string[]} [f.company]
 * @param {string[]} [f.employmentType]  full-time|part-time|contract|freelance|internship
 * @param {number} [f.compensationMin]
 * @param {number} [f.compensationMax]
 * @param {string} [f.postedWithin]      today|3d|week|month
 * @param {string} [f.sort]              newest|oldest|salary-desc|salary-asc
 * @param {number} [f.page=0]
 * @param {number} [f.size=20]
 */
export async function searchJobs(f = {}) {
  const params = new URLSearchParams();
  if (f.keyword) params.set("keyword", f.keyword);
  appendMulti(params, "location", f.location);
  appendMulti(params, "language", f.language);
  appendMulti(params, "company", f.company);
  appendMulti(params, "employmentType", f.employmentType);
  if (f.compensationMin != null) params.set("compensationMin", String(f.compensationMin));
  if (f.compensationMax != null) params.set("compensationMax", String(f.compensationMax));
  if (f.postedWithin) params.set("postedWithin", f.postedWithin);
  if (f.sort) params.set("sort", f.sort);
  params.set("page", String(f.page ?? 0));
  params.set("size", String(f.size ?? 20));

  const { data } = await request(`/jobs?${params.toString()}`);
  return unwrapPage(data);
}

export async function getJob(id) {
  const { data } = await request(`/jobs/${id}`);
  return data; // JobPostResponse
}

/**
 * Distinct filter values across the WHOLE job table (companies, locations,
 * languages, employment types) plus the overall compensation range — used to
 * populate the search filter dropdowns so they aren't limited to one page.
 * @returns {Promise<{ companies: {value,count}[], locations: {value,count}[],
 *   languages: {value,count}[], employmentTypes: {value,count}[],
 *   compensationMin: number|null, compensationMax: number|null }>}
 */
export async function getJobFacets() {
  const { data } = await request(`/jobs/facets`);
  return data || {};
}

/* ── Saved jobs (bookmarks, authenticated) ── */

/** @returns {Promise<{ items: object[], total: number, page: number, totalPages: number }>} */
export async function listSavedJobs({ page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  const { data } = await request(`/jobs/saved?${params.toString()}`, { auth: true });
  return unwrapPage(data); // content: SavedJobResponse[] ({ savedAt, job })
}

export async function saveJob(jobId) {
  await request(`/jobs/saved/${jobId}`, { method: "PUT", auth: true });
}

export async function unsaveJob(jobId) {
  await request(`/jobs/saved/${jobId}`, { method: "DELETE", auth: true });
}

/* ── Saved filter presets (max 5, authenticated) ── */

export async function listSavedFilters() {
  const { data } = await request(`/jobs/filters/saved`, { auth: true });
  return data || []; // SavedFilterResponse[]
}

export async function createSavedFilter({ name, filters }) {
  const { data } = await request(`/jobs/filters/saved`, {
    method: "POST",
    auth: true,
    body: { name, filters },
  });
  return data; // SavedFilterResponse
}

export async function updateSavedFilter(id, { name, filters } = {}) {
  const body = {};
  if (name !== undefined) body.name = name;
  if (filters !== undefined) body.filters = filters;
  const { data } = await request(`/jobs/filters/saved/${id}`, {
    method: "PATCH",
    auth: true,
    body,
  });
  return data; // SavedFilterResponse
}

export async function deleteSavedFilter(id) {
  await request(`/jobs/filters/saved/${id}`, { method: "DELETE", auth: true });
}

/* ── helpers ── */

function appendMulti(params, key, values) {
  if (!Array.isArray(values)) return;
  for (const v of values) {
    if (v != null && v !== "") params.append(key, String(v));
  }
}

function unwrapPage(data) {
  return {
    items: (data && data.content) || [],
    total: data && data.totalElements != null ? Number(data.totalElements) : 0,
    page: data && data.page != null ? Number(data.page) : 0,
    totalPages: data && data.totalPages != null ? Number(data.totalPages) : 0,
  };
}
