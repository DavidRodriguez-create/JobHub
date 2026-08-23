import React from "react";
import Icon from "../components/Icon.jsx";
import DATA from "../data/mockData.js";
import * as UI from "../components/ui.jsx";
import RichText from "../components/RichText.jsx";
import WritingLoader from "../components/WritingLoader.jsx";
import { MultiSelect, SavedFiltersDropdown } from "../components/FilterComponents.jsx";
import { searchJobs, getJobFacets, peekSearch, prefetchSearch, getJob, listSavedFilters, createSavedFilter, deleteSavedFilter } from "../api/jobs.js";
import { jobFromApi, composeLocation, EMPLOYMENT_TYPE_LABEL, CAREER_LEVEL_LABEL, POSTED_UI_TO_API, filterValuesFromPreset, savedFilterFromApi } from "../api/mappers.js";
import { ApiError } from "../api/client.js";
import { USE_API } from "../api/config.js";

// JobHub — Job Search screen + Job Detail drawer
const { Button, Input, Field, StatusPill, CoLogo, Card, Empty, JobRow } = UI;

// UI filter value → GET /jobs query param. POSTED_UI_TO_API (imported from mappers.js,
// story #523) backs both this screen's search-request builder and the saved-filter
// preset writer, so the two cannot drift apart.
const SORT_MAP = { newest: "newest", salary: "salary-desc" };
const PAGE_SIZES = [10, 25, 50, 100];
const DEFAULT_SIZE = 25;

// Ensure a crawled URL is absolute so it opens as an external link (not relative to the SPA).
function normalizeUrl(raw) {
  const s = String(raw || "").trim();
  if (!s) return null;
  return /^[a-z][a-z0-9+.-]*:\/\//i.test(s) ? s : "https://" + s;
}

/* ─── Job detail hydration (story #330) ──
   GET /jobs (list) now returns a slim JobPostSummary with no description/requirements:
   jobFromApi marks that with hasFullDetail=false. The drawer opens instantly with the
   list-known header fields, then fetches the full posting via getJob(id) to fill in
   "About the role"/"Requirements". A job that already carries full detail (e.g. reached
   from Saved Jobs, hasFullDetail=true) never fetches and never shows a loading state
   (AC-14). The ignore-flag guards against a stale in-flight fetch (from a previous
   mount/job) clobbering a later one (AC-12, AC-13). */
