/**
 * AdminCompaniesPage: admin-only company browse/enrichment screen (story #430,
 * sub-issue #456).
 *
 * Gated on account.isAdmin (from parent App); never rendered for non-admins or
 * unauthenticated users, mirroring AdminPage's own gating.
 *
 * Browse (GET /jobs/admin/companies): paginated (20/page), filterable by a name
 * substring (`q`) and by curated state (`manuallyEdited`), sorted alphabetically
 * by default. `X-Total-Count` (surfaced by api/jobs.js as `total`) drives the
 * pager without a second request.
 *
 * Edit (GET+PUT /jobs/admin/companies/{id}): opening a company loads the full
 * CompanyInfo projection and pre-fills every editable field, including nulls as
 * empty inputs (never a placeholder). The update call is a FULL REPLACE (ADR
 * 0025 D4): the form always submits all seven editable fields, echoing back
 * whatever the admin did not touch, so a field is never accidentally cleared by
 * omission and a field the admin does clear (empty input) is sent as null.
 *
 * Tag entry has a client-side format guard (lowercase kebab-case, <=20 tags,
 * unique) for a fast inline UX signal; the backend remains the source of truth
 * and re-validates on submit regardless.
 */
import React from "react";
import { listAdminCompanies, getAdminCompany, updateAdminCompany } from "../api/jobs.js";
import * as UI from "../components/ui.jsx";

const { Button, Input, Field, Empty } = UI;

const PAGE_SIZE = 20;
const TAG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const MAX_TAGS = 20;

const EDITABLE_FIELDS = ["website", "industry", "size", "headquarters", "description", "logoUrl"];

// Builds the form's editable-field state straight from a CompanyInfo response:
// every field maps 1:1, null stays "" for the input, so re-submitting an
// untouched field round-trips back to null (full-replace, ADR 0025 D4).
function formFromCompany(c) {
  return {
    website: c.website ?? "",
    industry: c.industry ?? "",
    size: c.size ?? "",
    headquarters: c.headquarters ?? "",
    description: c.description ?? "",
    logoUrl: c.logoUrl ?? "",
    tags: Array.isArray(c.tags) ? [...c.tags] : [],
  };
}

// The exact PUT body (all seven fields, every time): an empty string means
// "cleared" and is sent as null, never "". Never a partial object.
function buildUpdateBody(form) {
  const body = { tags: form.tags.length ? form.tags : null };
  EDITABLE_FIELDS.forEach((f) => {
    const v = (form[f] || "").trim();
    body[f] = v ? v : null;
  });
  return body;
}

function validateNewTag(candidate, existingTags) {
  if (!candidate) return "Enter a tag.";
  if (existingTags.length >= MAX_TAGS) return `A company can carry at most ${MAX_TAGS} tags.`;
  if (!TAG_PATTERN.test(candidate)) return "Tags must be lowercase-kebab-case, e.g. \"remote-first\".";
  if (candidate.length > 40) return "A tag can be at most 40 characters.";
  if (existingTags.includes(candidate)) return "That tag is already on this company.";
  return null;
}

// Row secondary line (story #486, sub-issue #490): industry/size/headquarters
// each render only when non-null, dot-separated in the given order, with no
// stray leading/trailing/doubled separator when one or more are missing. The
// whole line is omitted when all three are null (AC-486-17).
function CompanyRowMeta({ industry, size, headquarters }) {
  const parts = [industry, size, headquarters].filter((v) => v != null);
  if (parts.length === 0) return null;
  return (
    <div style={{ display: "flex", gap: 8, fontSize: 12, color: "var(--color-ink-3)", flexWrap: "wrap", alignItems: "center" }}>
      {parts.map((v, i) => (
        <React.Fragment key={i}>
          {i > 0 && <span className="dot-sep" />}
          <span>{v}</span>
        </React.Fragment>
      ))}
    </div>
  );
}

/**
 * AdminCompaniesPage component.
 * Props:
 *  - account: AccountResponse with isAdmin=true (enforced by parent)
 */
