import React from "react";
import Icon from "../components/Icon.jsx";
import DATA from "../data/mockData.js";
import * as UI from "../components/ui.jsx";
import RichText from "../components/RichText.jsx";
import WritingLoader from "../components/WritingLoader.jsx";
import { MultiSelect, DualRangeSlider, SavedFiltersDropdown } from "../components/FilterComponents.jsx";
import { searchJobs, getJobFacets } from "../api/jobs.js";
import { jobFromApi } from "../api/mappers.js";
import { USE_API } from "../api/config.js";

// JobHub — Job Search screen + Job Detail drawer
const { Button, Input, Field, StatusPill, CoLogo, Card, Empty, JobRow } = UI;

// UI filter value → GET /jobs query param
const POSTED_MAP = { any: undefined, today: "today", "3days": "3d", week: "week", month: "month" };
const SORT_MAP = { newest: "newest", salary: "salary-desc" };
const PAGE_SIZES = [10, 25, 50, 100];
const DEFAULT_SIZE = 25;

// Ensure a crawled URL is absolute so it opens as an external link (not relative to the SPA).
function normalizeUrl(raw) {
  const s = String(raw || "").trim();
  if (!s) return null;
  return /^[a-z][a-z0-9+.-]*:\/\//i.test(s) ? s : "https://" + s;
}

