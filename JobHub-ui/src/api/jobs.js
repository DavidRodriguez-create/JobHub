// job-service (/jobs) — public job search/detail + per-user saved jobs and saved filters.
// Mirrors api-contracts/openapi/job-service.yaml.
import { request } from "./client.js";
import { stableKey, cachedFetch, prefetch, peek } from "./query-cache.js";

/* ── Job search & detail (public) ── */

/**
 * Build the URLSearchParams for GET /jobs from a filters object. Hoisted out of
 * searchJobs so the same params object backs both the outbound request and the
 * query-cache key (story #329: client-side query cache).
 */
function buildSearchParams(f = {}) {
  const params = new URLSearchParams();
  if (f.keyword) params.set("keyword", f.keyword);
  appendMulti(params, "location", f.location);
  appendMulti(params, "language", f.language);
  appendMulti(params, "company", f.company);
  appendMulti(params, "employmentType", f.employmentType);
  appendMulti(params, "careerLevel", f.careerLevel);
  if (f.compensationMin != null) params.set("compensationMin", String(f.compensationMin));
  if (f.compensationMax != null) params.set("compensationMax", String(f.compensationMax));
  if (f.postedWithin) params.set("postedWithin", f.postedWithin);
  if (f.sort) params.set("sort", f.sort);
  params.set("page", String(f.page ?? 0));
  params.set("size", String(f.size ?? 20));
  return params;
}

/**
 * Search job postings. All filters are optional and combinable; multi-value
 * filters (location/language/company/employmentType) are repeated query params.
 * Returns the JobSearchPage wrapper unwrapped to { items, total, page, totalPages }.
 *
 * Cache-first (story #329): an exact-match filter/page/size/sort combination already
 * fetched in this session resolves from the in-memory query cache with no network
 * round-trip. A failed fetch is never cached (see api/query-cache.js).
 *
 * @param {object} f
 * @param {string} [f.keyword]
 * @param {string[]} [f.location]        e.g. ["Spain", "Remote"]
 * @param {string[]} [f.language]        e.g. ["English"]
 * @param {string[]} [f.company]
 * @param {string[]} [f.employmentType]  full-time|part-time|contract|freelance|internship
 * @param {string[]} [f.careerLevel]     internship|junior|mid|senior|lead|principal|manager|director
 * @param {number} [f.compensationMin]
 * @param {number} [f.compensationMax]
 * @param {string} [f.postedWithin]      today|3d|week|month
 * @param {string} [f.sort]              newest|oldest|salary-desc|salary-asc
 * @param {number} [f.page=0]
 * @param {number} [f.size=20]
 */
export async function searchJobs(f = {}) {
  const params = buildSearchParams(f);
  const key = stableKey("jobs:search", params);
  return cachedFetch(key, async () => {
    const { data } = await request(`/jobs?${params.toString()}`);
    return unwrapPage(data);
  });
}

/**
 * Synchronous cache peek mirroring searchJobs' cache key. Returns the exact cached
 * page (same { items, total, page, totalPages } shape searchJobs resolves) or
 * undefined on a miss. Never fetches. Used by JobSearchScreen to seed the list
 * instantly on a cache hit (keep-previous-data / no-spinner UX, story #329).
 * @param {object} [f={}]
 */
export function peekSearch(f = {}) {
  return peek(stableKey("jobs:search", buildSearchParams(f)));
}

/**
 * Fire-and-forget warm-up of a search page (typically page+1 after a settled search)
 * so a later searchJobs() call for the identical combination is a cache hit. Never
 * throws, never surfaces an error to the caller (story #329).
 * @param {object} [f={}]
 */
export function prefetchSearch(f = {}) {
  const params = buildSearchParams(f);
  const key = stableKey("jobs:search", params);
  prefetch(key, async () => {
    const { data } = await request(`/jobs?${params.toString()}`);
    return unwrapPage(data);
  });
}

export async function getJob(id) {
  const { data } = await request(`/jobs/${id}`);
  return data; // JobPostResponse
}

/**
 * Build the URLSearchParams for GET /jobs/facets from an active-filters object.
 * Hoisted out of getJobFacets so the same params object backs both the outbound
 * request and the query-cache key (story #329). Deliberately omits sort/page/size,
 * the contract forbids them on /jobs/facets, so their key naturally ignores those
 * dimensions (distinct from the jobs:search key, which includes them).
 */