export function AdminCompaniesPage({ account }) {
  const [items, setItems] = React.useState([]);
  const [total, setTotal] = React.useState(0);
  const [page, setPage] = React.useState(0);
  const [loading, setLoading] = React.useState(true);
  const [loadError, setLoadError] = React.useState(null);

  const [qInput, setQInput] = React.useState("");
  const [q, setQ] = React.useState("");
  const [filter, setFilter] = React.useState("all"); // all | backlog | curated

  const [selectedId, setSelectedId] = React.useState(null);
  const [selected, setSelected] = React.useState(null); // full CompanyInfo of the open row
  const [form, setForm] = React.useState(null);
  const [editLoading, setEditLoading] = React.useState(false);
  const [editLoadError, setEditLoadError] = React.useState(null);
  const [saving, setSaving] = React.useState(false);
  const [saveError, setSaveError] = React.useState(null);
  const [saveSuccess, setSaveSuccess] = React.useState(false);

  const [tagInput, setTagInput] = React.useState("");
  const [tagError, setTagError] = React.useState(null);

  const manuallyEdited = filter === "backlog" ? false : filter === "curated" ? true : undefined;

  const fetchList = React.useCallback(async (opts = {}) => {
    setLoading(true);
    setLoadError(null);
    try {
      const { items: fetched, total: fetchedTotal } = await listAdminCompanies({
        q: opts.q ?? q,
        manuallyEdited: "manuallyEdited" in opts ? opts.manuallyEdited : manuallyEdited,
        page: opts.page ?? page,
        size: PAGE_SIZE,
      });
      setItems(fetched);
      setTotal(fetchedTotal);
    } catch (e) {
      setLoadError((e && e.message) || "Couldn't load companies.");
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, manuallyEdited, page]);

  React.useEffect(() => {
    fetchList({ page: 0 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleSearchSubmit(e) {
    e.preventDefault();
    setPage(0);
    setQ(qInput);
    fetchList({ q: qInput, page: 0 });
  }

  function handleFilterChange(e) {
    const next = e.target.value;
    setFilter(next);
    const nextManuallyEdited = next === "backlog" ? false : next === "curated" ? true : undefined;
    setPage(0);
    fetchList({ manuallyEdited: nextManuallyEdited, page: 0 });
  }

  function handleNextPage() {
    const nextPage = page + 1;
    setPage(nextPage);
    fetchList({ page: nextPage });
  }

  function handlePrevPage() {
    const prevPage = Math.max(0, page - 1);
    setPage(prevPage);
    fetchList({ page: prevPage });
  }

  async function handleOpen(id) {
    setSelectedId(id);
    setSelected(null);
    setForm(null);
    setEditLoadError(null);
    setSaveError(null);
    setSaveSuccess(false);
    setTagInput("");
    setTagError(null);
    setEditLoading(true);
    try {
      const company = await getAdminCompany(id);
      setSelected(company);
      setForm(formFromCompany(company));
    } catch (e) {
      setEditLoadError((e && e.message) || "Couldn't load this company.");
    } finally {
      setEditLoading(false);
    }
  }

  function handleBack() {
    setSelectedId(null);
    setSelected(null);
    setForm(null);
  }

  function setField(name, value) {
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  function handleAddTag() {
    const candidate = tagInput.trim().toLowerCase();
    const err = validateNewTag(candidate, form.tags);
    if (err) {
      setTagError(err);
      return;
    }
    setForm((prev) => ({ ...prev, tags: [...prev.tags, candidate] }));
    setTagInput("");
    setTagError(null);
  }

  function handleRemoveTag(tag) {
    setForm((prev) => ({ ...prev, tags: prev.tags.filter((t) => t !== tag) }));
  }

  async function handleSave() {
    setSaving(true);
    setSaveError(null);
    setSaveSuccess(false);
    try {
      const body = buildUpdateBody(form);
      const updated = await updateAdminCompany(selectedId, body);
      setSelected(updated);
      setForm(formFromCompany(updated));
      setSaveSuccess(true);
      // Reflect the fresh curated state in the list behind this form without a
      // second full-list round trip.
      setItems((prev) => prev.map((c) => (c.id === selectedId ? updated : c)));
    } catch (e) {
      if (e && e.status === 400) {
        setSaveError((e && e.message) || "That update was rejected. Check the fields and try again.");
      } else {
        setSaveError((e && e.message) || "Couldn't save this company.");
      }
    } finally {
      setSaving(false);
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const hasNextPage = (page + 1) * PAGE_SIZE < total;

  return (
    <>
      <UI.Topbar title="Admin: Companies" />
      <div data-testid="admin-companies-page" className="content">
        {!selectedId ? (
          <>
            <form className="admin-company-filters" onSubmit={handleSearchSubmit}>
              <Input
                data-testid="company-search-input"
                placeholder="Search companies by name…"
                value={qInput}
                onChange={(e) => setQInput(e.target.value)}
                leading="search"
              />
              <Button data-testid="company-search-submit" type="submit" size="sm">Search</Button>
              <select
                data-testid="company-filter-select"
                className="input"
                value={filter}
                onChange={handleFilterChange}
                style={{ maxWidth: 220 }}
              >
                <option value="all">All companies</option>
                <option value="backlog">Needs enrichment</option>
                <option value="curated">Curated</option>
              </select>
            </form>

            {loadError && (
              <p role="alert" data-testid="admin-companies-load-error" className="admin-load-error">
                {loadError}
              </p>
            )}

            {!loading && !loadError && items.length === 0 && (
              <div data-testid="admin-companies-empty">
                <Empty
                  icon="building"
                  title="No companies match this filter"
                  desc="Try a different search or filter."
                />
              </div>
            )}

            {items.length > 0 && (
              <div className="card" data-testid="admin-companies-list">
                {items.map((c) => (
                  <div
                    key={c.id}
                    data-testid={`company-row-${c.id}`}
                    className="admin-company-row"
                    role="button"
                    tabIndex={0}
                    onClick={() => handleOpen(c.id)}
                    onKeyDown={(e) => { if (e.key === "Enter") handleOpen(c.id); }}
                  >
                    <div>
                      <div style={{ fontSize: 14, fontWeight: 600, color: "var(--color-ink)" }}>{c.name}</div>
                      <CompanyRowMeta industry={c.industry} size={c.size} headquarters={c.headquarters} />
                    </div>
                    <span className={"company-curated-pill " + (c.manuallyEdited ? "curated" : "backlog")}>
                      <span className="dot" />
                      {c.manuallyEdited ? "Curated" : "Not curated"}
                    </span>
                  </div>
                ))}
              </div>
            )}

            <div className="admin-company-pager">
              <Button data-testid="company-page-prev" size="sm" variant="ghost" onClick={handlePrevPage} disabled={page === 0}>
                Previous
              </Button>
              <span>Page {page + 1} of {totalPages}</span>
              <Button data-testid="company-page-next" size="sm" variant="ghost" onClick={handleNextPage} disabled={!hasNextPage}>
                Next
              </Button>
            </div>
          </>
        ) : (
          <CompanyEditForm
            selected={selected}
            form={form}
            loading={editLoading}
            loadError={editLoadError}
            saving={saving}
            saveError={saveError}
            saveSuccess={saveSuccess}
            tagInput={tagInput}
            tagError={tagError}
            onTagInputChange={(v) => { setTagInput(v); setTagError(null); }}
            onAddTag={handleAddTag}
            onRemoveTag={handleRemoveTag}
            onFieldChange={setField}
            onSave={handleSave}
            onBack={handleBack}
          />
        )}
      </div>
    </>
  );
}

function CompanyEditForm({
  selected, form, loading, loadError, saving, saveError, saveSuccess,
  tagInput, tagError, onTagInputChange, onAddTag, onRemoveTag, onFieldChange, onSave, onBack,
}) {
  return (
    <div>
      <Button data-testid="company-back-btn" variant="ghost" size="sm" icon="arrow-left" onClick={onBack}>
        Back to companies
      </Button>

      {loading && <p>Loading company…</p>}
      {loadError && (
        <p role="alert" data-testid="company-edit-load-error" className="admin-load-error">{loadError}</p>
      )}

      {form && (
        <div data-testid="company-edit-form" className="card card-pad" style={{ marginTop: 12 }}>
          <h3 style={{ marginTop: 0 }}>{selected.name}</h3>
          <span className={"company-curated-pill " + (selected.manuallyEdited ? "curated" : "backlog")}>
            <span className="dot" />
            {selected.manuallyEdited ? "Curated" : "Not curated"}
          </span>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginTop: 16 }}>
            <Field label="Website">
              <Input data-testid="company-field-website" type="url" value={form.website}
                onChange={(e) => onFieldChange("website", e.target.value)} />
            </Field>
            <Field label="Industry">
              <Input data-testid="company-field-industry" value={form.industry}
                onChange={(e) => onFieldChange("industry", e.target.value)} />
            </Field>
            <Field label="Size" hint="e.g. 1-10, 11-50, 51-200, 201-500, 1001-5000">
              <Input data-testid="company-field-size" value={form.size}
                onChange={(e) => onFieldChange("size", e.target.value)} />
            </Field>
            <Field label="Headquarters" hint="City, Country">
              <Input data-testid="company-field-headquarters" value={form.headquarters}
                onChange={(e) => onFieldChange("headquarters", e.target.value)} />
            </Field>
            <Field label="Logo URL">
              <Input data-testid="company-field-logoUrl" type="url" value={form.logoUrl}
                onChange={(e) => onFieldChange("logoUrl", e.target.value)} />
            </Field>
          </div>

          <div style={{ marginTop: 14 }}>
            <Field label="Description">
              <textarea data-testid="company-field-description" className="input" rows={4}
                value={form.description}
                onChange={(e) => onFieldChange("description", e.target.value)}
                style={{ width: "100%" }} />
            </Field>
          </div>

          <div style={{ marginTop: 14 }}>
            <Field label="Tags" hint='Lowercase kebab-case, e.g. "remote-first". Max 20.' error={tagError}>
              <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 8 }}>
                {form.tags.map((t) => (
                  <span key={t} className="chip">
                    {t}
                    <span
                      data-testid={`company-tag-remove-${t}`}
                      onClick={() => onRemoveTag(t)}
                      style={{ cursor: "pointer", marginLeft: 4, color: "var(--color-ink-3)" }}
                      aria-label={`Remove ${t}`}
                    >
                      ×
                    </span>
                  </span>
                ))}
              </div>
              <div style={{ display: "flex", gap: 8 }}>
                <Input
                  data-testid="company-tag-input"
                  placeholder="e.g. remote-first"
                  value={tagInput}
                  onChange={(e) => onTagInputChange(e.target.value)}
                  onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); onAddTag(); } }}
                />
                <Button data-testid="company-tag-add" type="button" size="sm" onClick={onAddTag}>Add</Button>
              </div>
              {tagError && <p data-testid="company-tag-error" role="alert" className="admin-error-text">{tagError}</p>}
            </Field>
          </div>

          {saveError && (
            <p role="alert" data-testid="company-save-error" className="admin-error-text" style={{ marginTop: 12 }}>
              {saveError}
            </p>
          )}
          {saveSuccess && (
            <p role="status" data-testid="company-save-success" className="admin-feedback success" style={{ marginTop: 12 }}>
              Saved. This company is now marked as curated.
            </p>
          )}

          <div style={{ marginTop: 16 }}>
            <Button data-testid="company-save-btn" variant="primary" onClick={onSave} disabled={saving}>
              {saving ? "Saving…" : "Save company"}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
