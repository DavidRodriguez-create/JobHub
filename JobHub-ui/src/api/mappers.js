// Adapters from backend DTOs to the UI's (richer) view model.
//
// The job/application contracts now carry company, compensation, language,
// employment type, source and requirements, so most fields map directly.
// The few that remain UI-only are marked SYNTHETIC (see BACKEND_GAPS.md).

/* ── Company derivation ──
   JobPostResponse/JobPostSummary carry a `company` object per the frozen
   CompanyInfo contract (story #428, ADR 0023): { id, slug, name, logoUrl,
   website, industry, size, headquarters, description, tags, manuallyEdited,
   updatedAt }. `name` is always present when `company` is present; every
   other field is nullable and, when null, genuinely unknown (never "" or
   "-"). When `company` itself is absent we fall back to deriving a name/key
   from the posting URL host, exactly as before this story. */
export function companyFromUrl(url) {
  if (!url) return { key: "unknown", name: "Unknown" };
  let host;
  try { host = new URL(url).hostname; } catch { return { key: "unknown", name: "Unknown" }; }
  host = host.replace(/^www\./, "");
  // greenhouse/lever/ashby boards embed the real company in the subdomain
  const label = host.split(".")[0];
  const key = slug(label);
  const name = label.charAt(0).toUpperCase() + label.slice(1);
  return { key, name };
}

// Local, truncating derivation. AC-428-24: this is ONLY the fallback for the
// unresolved window (company.slug/company.id both null), not the backend's
// own slug rule (CompanySlug.of, job-service). Kept unchanged so the fallback
// path itself stays a regression-locked no-op.
function slug(s) {
  return (s || "").toLowerCase().replace(/[^a-z0-9]/g, "").slice(0, 16) || "unknown";
}

// AC-428-24: prefer the backend's own stable identity (`company.slug`, then
// `company.id`) as the companies-map key; fall back to the local truncating
// slug() derivation only when both are null (the AC-428-13 unresolved window).
function companyKey(company) {
  if (!company) return null;
  if (company.slug) return company.slug;
  if (company.id) return String(company.id);
  if (company.name) return slug(company.name);
  return null;
}

// Build the companies-map patch for a resolved `company` object straight from
// the contract: every field maps 1:1, null stays null (never synthesised as
// "-" or backfilled from the job's own location). AC-428-22/23.
function companyPatchFromDto(company, jobUrl) {
  return {
    name: company.name,
    logoUrl: company.logoUrl ?? null,
    website: company.website ?? null,
    industry: company.industry ?? null,
    size: company.size ?? null,
    hq: company.headquarters ?? null,
    description: company.description ?? null,
    tags: company.tags ?? null,
    slug: company.slug ?? null,
    id: company.id ?? null,
    manuallyEdited: company.manuallyEdited ?? null,
    updatedAt: company.updatedAt ?? null,
    url: jobUrl || "",
  };
}

// AC-428-25/26: upgrade-never-downgrade merge. A field present (non-null) on
// the incoming patch replaces what is known; a null/undefined field on the
// incoming patch (e.g. `description` on a GET /jobs list projection, per the
// projection rule) never erases an already-known richer value.
function mergeCompanyEntry(existing, patch) {
  if (!existing) return { ...patch };
  const merged = { ...existing };
  Object.keys(patch).forEach((k) => {
    const v = patch[k];
    if (v !== null && v !== undefined) merged[k] = v;
  });
  return merged;
}

function registerCompany(companies, key, patch) {
  if (!companies) return;
  companies[key] = mergeCompanyEntry(companies[key], patch);
}

function daysSince(iso) {
  if (!iso) return 0;
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return 0;
  return Math.max(0, Math.round((Date.now() - t) / 86400000));
}

export const EMPLOYMENT_TYPE_LABEL = {
  "full-time": "Full-time",
  "part-time": "Part-time",
  contract: "Contract",
  freelance: "Freelance",
  internship: "Internship",
};