function buildFacetsParams(f = {}) {
  const params = new URLSearchParams();
  if (f.keyword) params.set("keyword", f.keyword);
  appendMulti(params, "location", f.location);
  appendMulti(params, "language", f.language);
  appendMulti(params, "company", f.company);
  appendMulti(params, "employmentType", f.employmentType);
  appendMulti(params, "careerLevel", f.careerLevel);
  if (f.compensationMin != null) params.set("compensationMin", String(f.compensationMin));
  if (f.compensationMax != null) params.set("compensationMax", String(f.compensationMax));
  if (f.postedWithin) params.set("postedWithin", f.postedWithin);
  // NOTE: sort, page, size are intentionally excluded (contract forbids them on /jobs/facets)
  return params;
}

/**
 * Filter-aware (drill-down) facet counts for the job filter controls.
 * Mirrors the frozen contract GET /jobs/facets.
 *
 * Active filters are forwarded as query params so each facet group is computed
 * against all filters except its own dimension ("exclude own dimension" semantics).
 * `sort`, `page`, and `size` are intentionally omitted — they do not affect
 * aggregate counts.
 *
 * Called with no args (or an empty object) it returns the full table-wide facets,
 * preserving backwards-compatibility with callers that do not pass filters yet.
 *
 * Cache-first (story #329): identical active filters resolve from the in-memory
 * query cache with no network round-trip. Uses its own "jobs:facets" namespace, so
 * it never shares an entry with searchJobs even for byte-identical filter values.
 * A failed fetch is never cached.
 *
 * @param {object} [f={}]
 * @param {string} [f.keyword]
 * @param {string[]} [f.location]        e.g. ["Spain", "Remote"]
 * @param {string[]} [f.language]        e.g. ["English"]
 * @param {string[]} [f.company]
 * @param {string[]} [f.employmentType]  full-time|part-time|contract|freelance|internship
 * @param {string[]} [f.careerLevel]     internship|junior|mid|senior|lead|principal|manager|director
 * @param {number}   [f.compensationMin]
 * @param {number}   [f.compensationMax]
 * @param {string}   [f.postedWithin]    today|3d|week|month
 * @returns {Promise<{ companies: {value,count}[], locations: {value,count}[],
 *   languages: {value,count}[], employmentTypes: {value,count}[],
 *   careerLevels: {value,count}[],
 *   compensationMin: number|null, compensationMax: number|null }>}
 */
export async function getJobFacets(f = {}) {
  const params = buildFacetsParams(f);
  const key = stableKey("jobs:facets", params);
  return cachedFetch(key, async () => {
    const qs = params.toString();
    const { data } = await request(qs ? `/jobs/facets?${qs}` : `/jobs/facets`);
    return data || {};
  });
}

/**
 * Synchronous cache peek mirroring getJobFacets' cache key. Returns the exact
 * cached facets object or undefined on a miss. Never fetches.
 * @param {object} [f={}]
 */
