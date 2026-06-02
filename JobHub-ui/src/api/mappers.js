// Adapters from backend DTOs to the UI's (richer) view model.
//
// The job/application contracts now carry company, compensation, language,
// employment type, source and requirements, so most fields map directly.
// The few that remain UI-only are marked SYNTHETIC (see BACKEND_GAPS.md).

/* ── Company derivation ──
   JobPostResponse carries a `company` object ({ name, logoUrl }). When it is
   absent we fall back to deriving a name/key from the posting URL host. */
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

function slug(s) {
  return (s || "").toLowerCase().replace(/[^a-z0-9]/g, "").slice(0, 16) || "unknown";
}

function daysSince(iso) {
  if (!iso) return 0;
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return 0;
  return Math.max(0, Math.round((Date.now() - t) / 86400000));
}

const EMPLOYMENT_TYPE_LABEL = {
  "full-time": "Full-time",
  "part-time": "Part-time",
  contract: "Contract",
  freelance: "Freelance",
  internship: "Internship",
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

/**
 * Map a backend JobPostResponse to the UI job shape and register its company in
 * the provided companies map (so CoLogo can resolve it).
 */
export function jobFromApi(dto, companies) {
  const co = dto.company && dto.company.name
    ? { key: slug(dto.company.name), name: dto.company.name }
    : companyFromUrl(dto.url);
  const location = dto.location || "—";
  if (companies && !companies[co.key]) {
    companies[co.key] = {
      name: co.name,
      industry: "—",
      size: "—",
      hq: location,
      url: dto.url || "",
      logoUrl: (dto.company && dto.company.logoUrl) || "",
    };
  }
  const minK = compToThousands(dto.compensationMin);
  const maxK = compToThousands(dto.compensationMax);
  return {
    id: dto.id,
    co: co.key,
    title: dto.title,
    location,
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
    desc: dto.description || "No description provided.",
    reqs: Array.isArray(dto.requirements) ? dto.requirements : [],
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
    store.companies[co.key] = { name: co.name, industry: "—", size: "—", hq: location, url: summary.url || "" };
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