export const CAREER_LEVEL_LABEL = {
  internship: "Internship",
  junior: "Junior",
  mid: "Mid",
  senior: "Senior",
  lead: "Lead",
  principal: "Principal",
  manager: "Manager",
  director: "Director",
};

// Compensation is stored as raw annual amounts; the UI works in thousands ("k").
function compToThousands(v) {
  return v == null ? null : Math.round(Number(v) / 1000);
}
function formatComp(minK, maxK) {
  if (minK != null && maxK != null) return `€${minK}k–€${maxK}k`;
  if (minK != null) return `€${minK}k+`;
  if (maxK != null) return `up to €${maxK}k`;
  return "—";
}
function countryFromLocation(location) {
  if (!location) return "—";
  if (/remote/i.test(location)) return "Remote";
  const parts = location.split(",").map((s) => s.trim()).filter(Boolean);
  return parts.length ? parts[parts.length - 1] : location;
}

/* ── Multiple locations (story #1) ──
   JobPostResponse.locations is the full opening set (JobLocation[]), primary
   first. Compose each entry's display string the same way the backend composes
   `location`: "city, country", the non-blank part alone, or "Remote". Absent/
   empty stays an empty array — never a single blank/undefined entry. */
export function composeLocation(loc) {
  if (!loc) return "";
  const country = (loc.country || "").trim();
  const city = (loc.city || "").trim();
  if (city && country) return `${city}, ${country}`;
  return city || country || "";
}

function locationsFromApi(dto) {
  if (!Array.isArray(dto.locations)) return [];
  return dto.locations
    .filter((l) => l && (l.country || l.city))
    .map((l) => ({ country: l.country ?? null, city: l.city ?? null, primary: !!l.primary }));
}

/**
 * Map a backend JobPostResponse to the UI job shape and register its company in
 * the provided companies map (so CoLogo can resolve it).
 */
export function jobFromApi(dto, companies) {
  const co = dto.company && dto.company.name
    ? { key: companyKey(dto.company) || slug(dto.company.name), name: dto.company.name }
    : companyFromUrl(dto.url);
  const location = dto.location || "—";
  const patch = dto.company
    ? companyPatchFromDto(dto.company, dto.url)
    : { name: co.name, url: dto.url || "", logoUrl: null };
  registerCompany(companies, co.key, patch);
  const minK = compToThousands(dto.compensationMin);
  const maxK = compToThousands(dto.compensationMax);
  // Story #330: GET /jobs now returns a slim JobPostSummary (no description/requirements
  // keys at all); GET /jobs/{id} and SavedJobResponse.job still return the full
  // JobPostResponse. `hasFullDetail` tells the drawer whether the heavy fields were ever
  // fetched, so "not yet loaded" (desc/reqs undefined) never collapses into the same value
  // as "loaded and genuinely empty" (desc: "", reqs: []).
  const hasFullDetail = Object.prototype.hasOwnProperty.call(dto, "description")
    || Object.prototype.hasOwnProperty.call(dto, "requirements");
  return {
    id: dto.id,
    co: co.key,
    title: dto.title,
    location,
    locations: locationsFromApi(dto),
    comp: formatComp(minK, maxK),
    compMin: minK ?? 0,          // 0 / 999 keep range filters from excluding unpriced jobs
    compMax: maxK ?? 999,
    type: EMPLOYMENT_TYPE_LABEL[dto.employmentType] || "Full-time",
    postedDays: daysSince(dto.firstSeenAt),
    source: dto.source || "Crawled",
    remote: /remote/i.test(location),
    tags: [],                    // SYNTHETIC — contract has no free-form tags
    country: countryFromLocation(location),
    language: (Array.isArray(dto.language) && dto.language[0]) || "English",
    hasFullDetail,
    desc: hasFullDetail ? (dto.description || "") : undefined,
    reqs: hasFullDetail ? (Array.isArray(dto.requirements) ? dto.requirements : []) : undefined,
    url: dto.url,
  };
}