export function peekFacets(f = {}) {
  return peek(stableKey("jobs:facets", buildFacetsParams(f)));
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

/* ── Admin: trigger crawl / enrichment (admin JWT required) ── */

/**
 * GET /jobs/admin/triggers/status
 * Returns { triggerEnabled, twoFactorRequired, crawl?, enrichment?, lastCrawlRun?,
 * lastEnrichmentRun? } (TriggerStatusResponse).
 * `twoFactorRequired` is per-caller (story #384): it reflects whether *this* admin
 * has 2FA enabled on their own account, resolved live from auth-service.
 * `lastCrawlRun`/`lastEnrichmentRun` (story #398, ADR 0032) are each a
 * TriggerLastRun | null: the most recent FINISHED run of that kind, whatever
 * started it (`origin` = scheduled | manual), distinct from `crawl`/`enrichment`
 * which describe the most recent *request* and may still be in flight.
 * Requires a Bearer token with the `admin` group.
 */
export async function getAdminTriggerStatus() {
  const { data } = await request("/jobs/admin/triggers/status", { auth: true });
  return data; // TriggerStatusResponse
}

/**
 * POST /jobs/admin/triggers
 * Records a trigger request for the given kind ("crawl" | "enrichment").
 * When twoFactorRequired=true, pass the admin's own current TOTP code (6 digits)
 * or an unused backup code (8 alphanumeric characters) as `code`. When
 * twoFactorRequired=false, omit `code` entirely (story #384; there is no
 * verificationId anymore, the emailed request-code flow is removed).
 * Returns TriggerResponse (202) or throws ApiError (400/403/409/422/429/500).
 *
 * @param {{ kind: string, code?: string }} params
 */
export async function triggerAdminPass({ kind, code } = {}) {
  const body = { kind };
  if (code != null) body.code = code;
  const { data } = await request("/jobs/admin/triggers", {
    method: "POST",
    auth: true,
    body,
  });
  return data; // TriggerResponse
}

/**
 * POST /jobs/admin/triggers/{kind}/cancel
 * Cancels the active trigger request for the given kind ("crawl" | "enrichment").
 * - If the active row is `queued`, it transitions immediately to `cancelled`.
 * - If the active row is `running`, it transitions to `cancel_requested`
 *   (finalized to `cancelled` later by crawler-service).
 * Returns TriggerResponse (200) or throws ApiError (401/403/409/500).
 *
 * @param {string} kind "crawl" | "enrichment"
 */
export async function cancelAdminTrigger(kind) {
  const { data } = await request(`/jobs/admin/triggers/${kind}/cancel`, {
    method: "POST",
    auth: true,
  });
  return data; // TriggerResponse
}

/* ── Admin: company enrichment (admin JWT required, story #430) ── */

/**
 * GET /jobs/admin/companies
 * Browse stored companies for the admin enrichment screen. Every entry is the FULL
 * CompanyInfo projection (description included), unlike the public GET /jobs list.
 * Total match count (independent of page size) comes from X-Total-Count.
 *
 * @param {object} [f={}]
 * @param {string} [f.q]                     case-insensitive name substring filter
 * @param {boolean} [f.manuallyEdited]        true = curated only, false = enrichment backlog
 * @param {string} [f.sort]                   name-asc|name-desc|updated-desc|updated-asc
 * @param {number} [f.page=0]
 * @param {number} [f.size=20]
 * @returns {Promise<{ items: object[], total: number }>}
 */
export async function listAdminCompanies({ q, manuallyEdited, sort, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (manuallyEdited != null) params.set("manuallyEdited", String(manuallyEdited));
  if (sort) params.set("sort", sort);
  params.set("page", String(page));
  params.set("size", String(size));
  const { data, total } = await request(`/jobs/admin/companies?${params.toString()}`, { auth: true });
  return { items: Array.isArray(data) ? data : [], total: total ?? 0 };
}

/**
 * GET /jobs/admin/companies/{id}
 * @param {string} id
 * @returns {Promise<object>} CompanyInfo, full projection
 */
export async function getAdminCompany(id) {
  const { data } = await request(`/jobs/admin/companies/${id}`, { auth: true });
  return data; // CompanyInfo
}

/**
 * PUT /jobs/admin/companies/{id}
 * Full-replace semantics (ADR 0025 D4, story #430): the request body always carries
 * all seven editable fields. A field missing from `fields` (undefined) is sent as
 * `null`, which CLEARS the stored value (there is no partial-update call), so a
 * caller (the edit form) that wants to keep a value must echo it back explicitly.
 * `id`/`slug`/`name` are not part of the editable set and are never sent here.
 *
 * @param {string} id
 * @param {object} [fields={}]
 * @param {string|null} [fields.website]
 * @param {string|null} [fields.industry]
 * @param {string|null} [fields.size]
 * @param {string|null} [fields.headquarters]
 * @param {string|null} [fields.description]
 * @param {string[]|null} [fields.tags]        an empty/absent array clears to null (never [])
 * @param {string|null} [fields.logoUrl]
 * @returns {Promise<object>} the updated CompanyInfo, manuallyEdited=true
 */
export async function updateAdminCompany(id, fields = {}) {
  const body = {
    website: fields.website ?? null,
    industry: fields.industry ?? null,
    size: fields.size ?? null,
    headquarters: fields.headquarters ?? null,
    description: fields.description ?? null,
    tags: Array.isArray(fields.tags) && fields.tags.length ? fields.tags : null,
    logoUrl: fields.logoUrl ?? null,
  };
  const { data } = await request(`/jobs/admin/companies/${id}`, { method: "PUT", auth: true, body });
  return data; // CompanyInfo
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