function useJobDetail(job) {
  const alreadyLoaded = !!job.hasFullDetail;
  const [detail, setDetail] = React.useState(() =>
    alreadyLoaded
      ? { status: "ready", description: job.desc, requirements: job.reqs }
      : { status: "loading" }
  );

  React.useEffect(() => {
    let ignore = false;
    if (alreadyLoaded) {
      setDetail({ status: "ready", description: job.desc, requirements: job.reqs });
      return () => { ignore = true; };
    }
    setDetail({ status: "loading" });
    getJob(job.id)
      .then((dto) => {
        if (ignore) return;
        setDetail({
          status: "ready",
          description: (dto && dto.description) || "",
          requirements: Array.isArray(dto && dto.requirements) ? dto.requirements : [],
        });
      })
      .catch(() => {
        if (ignore) return;
        setDetail({ status: "error" });
      });
    return () => { ignore = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [job.id, alreadyLoaded]);

  return detail;
}

/* ─── Job Detail Drawer ─── */
function JobDetailDrawer({ job, onClose, closing, onApply, onSave, isSaved, isApplied, authed, onOpenApplyProfile }) {
  const c = DATA.coOf(job.co);
  const vw = useViewport();
  const isWide = vw >= 1280;
  const detail = useJobDetail(job);
  React.useEffect(() => {
    const h = (e) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [onClose]);

  return (
    <>
      <div className={"job-drawer-backdrop" + (closing ? " job-drawer-backdrop--closing" : "")} onClick={onClose} />
      <div className={"job-drawer" + (isWide ? " job-drawer--wide" : "") + (closing ? " job-drawer--closing" : "")}>
        <div className="job-drawer-head">
          <CoLogo co={job.co} size="lg" />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 18, fontWeight: 600, color: "var(--color-ink)", letterSpacing: "-0.018em" }}>{job.title}</div>
            <div style={{ display: "flex", gap: 8, fontSize: 13, color: "var(--color-ink-3)", alignItems: "center", marginTop: 4, flexWrap: "wrap" }}>
              <span style={{ color: "var(--color-ink-2)", fontWeight: 500 }}>{c.name}</span>
              <span className="dot-sep" />
              <span>{job.location}</span>
              <span className="dot-sep" />
              <span className="mono">{job.comp}</span>
            </div>
            <div style={{ display: "flex", gap: 6, marginTop: 8, flexWrap: "wrap" }}>
              {job.tags.map((t) => (
                <span key={t} style={{ padding: "2px 8px", borderRadius: 4, fontSize: 11, fontWeight: 500,
                  background: "var(--color-surface-2)", color: "var(--color-ink-2)", border: "1px solid var(--color-border)" }}>{t}</span>
              ))}
              <span style={{ padding: "2px 8px", borderRadius: 4, fontSize: 11, fontWeight: 500,
                background: "var(--color-surface-2)", color: "var(--color-ink-2)", border: "1px solid var(--color-border)" }}>{job.type}</span>
              {job.remote && <span style={{ padding: "2px 8px", borderRadius: 4, fontSize: 11, fontWeight: 500,
                background: "var(--color-brand-50)", color: "var(--color-brand-700)", border: "1px solid var(--color-brand-200)" }}>Remote</span>}
            </div>
          </div>
          <Button variant="ghost" size="sm" icon="x" onClick={onClose} aria-label="Close" />
        </div>

        <div className="job-drawer-body">
          <JobLocationsSection job={job} />
          <div style={{ marginBottom: 24 }}>
            <h4 style={{ fontSize: 14, fontWeight: 600, marginBottom: 8, color: "var(--color-ink)" }}>About the role</h4>
            {detail.status === "loading" && (
              <div data-testid="job-detail-loading">
                <WritingLoader label="Loading the full job description…" />
              </div>
            )}
            {detail.status === "error" && (
              <p data-testid="job-detail-error" style={{ fontSize: 13, color: "var(--color-ink-3)" }}>
                We couldn't load the full description for this posting. Try closing and reopening it.
              </p>
            )}
            {detail.status === "ready" && <RichText text={detail.description} />}
          </div>
          <div style={{ marginBottom: 24 }}>
            <h4 style={{ fontSize: 14, fontWeight: 600, marginBottom: 8, color: "var(--color-ink)" }}>Requirements</h4>
            {detail.status === "loading" && (
              <div data-testid="job-detail-loading">
                <WritingLoader label="Loading requirements…" />
              </div>
            )}
            {detail.status === "error" && (
              <p data-testid="job-detail-error" style={{ fontSize: 13, color: "var(--color-ink-3)" }}>
                We couldn't load the requirements for this posting. Try closing and reopening it.
              </p>
            )}
            {detail.status === "ready" && (
              detail.requirements.length ? (
                <ul style={{ margin: 0, paddingLeft: 18, display: "flex", flexDirection: "column", gap: 6 }}>
                  {detail.requirements.map((r, i) => (
                    <li key={i} style={{ fontSize: 13, color: "var(--color-ink-2)", lineHeight: 1.5 }}>{r}</li>
                  ))}
                </ul>
              ) : (
                <p data-testid="job-detail-requirements-empty" style={{ fontSize: 13, color: "var(--color-ink-3)" }}>
                  No requirements listed.
                </p>
              )
            )}
          </div>
          <div style={{ padding: 16, background: "var(--color-bg)", borderRadius: 10, border: "1px solid var(--color-border)" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12 }}>
              <CoLogo co={job.co} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 14, fontWeight: 600, color: "var(--color-ink)" }}>{c.name}</div>
                {c.industry != null && (
                  <div data-testid="company-industry" style={{ fontSize: 12, color: "var(--color-ink-3)" }}>{c.industry}</div>
                )}
              </div>
              {/* Company website (story #486): external link, opened without exposing
                  window.opener back to this window (AC-486-04). Omitted entirely when
                  the company has no known website (AC-486-05). */}
              {c.website && (
                <a href={normalizeUrl(c.website)} target="_blank" rel="noopener noreferrer"
                  data-testid="company-website-row"
                  style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 12, fontWeight: 500,
                    color: "var(--color-brand-600)", textDecoration: "none", whiteSpace: "nowrap" }}>
                  Website
                  <Icon name="external-link" size={12} />
                </a>
              )}
            </div>
            {/* Company description (story #486): short prose from the company record.
                Omitted entirely when unknown (AC-486-07) : this must never reuse the
                job's own "About the role" empty-state copy, since a null here is often
                just the JobPostSummary list-projection artefact, not a genuine unknown. */}
            {c.description && (
              <p data-testid="company-description" style={{ fontSize: 12, color: "var(--color-ink-2)", lineHeight: 1.5,
                margin: "0 0 12px", whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
                {c.description}
              </p>
            )}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
              <InfoKV k="Size" v={c.size} testId="company-size-row" />
              <InfoKV k="HQ" v={c.hq} testId="company-hq-row" />
              <InfoKV k="Source" v={job.source} />
              <InfoKV k="Posted" v={UI.postedLabel(job.postedDays)} />
              <InfoKV k="Language" v={job.language || "—"} />
            </div>
            {/* Company tags (story #430): sourced from company.tags exclusively (real,
                admin-curated data), never from the drawer head's synthetic job.tags chip
                row above. Rendered only when non-null/non-empty (AC-430-29/30). */}
            {Array.isArray(c.tags) && c.tags.length > 0 && (
              <div data-testid="company-tags-row" style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 10 }}>
                {c.tags.map((t) => (
                  <span key={t} style={{ padding: "2px 8px", borderRadius: 4, fontSize: 11, fontWeight: 500,
                    background: "var(--color-surface-2)", color: "var(--color-ink-2)", border: "1px solid var(--color-border)" }}>{t}</span>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="job-drawer-foot">
          <Button variant="ghost" icon="bookmark"
            onClick={() => onSave(job)}
            style={isSaved ? { color: "var(--color-brand-600)" } : {}}>
            {isSaved ? "Saved" : "Save job"}
          </Button>
          {onOpenApplyProfile && (
            <Button variant="ghost" icon="copy" onClick={onOpenApplyProfile} data-testid="apply-profile-trigger-detail">
              Apply profile
            </Button>
          )}
          {job.url && (
            <Button variant="ghost" icon="external-link"
              onClick={() => window.open(normalizeUrl(job.url), "_blank", "noopener,noreferrer")}>
              View posting
            </Button>
          )}
          <div style={{ flex: 1 }} />
          {isApplied ? (
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <StatusPill status="applied" />
              <span style={{ fontSize: 12, color: "var(--color-ink-3)" }}>You applied to this job</span>
            </div>
          ) : (
            <Button variant="primary" icon="send" onClick={() => onApply(job)}>Apply now</Button>
          )}
        </div>
      </div>
    </>
  );
}

/* ─── Job locations (story #1, #293): every opening, primary first, no tooltip-only display ─── */
function JobLocationsSection({ job }) {
  const locations = Array.isArray(job.locations) ? job.locations : [];
  if (locations.length === 0) return null;
  return (
    <div style={{ marginBottom: 24 }}>
      <h4 style={{ fontSize: 14, fontWeight: 600, marginBottom: 8, color: "var(--color-ink)" }}>
        {locations.length > 1 ? "Locations" : "Location"}
      </h4>
      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        {locations.map((loc, i) => {
          const label = composeLocation(loc) || "—";
          return (
            <div key={`${label}-${i}`} data-testid="job-location-item"
              style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "var(--color-ink-2)" }}>
              <span>{label}</span>
              {loc.primary && (
                <span style={{ padding: "1px 7px", borderRadius: 4, fontSize: 10, fontWeight: 600,
                  background: "var(--color-brand-50)", color: "var(--color-brand-700)", border: "1px solid var(--color-brand-200)" }}>
                  Primary
                </span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

// AC-428-10: an unknown (null/undefined) value is OMITTED entirely, never
// rendered as a blank row or a "-" filler.
function InfoKV({ k, v, testId }) {
  if (v === null || v === undefined) return null;
  return (
    <div data-testid={testId} style={{ display: "flex", justifyContent: "space-between", fontSize: 12, padding: "4px 0" }}>
      <span style={{ color: "var(--color-ink-3)" }}>{k}</span>
      <span style={{ color: "var(--color-ink)", fontWeight: 500 }}>{v}</span>
    </div>
  );
}

/* ─── Viewport hook: tracks window.innerWidth, re-renders on resize ─── */
function useViewport() {
  const [width, setWidth] = React.useState(window.innerWidth);
  React.useEffect(() => {
    const h = () => setWidth(window.innerWidth);
    window.addEventListener("resize", h);
    return () => window.removeEventListener("resize", h);
  }, []);
  return width;
}

/* ─── Debounce: avoid one API call per keystroke / slider tick ─── */
function useDebounced(value, ms) {
  const [v, setV] = React.useState(value);
  React.useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

/* ─── Pager: page nav + page-size selector (rendered top & bottom of the list) ─── */
function pageWindow(page, totalPages) {
  // current ±1, always first & last, with "…" gaps.
  const out = [];
  const push = (n) => { if (!out.includes(n) && n >= 0 && n < totalPages) out.push(n); };
  push(0); push(page - 1); push(page); push(page + 1); push(totalPages - 1);
  out.sort((a, b) => a - b);
  const withGaps = [];
  out.forEach((n, i) => {
    if (i > 0 && n - out[i - 1] > 1) withGaps.push("gap" + i);
    withGaps.push(n);
  });
  return withGaps;
}

function Pager({ page, size, total, loading, onPage, onSize }) {
  const totalPages = Math.max(1, Math.ceil(total / size));
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  const numStyle = (active) => ({
    minWidth: 30, height: 30, padding: "0 8px", borderRadius: 6, fontSize: 12, cursor: "pointer",
    border: "1px solid " + (active ? "var(--color-brand-600)" : "var(--color-border-2)"),
    background: active ? "var(--color-brand-600)" : "var(--color-surface)",
    color: active ? "#fff" : "var(--color-ink-2)", fontWeight: active ? 600 : 500,
    display: "inline-flex", alignItems: "center", justifyContent: "center",
  });

  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
      <span style={{ fontSize: 12, color: "var(--color-ink-3)" }}>
        {loading ? "Loading…" : <>Showing <strong style={{ color: "var(--color-ink-2)" }}>{from}–{to}</strong> of {total}</>}
      </span>
      <div style={{ flex: 1 }} />
      <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "var(--color-ink-3)" }}>
        Per page
        <select className="input" style={{ width: 72, height: 30, fontSize: 12 }} value={size}
          onChange={(e) => onSize(Number(e.target.value))}>
          {PAGE_SIZES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </label>
      <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
        <button style={{ ...numStyle(false), opacity: page <= 0 ? 0.4 : 1, cursor: page <= 0 ? "default" : "pointer" }}
          disabled={page <= 0} onClick={() => onPage(page - 1)} aria-label="Previous page">
          <Icon name="chevron-left" size={14} />
        </button>
        {pageWindow(page, totalPages).map((n) =>
          typeof n === "string"
            ? <span key={n} style={{ color: "var(--color-ink-4)", padding: "0 2px" }}>…</span>
            : <button key={n} style={numStyle(n === page)} onClick={() => onPage(n)}>{n + 1}</button>
        )}
        <button style={{ ...numStyle(false), opacity: page >= totalPages - 1 ? 0.4 : 1, cursor: page >= totalPages - 1 ? "default" : "pointer" }}
          disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)} aria-label="Next page">
          <Icon name="chevron-right" size={14} />
        </button>
      </div>
    </div>
  );
}

/* ─── Job Search Screen ─── */
function JobSearchScreen({ goto, onSaveToggle, savedIds, openJob, appliedJobIds, authed, openSearch, onOpenApplyProfile }) {
  const vw = useViewport();
  const isCollapsed = vw < 860;
  const [query, setQuery] = React.useState("");
  const [selectedCompanies, setSelectedCompanies] = React.useState(new Set()); // company NAMES
  const [selectedLocations, setSelectedLocations] = React.useState(new Set()); // countries + "Remote"
  const [selectedEmploymentTypes, setSelectedEmploymentTypes] = React.useState(new Set()); // slugs e.g. "full-time"
  const [selectedCareerLevels, setSelectedCareerLevels] = React.useState(new Set()); // slugs e.g. "senior"
  const [selectedLanguage, setSelectedLanguage] = React.useState("all");
  // Story #331 (sub-issue #381): default the job search to "Past 3 days" on cold start
  // instead of "Any time". applyFilterState (saved filters / restored state) always sets
  // this explicitly from the saved/restored value, so this default only governs the
  // screen's very first render, never overrides an explicit user or saved choice.
  const [postedFilter, setPostedFilter] = React.useState("3days");
  const [sortBy, setSortBy] = React.useState("newest");

  // Server-side result page
  const [apiItems, setApiItems] = React.useState([]);
  const [apiTotal, setApiTotal] = React.useState(0);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(DEFAULT_SIZE);
  const [loading, setLoading] = React.useState(USE_API);

  // Filter-option facets (full table)
  const [facets, setFacets] = React.useState({ companies: [], locations: [], languages: [], employmentTypes: [], careerLevels: [] });

  // ── Saved filters, per user (story #523) ──
  // The API is the source of truth when authenticated and USE_API=true; USE_API=false
  // keeps presets in component state for the session only. The legacy shared
  // localStorage key is dropped, not migrated (ADR 0030 Decision 2).
  const [savedFilters, setSavedFilters] = React.useState([]); // [{ id, name, state }]
  const [savedFiltersError, setSavedFiltersError] = React.useState(false);
  const [savingFilter, setSavingFilter] = React.useState(false);
  const [saveError, setSaveError] = React.useState(null);
  const [showSaveDialog, setShowSaveDialog] = React.useState(false);
  const [filterName, setFilterName] = React.useState("");

  // One-time cleanup of the legacy shared-browser key, in every mode.
  React.useEffect(() => {
    try { localStorage.removeItem("jobhub_saved_filters"); } catch {}
  }, []);

  // Load presets, keyed on `authed` so a sign-out/sign-in inside one tab never leaks the
  // previous user's list. Clearing on authed===false is load-bearing (not defensive): it
  // is what makes "per user" true within a single tab session (ADR 0030 Decision 1/3).
  React.useEffect(() => {
    if (!authed) { setSavedFilters([]); setSavedFiltersError(false); return; }
    if (!USE_API) return; // mock mode keeps its in-session list
    let cancelled = false;
    setSavedFiltersError(false);
    listSavedFilters()
      .then((rows) => { if (!cancelled) setSavedFilters(rows.map(savedFilterFromApi)); })
      .catch(() => { if (!cancelled) { setSavedFilters([]); setSavedFiltersError(true); } });
    return () => { cancelled = true; };
  }, [authed]);

  const dQuery = useDebounced(query, 300);

  // ── Load filter facets (re-fetch whenever active filters change, debounced) ──
  // The facets call mirrors the active filter state so each bucket shows
  // drill-down counts ("exclude own dimension" semantics per the contract).
  React.useEffect(() => {
    let cancelled = false;
    if (!USE_API) return;
    const facetFilters = {
      keyword: dQuery || undefined,
      company: selectedCompanies.size > 0 ? [...selectedCompanies] : undefined,
      location: selectedLocations.size > 0 ? [...selectedLocations] : undefined,
      employmentType: selectedEmploymentTypes.size > 0 ? [...selectedEmploymentTypes] : undefined,
      careerLevel: selectedCareerLevels.size > 0 ? [...selectedCareerLevels] : undefined,
      language: selectedLanguage !== "all" ? [selectedLanguage] : undefined,
      postedWithin: POSTED_UI_TO_API[postedFilter],
    };
    getJobFacets(facetFilters)
      .then((f) => {
        if (!cancelled) setFacets(f);
      })
      .catch(() => {});
    return () => { cancelled = true; };
  // Re-run whenever any debounced filter dimension changes.
  // dQuery is already debounced (300 ms) so rapid changes coalesce.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dQuery, selectedCompanies, selectedLocations, selectedEmploymentTypes, selectedCareerLevels, selectedLanguage, postedFilter]);

  // ── Query cache (story #329): keep-previous-data, no spinner on an exact hit,
  // keep-previous-on-failure, prefetch page n+1 ──
  // hasLoadedOnce gates the big first-load loader (shown at most once per session) and
  // distinguishes a genuine first-load failure from a later, non-blocking one.
  const hasLoadedOnce = React.useRef(false);
  const [searchFailed, setSearchFailed] = React.useState(false);

  function applyResult(res) {
    const mapped = res.items.map((dto) => jobFromApi(dto, DATA.companies));
    // Upsert into the store so openJob / DATA.byId / DATA.coOf resolve for the drawer.
    // Replace (not skip) so full search data wins over any bare applied-job placeholder.
    mapped.forEach((j) => {
      const idx = DATA.jobs.findIndex((x) => x.id === j.id);
      if (idx >= 0) DATA.jobs[idx] = j; else DATA.jobs.push(j);
    });
    setApiItems(mapped);
    setApiTotal(res.total);
  }

  // Silently warm page n+1 after a successful settle so a later "Next" click is a cache
  // hit. Only the immediate next page, only when one exists, never on a miss-catch.
  function warmNextPage(res, filters) {
    const totalPages = res.totalPages || Math.ceil(res.total / (filters.size || size));
    if (filters.page + 1 < totalPages) prefetchSearch({ ...filters, page: filters.page + 1 });
  }

  // ── Server-side search: refetch whenever a filter / sort / page / size changes ──
  React.useEffect(() => {
    if (!USE_API) return;
    let cancelled = false;
    const filters = {
      keyword: dQuery || undefined,
      company: [...selectedCompanies],
      location: [...selectedLocations],
      employmentType: selectedEmploymentTypes.size > 0 ? [...selectedEmploymentTypes] : undefined,
      careerLevel: selectedCareerLevels.size > 0 ? [...selectedCareerLevels] : undefined,
      language: selectedLanguage !== "all" ? [selectedLanguage] : undefined,
      postedWithin: POSTED_UI_TO_API[postedFilter],
      sort: SORT_MAP[sortBy],
      page,
      size,
    };

    setSearchFailed(false);

    // Exact cache hit: seed the list synchronously, no spinner, no dim.
    const cached = peekSearch(filters);
    if (cached) {
      applyResult(cached);
      setLoading(false);
      hasLoadedOnce.current = true;
    } else {
      setLoading(true); // cache miss: keep previous apiItems on screen, dimmed
    }

    // searchJobs is itself cache-first, so a hit above resolves this with no network call.
    searchJobs(filters)
      .then((res) => {
        if (cancelled) return;
        applyResult(res);
        hasLoadedOnce.current = true;
        setSearchFailed(false);
        warmNextPage(res, filters);
      })
      .catch(() => {
        if (cancelled) return;
        setSearchFailed(true);
        if (!hasLoadedOnce.current) {
          // Genuine first-load failure: nothing valid has ever rendered this session.
          setApiItems([]);
          setApiTotal(0);
        }
        // Otherwise: keep-previous-on-failure, leave the last good list on screen and
        // surface the non-blocking searchFailed indicator instead.
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [dQuery, selectedCompanies, selectedLocations, selectedEmploymentTypes, selectedCareerLevels, selectedLanguage, postedFilter, sortBy, page, size]);

  // ── Option lists (full table via facets; fall back to the in-memory store) ──
  const companyOptions = React.useMemo(() => {
    if (USE_API) return (facets.companies || []).map((c) => ({ value: c.value, label: c.value, count: c.count }));
    const counts = {};
    DATA.jobs.forEach((j) => { const n = DATA.coOf(j.co).name; counts[n] = (counts[n] || 0) + 1; });
    return Object.keys(counts).sort().map((n) => ({ value: n, label: n, count: counts[n] }));
  }, [facets]);

  const locationOptions = React.useMemo(() => {
    if (USE_API) return (facets.locations || []).map((l) => ({ value: l.value, label: l.value, count: l.count }));
    const counts = {};
    DATA.jobs.forEach((j) => {
      counts[j.country] = (counts[j.country] || 0) + 1;
      if (j.remote) counts["Remote"] = (counts["Remote"] || 0) + 1;
    });
    return Object.keys(counts).sort().map((l) => ({ value: l, label: l, count: counts[l] }));
  }, [facets]);

  const employmentTypeOptions = React.useMemo(() => {
    if (!USE_API) return [];
    return (facets.employmentTypes || []).map((e) => ({
      value: e.value,
      label: EMPLOYMENT_TYPE_LABEL[e.value] || e.value,
      count: e.count,
    }));
  }, [facets]);

  const careerLevelOptions = React.useMemo(() => {
    if (!USE_API) return [];
    return (facets.careerLevels || []).map((c) => ({
      value: c.value,
      label: CAREER_LEVEL_LABEL[c.value] || c.value,
      count: c.count,
    }));
  }, [facets]);

  const allLanguages = React.useMemo(() => {
    if (USE_API) return (facets.languages || []).map((l) => l.value);
    return [...new Set(DATA.jobs.map((j) => j.language))].sort();
  }, [facets]);

  // ── Client-side fallback (USE_API=false): filter + sort the in-memory store ──
  const clientFiltered = React.useMemo(() => {
    if (USE_API) return [];
    let list = DATA.jobs.filter((j) => {
      const coName = DATA.coOf(j.co).name;
      if (query && !`${j.title} ${coName}`.toLowerCase().includes(query.toLowerCase())) return false;
      if (selectedCompanies.size > 0 && !selectedCompanies.has(coName)) return false;
      if (selectedLocations.size > 0) {
        const matchCountry = selectedLocations.has(j.country);
        const matchRemote = selectedLocations.has("Remote") && j.remote;
        if (!matchCountry && !matchRemote) return false;
      }
      if (selectedLanguage !== "all" && j.language !== selectedLanguage) return false;
      if (postedFilter === "today" && j.postedDays > 1) return false;
      if (postedFilter === "3days" && j.postedDays > 3) return false;
      if (postedFilter === "week" && j.postedDays > 7) return false;
      if (postedFilter === "month" && j.postedDays > 30) return false;
      return true;
    });
    if (sortBy === "newest") list = [...list].sort((a, b) => a.postedDays - b.postedDays);
    else if (sortBy === "salary") list = [...list].sort((a, b) => b.compMin - a.compMin);
    return list;
  }, [query, selectedCompanies, selectedLocations, selectedLanguage, postedFilter, sortBy]);

  const results = USE_API ? apiItems : clientFiltered.slice(page * size, page * size + size);
  const total = USE_API ? apiTotal : clientFiltered.length;

  // Any filter change resets to the first page.
  const resetPage = () => setPage(0);

  // Bumped by "Clear all" / "Saved filters → apply" so any open multi-select dropdown
  // discards its pending edits and closes (E7/AC-F18/AC-F19).
  const [multiSelectResetSignal, setMultiSelectResetSignal] = React.useState(0);

  const clearAll = () => {
    setQuery(""); setSelectedCompanies(new Set()); setSelectedLocations(new Set());
    setSelectedEmploymentTypes(new Set()); setSelectedCareerLevels(new Set());
    setSelectedLanguage("all"); setPostedFilter("any");
    setMultiSelectResetSignal((n) => n + 1);
    resetPage();
  };

  const currentFilterState = () => ({
    query, companies: [...selectedCompanies], locations: [...selectedLocations],
    employmentTypes: [...selectedEmploymentTypes], careerLevels: [...selectedCareerLevels],
    language: selectedLanguage, posted: postedFilter,
  });

  const applyFilterState = (f) => {
    setQuery(f.query || "");
    setSelectedCompanies(new Set(f.companies || []));
    setSelectedLocations(new Set(f.locations || []));
    setSelectedEmploymentTypes(new Set(f.employmentTypes || []));
    setSelectedCareerLevels(new Set(f.careerLevels || []));
    setSelectedLanguage(f.language || "all");
    setPostedFilter(f.posted || "any");
    setMultiSelectResetSignal((n) => n + 1);
    resetPage();
  };

  // Create (story #523): guard on name / 5-preset ceiling / an in-flight save. Mock mode
  // (USE_API=false) appends a session-only entry synchronously; API mode calls the server
  // and lets it own the id (no optimistic insert), so the dialog stays open with a fixed,
  // deterministic message on failure (AC-523-14/15).
  const saveCurrentFilter = async () => {
    const name = filterName.trim();
    if (!name || savedFilters.length >= 5 || savingFilter) return;
    if (!USE_API) {
      setSavedFilters((prev) => [...prev, { id: "local-" + Date.now(), name, state: currentFilterState() }]);
      setFilterName("");
      setShowSaveDialog(false);
      return;
    }
    setSavingFilter(true);
    setSaveError(null);
    try {
      const dto = await createSavedFilter({ name, filters: filterValuesFromPreset(currentFilterState()) });
      setSavedFilters((prev) => [...prev, savedFilterFromApi(dto)]);
      setFilterName("");
      setShowSaveDialog(false);
    } catch (e) {
      setSaveError(e instanceof ApiError && e.status === 400
        ? "You already have 5 saved filters. Delete one first."
        : "Couldn't save this filter. Please try again.");
    } finally {
      setSavingFilter(false);
    }
  };

  // Delete (story #523): await first, then remove. A 404 is treated as success (the
  // preset is already gone); any other failure leaves the row in place and surfaces the
  // shared saved-filters-error message (AC-523-20/21).
  const deleteFilter = async (idx) => {
    const target = savedFilters[idx];
    if (!target) return;
    if (!USE_API) {
      setSavedFilters((prev) => prev.filter((_, i) => i !== idx));
      return;
    }
    try {
      await deleteSavedFilter(target.id);
    } catch (e) {
      if (!(e instanceof ApiError) || e.status !== 404) {
        setSavedFiltersError(true);
        return;
      }
    }
    setSavedFilters((prev) => prev.filter((f) => f.id !== target.id));
  };

  // Active chips
  const activeChips = [];
  selectedCompanies.forEach((name) => activeChips.push({ label: name, clear: () => {
    setSelectedCompanies((p) => { const n = new Set(p); n.delete(name); return n; }); resetPage();
  }}));
  selectedLocations.forEach((l) => activeChips.push({ label: l, clear: () => {
    setSelectedLocations((p) => { const n = new Set(p); n.delete(l); return n; }); resetPage();
  }}));
  selectedEmploymentTypes.forEach((slug) => activeChips.push({
    label: EMPLOYMENT_TYPE_LABEL[slug] || slug,
    clear: () => { setSelectedEmploymentTypes((p) => { const n = new Set(p); n.delete(slug); return n; }); resetPage(); },
  }));
  selectedCareerLevels.forEach((slug) => activeChips.push({
    label: CAREER_LEVEL_LABEL[slug] || slug,
    clear: () => { setSelectedCareerLevels((p) => { const n = new Set(p); n.delete(slug); return n; }); resetPage(); },
  }));
  if (selectedLanguage !== "all") activeChips.push({ label: selectedLanguage, clear: () => { setSelectedLanguage("all"); resetPage(); } });
  if (postedFilter !== "any") activeChips.push({ label: "Posted: " + postedFilter, clear: () => { setPostedFilter("any"); resetPage(); } });

  const hasActiveFilters = activeChips.length > 0 || query;

  return (
    <>
      <UI.Topbar
        title="Job search"
        sub={`${total} ${total === 1 ? "job" : "jobs"} match`}
        searchLabel="Search jobs…"
        onSearchClick={openSearch}
        actions={onOpenApplyProfile && (
          <Button variant="secondary" icon="copy" onClick={onOpenApplyProfile} data-testid="apply-profile-trigger-search">
            Apply profile
          </Button>
        )}
      />
      <div className="content">
        <div className={"search-layout" + (isCollapsed ? " search-layout--collapsed" : "")}>
          {/* FILTERS */}
          <div className="filters search-filters">

            {authed && savedFiltersError && (
              <div data-testid="saved-filters-error" role="alert"
                style={{ fontSize: 12, color: "var(--color-danger)", padding: "8px 10px",
                  border: "1px solid var(--color-danger)", borderRadius: 6, background: "var(--color-surface)" }}>
                Couldn't load your saved filters. Reload the page to try again.
              </div>
            )}

            {authed && savedFilters.length > 0 && (
              <SavedFiltersDropdown filters={savedFilters} onApply={applyFilterState} onDelete={deleteFilter} />
            )}

            <Field label="Search">
              <Input leading="search" placeholder="Title, company…" value={query}
                onChange={(e) => { setQuery(e.target.value); resetPage(); }} />
            </Field>

            <Field label="Posted">
              <select className="input" value={postedFilter} onChange={(e) => { setPostedFilter(e.target.value); resetPage(); }}>
                <option value="any">Any time</option>
                <option value="today">Today</option>
                <option value="3days">Past 3 days</option>
                <option value="week">Past week</option>
                <option value="month">Past 30 days</option>
              </select>
            </Field>

            <Field label="Company">
              <MultiSelect label="All companies" options={companyOptions}
                applied={selectedCompanies}
                onApply={(s) => { setSelectedCompanies(s); resetPage(); }}
                onClearApplied={() => { setSelectedCompanies(new Set()); resetPage(); }}
                resetSignal={multiSelectResetSignal} />
            </Field>

            <Field label="Location">
              <MultiSelect label="All locations" options={locationOptions}
                applied={selectedLocations}
                onApply={(s) => { setSelectedLocations(s); resetPage(); }}
                onClearApplied={() => { setSelectedLocations(new Set()); resetPage(); }}
                resetSignal={multiSelectResetSignal} />
            </Field>

            <Field label="Employment type">
              <MultiSelect label="All employment types" options={employmentTypeOptions}
                applied={selectedEmploymentTypes}
                onApply={(s) => { setSelectedEmploymentTypes(s); resetPage(); }}
                onClearApplied={() => { setSelectedEmploymentTypes(new Set()); resetPage(); }}
                resetSignal={multiSelectResetSignal} />
            </Field>

            <Field label="Career level">
              <MultiSelect label="All career levels" options={careerLevelOptions}
                applied={selectedCareerLevels}
                onApply={(s) => { setSelectedCareerLevels(s); resetPage(); }}
                onClearApplied={() => { setSelectedCareerLevels(new Set()); resetPage(); }}
                resetSignal={multiSelectResetSignal} />
            </Field>

            <Field label="Language">
              <select className="input" value={selectedLanguage} onChange={(e) => { setSelectedLanguage(e.target.value); resetPage(); }}>
                <option value="all">All languages</option>
                {allLanguages.map((l) => <option key={l} value={l}>{l}</option>)}
              </select>
            </Field>

            <div style={{ display: "flex", gap: 8 }}>
              {hasActiveFilters && (
                <Button variant="ghost" size="sm" icon="x" onClick={clearAll} style={{ flex: 1 }}>Clear all</Button>
              )}
              {authed && hasActiveFilters && savedFilters.length < 5 && (
                <Button variant="secondary" size="sm" icon="bookmark" onClick={() => { setShowSaveDialog(true); setSaveError(null); }} style={{ flex: 1 }}>Save filter</Button>
              )}
            </div>

            {showSaveDialog && (
              <div style={{ padding: 12, border: "1px solid var(--color-border)", borderRadius: 8, background: "var(--color-surface)", display: "flex", flexDirection: "column", gap: 8 }}>
                <div style={{ fontSize: 12, fontWeight: 600, color: "var(--color-ink)" }}>Save current filters</div>
                <Input placeholder="Filter name…" value={filterName} onChange={(e) => setFilterName(e.target.value)}
                  onKeyDown={(e) => { if (e.key === "Enter") saveCurrentFilter(); }} autoFocus />
                {saveError && (
                  <div data-testid="save-filter-error" role="alert" style={{ fontSize: 11, color: "var(--color-danger)" }}>{saveError}</div>
                )}
                <div style={{ display: "flex", gap: 6, justifyContent: "flex-end" }}>
                  <Button variant="ghost" size="sm" onClick={() => setShowSaveDialog(false)} disabled={savingFilter}>Cancel</Button>
                  <Button variant="primary" size="sm" onClick={saveCurrentFilter} disabled={!filterName.trim() || savingFilter}>Save</Button>
                </div>
                <div style={{ fontSize: 11, color: "var(--color-ink-4)" }}>{savedFilters.length}/5 saved</div>
              </div>
            )}
          </div>

          {/* RESULTS */}
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14, flexWrap: "wrap" }}>
              {activeChips.map((f) => (
                <span key={f.label} className="chip active" onClick={f.clear}>
                  {f.label} <Icon name="x" size={11} />
                </span>
              ))}
              <div style={{ flex: 1 }} />
              <select className="input" style={{ width: 170, height: 30, fontSize: 12 }}
                value={sortBy} onChange={(e) => { setSortBy(e.target.value); resetPage(); }}>
                <option value="newest">Sort: Newest first</option>
                <option value="salary">Salary: high to low</option>
              </select>
            </div>

            {/* Top pager */}
            <div style={{ paddingBottom: 12, marginBottom: 12, borderBottom: "1px solid var(--color-border)" }}>
              <Pager page={page} size={size} total={total} loading={loading}
                onPage={setPage} onSize={(s) => { setSize(s); resetPage(); }} />
            </div>

            {loading && !hasLoadedOnce.current ? (
              <WritingLoader label="Writing up the latest postings…" />
            ) : (
              <div data-testid="results-list" className="results-list" data-dimmed={loading ? "true" : "false"}
                style={{ display: "flex", flexDirection: "column", gap: 10, opacity: loading ? 0.6 : 1, transition: "opacity 120ms" }}>
                {searchFailed && (
                  <div data-testid="search-failed-banner" className="banner-warning" role="alert"
                    style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <Icon name="info" size={14} />
                    <span style={{ flex: 1 }}>
                      {hasLoadedOnce.current
                        ? "Couldn't refresh results. Showing the last successful results."
                        : "Couldn't load results. Please try again."}
                    </span>
                    <button type="button" onClick={() => setSearchFailed(false)} aria-label="Dismiss"
                      style={{ background: "none", border: "none", cursor: "pointer", color: "inherit", display: "flex", padding: 0 }}>
                      <Icon name="x" size={12} />
                    </button>
                  </div>
                )}
                {results.map((j) => (
                  <JobRow key={j.id} job={j}
                    isSaved={savedIds.has(j.id)}
                    isApplied={appliedJobIds.has(j.id)}
                    onSave={onSaveToggle}
                    onOpen={openJob} />
                ))}
                {results.length === 0 && !loading && !searchFailed && (
                  <Empty icon="search" title="No jobs match your filters" desc="Try broadening location or the posted-date range, or remove a filter." />
                )}
              </div>
            )}

            {/* Bottom pager */}
            {total > 0 && (
              <div style={{ paddingTop: 14, marginTop: 14, borderTop: "1px solid var(--color-border)" }}>
                <Pager page={page} size={size} total={total} loading={loading}
                  onPage={setPage} onSize={(s) => { setSize(s); resetPage(); }} />
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  );
}

export { JobSearchScreen, JobDetailDrawer };