/** Map a SavedJobResponse ({ savedAt, job }) to the UI job shape. */
export function savedJobFromApi(dto, companies) {
  return jobFromApi(dto.job, companies);
}

/* ── Application status mapping ──
   Backend enum: applied | screening | interviewing | offered | rejected | accepted | withdrawn | ghosted
   UI statuses:  applied | screening | interview | offer | accepted | rejected | ghosted | withdrawn
   Only the interviewing↔interview and offered↔offer names differ; everything
   else (including screening and ghosted) round-trips 1:1. */
export const STATUS_API_TO_UI = {
  applied: "applied",
  screening: "screening",
  interviewing: "interview",
  offered: "offer",
  rejected: "rejected",
  accepted: "accepted",
  withdrawn: "withdrawn",
  ghosted: "ghosted",
};

export const STATUS_UI_TO_API = {
  applied: "applied",
  screening: "screening",
  interview: "interviewing",
  offer: "offered",
  rejected: "rejected",
  accepted: "accepted",
  withdrawn: "withdrawn",
  ghosted: "ghosted",
};

export function statusToUi(apiStatus) {
  return STATUS_API_TO_UI[apiStatus] || "applied";
}
export function statusToApi(uiStatus) {
  return STATUS_UI_TO_API[uiStatus] || null;
}

/* ── Account display ──
   Derive display name + initials from an AccountResponse ({ firstName, lastName, email }).
   Returns empty strings for a null account — never a hard-coded demo identity. */
export function accountName(a) {
  if (!a) return "";
  const full = [a.firstName, a.lastName].filter(Boolean).join(" ").trim();
  return full || a.email || "";
}

export function accountInitials(a) {
  if (!a) return "";
  const f = (a.firstName || "").trim();
  const l = (a.lastName || "").trim();
  if (f || l) return ((f[0] || "") + (l[0] || "")).toUpperCase();
  const e = (a.email || "").trim();
  return e ? e[0].toUpperCase() : "";
}

/* ── Application mapping ──
   ApplicationResponse → the UI's application view model. The embedded job is a
   JobSummary ({title, company, location, url}); we register a matching job + company
   in the store so DATA.byId(app.jobId) / DATA.coOf(job.co) resolve, exactly like a
   crawled job. `apiId` carries the real application UUID for update/delete calls. */
export function appFromApi(dto, store) {
  if (!dto) return null;
  const summary = dto.job || {};
  const location = summary.location || "—";
  const co = summary.company
    ? { key: slug(summary.company), name: summary.company }
    : companyFromUrl(summary.url);
  // For crawled jobs the response carries the originating job-service id; use it so the
  // application maps back to its job-search row (marks it "Applied", blocks re-applying).
  // Manual entries have no job post — fall back to an id derived from the application.
  const jobId = dto.jobPostId ? String(dto.jobPostId) : "app-job-" + dto.id;

  if (store && store.companies && !store.companies[co.key]) {
    store.companies[co.key] = { name: co.name, industry: null, size: null, hq: null, url: summary.url || "" };
  }
  if (store && Array.isArray(store.jobs) && !store.jobs.some((j) => j.id === jobId)) {
    store.jobs.push({
      id: jobId, co: co.key, title: summary.title || "Untitled role", location,
      comp: "—", compMin: 0, compMax: 999, type: "Full-time", postedDays: 0,
      source: "—", remote: /remote/i.test(location), tags: [],
      country: countryFromLocation(location), language: "English",
      desc: "", reqs: [], url: summary.url || "",
    });
  }

  return {
    id: dto.id,
    apiId: dto.id,
    jobId,
    status: statusToUi(dto.status),
    appliedOn: dateOnly(dto.appliedAt),
    lastUpdate: dateOnly(dto.updatedAt) || dateOnly(dto.appliedAt),
    nextStep: nextStepLabel(dto.nextStep),
    notes: dto.notes || "",
    contact: dto.contact || "",
    postUrl: summary.url || "",
    portalUrl: dto.portalUrl ? String(dto.portalUrl) : "",
    timeline: timelineFromApi(dto.timeline),
  };
}