/* ─── Job Detail Drawer ─── */
function JobDetailDrawer({ job, onClose, onApply, onSave, isSaved, isApplied, authed }) {
  const c = DATA.coOf(job.co);
  React.useEffect(() => {
    const h = (e) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [onClose]);

  return (
    <>
      <div className="job-drawer-backdrop" onClick={onClose} />
      <div className="job-drawer">
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
          <Button variant="ghost" size="sm" icon="x" onClick={onClose} />
        </div>

        <div className="job-drawer-body">
          <div style={{ marginBottom: 24 }}>
            <h4 style={{ fontSize: 14, fontWeight: 600, marginBottom: 8, color: "var(--color-ink)" }}>About the role</h4>
            <RichText text={job.desc} />
          </div>
          <div style={{ marginBottom: 24 }}>
            <h4 style={{ fontSize: 14, fontWeight: 600, marginBottom: 8, color: "var(--color-ink)" }}>Requirements</h4>
            <ul style={{ margin: 0, paddingLeft: 18, display: "flex", flexDirection: "column", gap: 6 }}>
              {job.reqs.map((r, i) => (
                <li key={i} style={{ fontSize: 13, color: "var(--color-ink-2)", lineHeight: 1.5 }}>{r}</li>
              ))}
            </ul>
          </div>
          <div style={{ padding: 16, background: "var(--color-bg)", borderRadius: 10, border: "1px solid var(--color-border)" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12 }}>
              <CoLogo co={job.co} />
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: "var(--color-ink)" }}>{c.name}</div>
                <div style={{ fontSize: 12, color: "var(--color-ink-3)" }}>{c.industry}</div>
              </div>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
              <InfoKV k="Size" v={c.size} />
              <InfoKV k="HQ" v={c.hq} />
              <InfoKV k="Source" v={job.source} />
              <InfoKV k="Posted" v={UI.postedLabel(job.postedDays)} />
            </div>
          </div>
        </div>

        <div className="job-drawer-foot">
          <Button variant="ghost" icon="bookmark"
            onClick={() => onSave(job)}
            style={isSaved ? { color: "var(--color-brand-600)" } : {}}>
            {isSaved ? "Saved" : "Save job"}
          </Button>
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

function InfoKV({ k, v }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, padding: "4px 0" }}>
      <span style={{ color: "var(--color-ink-3)" }}>{k}</span>
      <span style={{ color: "var(--color-ink)", fontWeight: 500 }}>{v}</span>
    </div>
  );
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
function JobSearchScreen({ goto, onSaveToggle, savedIds, openJob, appliedJobIds, authed, openSearch }) {
  const [query, setQuery] = React.useState("");
  const [selectedCompanies, setSelectedCompanies] = React.useState(new Set()); // company NAMES
  const [selectedLocations, setSelectedLocations] = React.useState(new Set()); // countries + "Remote"
  const [selectedLanguage, setSelectedLanguage] = React.useState("all");
  const [postedFilter, setPostedFilter] = React.useState("any");
  const [sortBy, setSortBy] = React.useState("newest");

  // Compensation slider bounds come from the data (facets); defaults until they load.
  const [bounds, setBounds] = React.useState({ min: 0, max: 300 });
  const [compMin, setCompMin] = React.useState(0);
  const [compMax, setCompMax] = React.useState(300);

  // Server-side result page
  const [apiItems, setApiItems] = React.useState([]);
  const [apiTotal, setApiTotal] = React.useState(0);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(DEFAULT_SIZE);
  const [loading, setLoading] = React.useState(USE_API);

  // Filter-option facets (full table)
  const [facets, setFacets] = React.useState({ companies: [], locations: [], languages: [] });

  // Saved filters (persisted in localStorage, max 5)
  const SAVED_KEY = "jobhub_saved_filters";
  const [savedFilters, setSavedFilters] = React.useState(() => {
    try { return JSON.parse(localStorage.getItem(SAVED_KEY)) || []; } catch { return []; }
  });
  const [showSaveDialog, setShowSaveDialog] = React.useState(false);
  const [filterName, setFilterName] = React.useState("");

  React.useEffect(() => {
    try { localStorage.setItem(SAVED_KEY, JSON.stringify(savedFilters)); } catch {}
  }, [savedFilters]);

  const dQuery = useDebounced(query, 300);
  const dCompMin = useDebounced(compMin, 300);
  const dCompMax = useDebounced(compMax, 300);

  // ── Load filter facets once (full-table option lists + comp range) ──
  React.useEffect(() => {
    let cancelled = false;
    function applyBounds(lo, hi) {
      let bMin = lo != null ? Math.floor(lo / 1000 / 10) * 10 : 0;
      let bMax = hi != null ? Math.ceil(hi / 1000 / 10) * 10 : 300;
      if (bMax <= bMin) bMax = bMin + 10;
      setBounds({ min: bMin, max: bMax });
      setCompMin(bMin); setCompMax(bMax);
    }
    if (USE_API) {
      getJobFacets()
        .then((f) => { if (!cancelled) { setFacets(f); applyBounds(f.compensationMin, f.compensationMax); } })
        .catch(() => {});
    } else {
      const minsK = DATA.jobs.map((j) => j.compMin).filter((n) => n > 0);
      const maxsK = DATA.jobs.map((j) => j.compMax).filter((n) => n > 0 && n < 999);
      const lo = minsK.length ? Math.min(...minsK) * 1000 : null;
      const hi = maxsK.length ? Math.max(...maxsK) * 1000 : null;
      applyBounds(lo, hi);
    }
    return () => { cancelled = true; };
  }, []);

  // ── Server-side search: refetch whenever a filter / sort / page / size changes ──
  React.useEffect(() => {
    if (!USE_API) return;
    let cancelled = false;
    setLoading(true);
    const filters = {
      keyword: dQuery || undefined,
      company: [...selectedCompanies],
      location: [...selectedLocations],
      language: selectedLanguage !== "all" ? [selectedLanguage] : undefined,
      postedWithin: POSTED_MAP[postedFilter],
      sort: SORT_MAP[sortBy],
      compensationMin: dCompMin > bounds.min ? dCompMin * 1000 : undefined,
      compensationMax: dCompMax < bounds.max ? dCompMax * 1000 : undefined,
      page,
      size,
    };
    searchJobs(filters)
      .then((res) => {
        if (cancelled) return;
        const mapped = res.items.map((dto) => jobFromApi(dto, DATA.companies));
        // Upsert into the store so openJob / DATA.byId / DATA.coOf resolve for the drawer.
        // Replace (not skip) so full search data wins over any bare applied-job placeholder.
        mapped.forEach((j) => {
          const idx = DATA.jobs.findIndex((x) => x.id === j.id);
          if (idx >= 0) DATA.jobs[idx] = j; else DATA.jobs.push(j);
        });
        setApiItems(mapped);
        setApiTotal(res.total);
      })
      .catch(() => { if (!cancelled) { setApiItems([]); setApiTotal(0); } })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [dQuery, selectedCompanies, selectedLocations, selectedLanguage, postedFilter, sortBy, dCompMin, dCompMax, page, size, bounds]);

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
      if (compMin > bounds.min && j.compMax < compMin) return false;
      if (compMax < bounds.max && j.compMin > compMax) return false;
      return true;
    });
    if (sortBy === "newest") list = [...list].sort((a, b) => a.postedDays - b.postedDays);
    else if (sortBy === "salary") list = [...list].sort((a, b) => b.compMin - a.compMin);
    return list;
  }, [query, selectedCompanies, selectedLocations, selectedLanguage, postedFilter, sortBy, compMin, compMax, bounds]);

  const results = USE_API ? apiItems : clientFiltered.slice(page * size, page * size + size);
  const total = USE_API ? apiTotal : clientFiltered.length;

  // Any filter change resets to the first page.
  const resetPage = () => setPage(0);

  const clearAll = () => {
    setQuery(""); setSelectedCompanies(new Set()); setSelectedLocations(new Set());
    setSelectedLanguage("all"); setPostedFilter("any");
    setCompMin(bounds.min); setCompMax(bounds.max);
    resetPage();
  };

  const currentFilterState = () => ({
    query, companies: [...selectedCompanies], locations: [...selectedLocations],
    language: selectedLanguage, posted: postedFilter, compMin, compMax,
  });

  const applyFilterState = (f) => {
    setQuery(f.query || "");
    setSelectedCompanies(new Set(f.companies || []));
    setSelectedLocations(new Set(f.locations || []));
    setSelectedLanguage(f.language || "all");
    setPostedFilter(f.posted || "any");
    setCompMin(f.compMin != null ? f.compMin : bounds.min);
    setCompMax(f.compMax != null ? f.compMax : bounds.max);
    resetPage();
  };

  const saveCurrentFilter = () => {
    if (!filterName.trim() || savedFilters.length >= 5) return;
    setSavedFilters((prev) => [...prev, { name: filterName.trim(), state: currentFilterState() }]);
    setFilterName("");
    setShowSaveDialog(false);
  };

  const deleteFilter = (idx) => setSavedFilters((prev) => prev.filter((_, i) => i !== idx));

  // Active chips
  const activeChips = [];
  selectedCompanies.forEach((name) => activeChips.push({ label: name, clear: () => {
    setSelectedCompanies((p) => { const n = new Set(p); n.delete(name); return n; }); resetPage();
  }}));
  selectedLocations.forEach((l) => activeChips.push({ label: l, clear: () => {
    setSelectedLocations((p) => { const n = new Set(p); n.delete(l); return n; }); resetPage();
  }}));
  if (selectedLanguage !== "all") activeChips.push({ label: selectedLanguage, clear: () => { setSelectedLanguage("all"); resetPage(); } });
  if (postedFilter !== "any") activeChips.push({ label: "Posted: " + postedFilter, clear: () => { setPostedFilter("any"); resetPage(); } });
  if (compMin > bounds.min || compMax < bounds.max) {
    activeChips.push({ label: `€${compMin}k–€${compMax}k`, clear: () => { setCompMin(bounds.min); setCompMax(bounds.max); resetPage(); } });
  }

  const hasActiveFilters = activeChips.length > 0 || query;

  return (
    <>
      <UI.Topbar
        title="Job search"
        sub={`${total} ${total === 1 ? "job" : "jobs"} match`}
        searchLabel="Search jobs…"
        onSearchClick={openSearch}
      />
      <div className="content">
        <div className="search-layout">
          {/* FILTERS */}
          <div className="filters search-filters">

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
                selected={selectedCompanies} onChange={(s) => { setSelectedCompanies(s); resetPage(); }} />
            </Field>

            <Field label="Location">
              <MultiSelect label="All locations" options={locationOptions}
                selected={selectedLocations} onChange={(s) => { setSelectedLocations(s); resetPage(); }} />
            </Field>

            <Field label="Language">
              <select className="input" value={selectedLanguage} onChange={(e) => { setSelectedLanguage(e.target.value); resetPage(); }}>
                <option value="all">All languages</option>
                {allLanguages.map((l) => <option key={l} value={l}>{l}</option>)}
              </select>
            </Field>

            <Field label={`Compensation: €${compMin}k – €${compMax}k`}>
              <DualRangeSlider min={bounds.min} max={bounds.max} step={10}
                valueMin={compMin} valueMax={compMax}
                onChange={(lo, hi) => { setCompMin(lo); setCompMax(hi); resetPage(); }}
                formatLabel={(v) => `€${v}k`} />
            </Field>

            <div style={{ display: "flex", gap: 8 }}>
              {hasActiveFilters && (
                <Button variant="ghost" size="sm" icon="x" onClick={clearAll} style={{ flex: 1 }}>Clear all</Button>
              )}
              {authed && hasActiveFilters && savedFilters.length < 5 && (
                <Button variant="secondary" size="sm" icon="bookmark" onClick={() => setShowSaveDialog(true)} style={{ flex: 1 }}>Save filter</Button>
              )}
            </div>

            {showSaveDialog && (
              <div style={{ padding: 12, border: "1px solid var(--color-border)", borderRadius: 8, background: "var(--color-surface)", display: "flex", flexDirection: "column", gap: 8 }}>
                <div style={{ fontSize: 12, fontWeight: 600, color: "var(--color-ink)" }}>Save current filters</div>
                <Input placeholder="Filter name…" value={filterName} onChange={(e) => setFilterName(e.target.value)}
                  onKeyDown={(e) => { if (e.key === "Enter") saveCurrentFilter(); }} autoFocus />
                <div style={{ display: "flex", gap: 6, justifyContent: "flex-end" }}>
                  <Button variant="ghost" size="sm" onClick={() => setShowSaveDialog(false)}>Cancel</Button>
                  <Button variant="primary" size="sm" onClick={saveCurrentFilter} disabled={!filterName.trim()}>Save</Button>
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

            {loading && results.length === 0 ? (
              <WritingLoader label="Writing up the latest postings…" />
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 10, opacity: loading ? 0.6 : 1, transition: "opacity 120ms" }}>
                {results.map((j) => (
                  <JobRow key={j.id} job={j}
                    isSaved={savedIds.has(j.id)}
                    isApplied={appliedJobIds.has(j.id)}
                    onSave={onSaveToggle}
                    onOpen={openJob} />
                ))}
                {results.length === 0 && !loading && (
                  <Empty icon="search" title="No jobs match your filters" desc="Try broadening location or compensation, or remove a filter." />
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