function dateOnly(iso) {
  return iso ? String(iso).slice(0, 10) : "";
}

function nextStepLabel(ns) {
  if (!ns) return "—";
  const parts = [];
  if (ns.label) parts.push(ns.label);
  if (ns.date) parts.push(dateOnly(ns.date));
  return parts.length ? parts.join(" · ") : "—";
}

const STATUS_PHRASE = {
  applied: "Applied", screening: "Screening", interviewing: "Interviewing",
  offered: "Offer received", rejected: "Rejected", accepted: "Accepted",
  withdrawn: "Withdrawn", ghosted: "Ghosted",
};

function timelineFromApi(timeline) {
  if (!Array.isArray(timeline) || timeline.length === 0) return [];
  // newest first — the UI marks index 0 as the latest ("done") event.
  return [...timeline]
    .sort((a, b) => String(b.occurredAt).localeCompare(String(a.occurredAt)))
    .map((t) => ({ date: dateOnly(t.occurredAt), what: STATUS_PHRASE[t.status] || t.status }));
}

/* ── Saved filter presets (story #523) ──
   UI preset state -> FilterValues (request body) and back. Presets persist search
   dimensions only: keyword, company, location, employmentType, careerLevel, language,
   postedWithin. `compensationMin`/`compensationMax`/`sort` are never written, and are
   ignored when reading a preset back (ADR 0030 Decision 7). `POSTED_UI_TO_API` also backs
   JobSearch.jsx's own search-request builder, so the search request and the preset writer
   cannot drift apart (design note section 4.3). */
export const POSTED_UI_TO_API = { any: undefined, today: "today", "3days": "3d", week: "week", month: "month" };
export const POSTED_API_TO_UI = { today: "today", "3d": "3days", week: "week", month: "month" };

/** UI preset state -> FilterValues (request body). Allow-list-shaped: only the seven
 * persisted dimensions are ever read from `state`; comp/sort never leak through even if
 * present on the input object. */
export function filterValuesFromPreset(state = {}) {
  const out = {};
  const keyword = (state.query || "").trim();
  if (keyword) out.keyword = state.query;
  if (Array.isArray(state.companies) && state.companies.length) out.company = state.companies;
  if (Array.isArray(state.locations) && state.locations.length) out.location = state.locations;
  if (Array.isArray(state.employmentTypes) && state.employmentTypes.length) out.employmentType = state.employmentTypes;
  if (Array.isArray(state.careerLevels) && state.careerLevels.length) out.careerLevel = state.careerLevels;
  if (state.language && state.language !== "all") out.language = [state.language];
  const postedWithin = POSTED_UI_TO_API[state.posted];
  if (postedWithin !== undefined) out.postedWithin = postedWithin;
  return out;
}

/** FilterValues -> UI preset state. Tolerates a null/absent/malformed `filters` object;
 * every field defaults so the caller never sees `undefined`. Ignores compensationMin/
 * compensationMax/sort even when present on the input (ADR 0030 Decision 7 / AC-523-36). */
export function presetFromFilterValues(filters) {
  const f = filters || {};
  const arr = (v) => (Array.isArray(v) ? v : []);
  const language = arr(f.language);
  return {
    query: f.keyword || "",
    companies: arr(f.company),
    locations: arr(f.location),
    employmentTypes: arr(f.employmentType),
    careerLevels: arr(f.careerLevel),
    language: language.length ? language[0] : "all",
    posted: POSTED_API_TO_UI[f.postedWithin] ?? "any",
  };
}

/** SavedFilterResponse -> { id, name, state }, the shape SavedFiltersDropdown consumes. */
export function savedFilterFromApi(dto) {
  return {
    id: dto.id,
    name: dto.name,
    state: presetFromFilterValues(dto.filters),
  };
}
