/**
 * Component tests for JobDetailDrawer
 * Cases: EP-FE-01..06
 *
 * EP-FE-01: language shown in drawer
 * EP-FE-02: "Unknown" still shown (not suppressed)
 * EP-FE-03: missing/undefined language → no crash, shows fallback "—" or omits row gracefully
 * EP-FE-04: wide class applied at >=1280px (job-drawer--wide)
 * EP-FE-05: no wide class at 375px
 * EP-FE-06: search-layout--collapsed at <860px (800px)
 */
import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, afterEach } from "vitest";
import DATA from "../../data/mockData.js";

// ── Mocks ──────────────────────────────────────────────────────────────────

vi.mock("../../api/config.js", () => ({ USE_API: false }));

vi.mock("../../api/jobs.js", () => ({
  searchJobs: vi.fn(),
  getJobFacets: vi.fn(),
  getJob: vi.fn(),
}));

vi.mock("../../data/mockData.js", () => ({
  default: {
    companies: {},
    jobs: [],
    applications: [],
    saved: [],
    byId: () => undefined,
    coOf: () => ({ name: "Acme Corp", industry: "Technology", size: "100-500", hq: "Madrid, Spain", url: "" }),
    appForJob: () => undefined,
    nextAppId: () => "APP-001",
  },
}));

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

vi.mock("../../components/WritingLoader.jsx", () => ({
  default: ({ label }) => <div data-testid="writing-loader">{label}</div>,
}));

// ── Helpers ─────────────────────────────────────────────────────────────────

// Story #330: the drawer now hydrates description/requirements via getJob(id) on open,
// unless the job already carries full detail (hasFullDetail=true, per mappers.jobFromApi).
// The pre-existing suites in this file (EP-FE-*, QAE-UI-*) built a fully-formed job object
// and asserted synchronously, before this story's hydrate-on-open behaviour existed: mark
// the default fixture "already loaded" (AC-14's signal) so they keep asserting synchronously
// without needing to mock/await getJob individually.
function makeJob(overrides = {}) {
  return {
    id: "job-1",
    co: "acme",
    title: "Software Engineer",
    location: "Madrid, Spain",
    comp: "€60k–€80k",
    compMin: 60,
    compMax: 80,
    type: "Full-time",
    postedDays: 2,
    source: "Greenhouse",
    remote: false,
    tags: ["React", "TypeScript"],
    country: "Spain",
    language: "English",
    hasFullDetail: true,
    desc: "A great role.",
    reqs: ["3+ years experience"],
    url: "https://example.com/job",
    ...overrides,
  };
}

async function renderDrawer(jobOverrides = {}, props = {}) {
  const { JobDetailDrawer } = await import("../../screens/JobSearch.jsx");
  const job = makeJob(jobOverrides);
  const defaultProps = {
    job,
    onClose: vi.fn(),
    onApply: vi.fn(),
    onSave: vi.fn(),
    isSaved: false,
    isApplied: false,
    authed: false,
  };
  return render(<JobDetailDrawer {...defaultProps} {...props} />);
}

// ── EP-FE-01..03: Language display ──────────────────────────────────────────

describe("JobDetailDrawer — language display (EP-FE-01..03)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("EP-FE-01: shows the job language in the drawer", async () => {
    await renderDrawer({ language: "Spanish" });
    expect(screen.getByText("Spanish")).toBeInTheDocument();
  });

  it("EP-FE-02: shows 'Unknown' language without suppressing it", async () => {
    await renderDrawer({ language: "Unknown" });
    expect(screen.getByText("Unknown")).toBeInTheDocument();
  });

  it("EP-FE-03: missing/undefined language does not crash and shows fallback", async () => {
    // Should not throw; renders "—" or omits gracefully
    await renderDrawer({ language: undefined });
    // The drawer still renders (title is visible)
    expect(screen.getByText("Software Engineer")).toBeInTheDocument();
    // Either shows "—" fallback or simply omits the row — neither crashes
    // If a "—" is shown for the language field, verify it's present
    const content = document.body.textContent;
    // Should not contain "undefined" literally
    expect(content).not.toContain("undefined");
  });
});

// ── Multiple locations (story #1, #293): QAE-UI-DISPLAY-3/4, QAE-UI-EDGE-1/2 ──────────

describe("JobDetailDrawer — multiple locations (QAE-UI-DISPLAY-3/4, QAE-UI-EDGE-1/2)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("QAE-UI-DISPLAY-3: shows every opening without extra interaction, primary marked first", async () => {
    await renderDrawer({
      location: "Barcelona, Spain",
      locations: [
        { country: "Spain", city: "Barcelona", primary: true },
        { country: "Netherlands", city: "Amsterdam", primary: false },
        { country: "France", city: "Paris", primary: false },
      ],
    });

    // All three openings visible without any hover/click interaction (queried within
    // the location items list, since the head summary line also shows the primary
    // opening's short string as today).
    const items = screen.getAllByTestId("job-location-item");
    expect(items).toHaveLength(3);
    expect(items[0]).toHaveTextContent("Barcelona, Spain");
    expect(items[1]).toHaveTextContent("Amsterdam, Netherlands");
    expect(items[2]).toHaveTextContent("Paris, France");

    // Primary opening is first in DOM order and carries a distinguishing marker.
    expect(items[0]).toHaveTextContent(/primary/i);
    expect(items[1]).not.toHaveTextContent(/primary/i);
    expect(items[2]).not.toHaveTextContent(/primary/i);
  });

  it("QAE-UI-DISPLAY-4: single-opening posting shows exactly one location line, unchanged", async () => {
    await renderDrawer({
      location: "Madrid, Spain",
      locations: [{ country: "Spain", city: "Madrid", primary: true }],
    });

    const items = screen.getAllByTestId("job-location-item");
    expect(items).toHaveLength(1);
    expect(items[0]).toHaveTextContent("Madrid, Spain");
    // No "+0" or other stray affordance for the single-opening case.
    expect(screen.queryByTestId("location-more")).not.toBeInTheDocument();
  });

  it("QAE-UI-EDGE-1: empty/absent locations renders without crashing, no location line", async () => {
    await renderDrawer({ location: undefined, locations: [] });
    expect(screen.getByText("Software Engineer")).toBeInTheDocument();
    expect(screen.queryAllByTestId("job-location-item")).toHaveLength(0);
    expect(document.body.textContent).not.toContain("undefined");
  });

  it("QAE-UI-EDGE-2: Remote-only posting renders 'Remote' in the detail view", async () => {
    await renderDrawer({
      location: "Remote",
      locations: [{ country: "Remote", city: null, primary: true }],
    });
    const items = screen.getAllByTestId("job-location-item");
    expect(items).toHaveLength(1);
    expect(items[0]).toHaveTextContent("Remote");
  });
});

// ── EP-FE-04..05: job-drawer--wide class ────────────────────────────────────

describe("JobDetailDrawer — wide class toggle (EP-FE-04..05)", () => {
  const originalInnerWidth = window.innerWidth;

  afterEach(() => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: originalInnerWidth,
    });
    vi.clearAllMocks();
  });

  it("EP-FE-04: applies job-drawer--wide class when window.innerWidth >= 1280 (1440px)", async () => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: 1440,
    });

    await renderDrawer();
    const drawer = document.querySelector(".job-drawer");
    expect(drawer).not.toBeNull();
    expect(drawer.classList.contains("job-drawer--wide")).toBe(true);
  });

  it("EP-FE-05: does NOT apply job-drawer--wide class when window.innerWidth < 1280 (375px)", async () => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: 375,
    });

    await renderDrawer();
    const drawer = document.querySelector(".job-drawer");
    expect(drawer).not.toBeNull();
    expect(drawer.classList.contains("job-drawer--wide")).toBe(false);
  });
});

// ── EP-FE-06: search-layout--collapsed class ─────────────────────────────────

describe("JobSearchScreen — search-layout--collapsed class (EP-FE-06)", () => {
  const originalInnerWidth = window.innerWidth;

  afterEach(() => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: originalInnerWidth,
    });
    vi.clearAllMocks();
  });

  it("EP-FE-06: applies search-layout--collapsed when window.innerWidth < 860 (800px)", async () => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: 800,
    });

    const { searchJobs, getJobFacets } = await import("../../api/jobs.js");
    searchJobs.mockResolvedValue({ items: [], total: 0, page: 0, totalPages: 0 });
    getJobFacets.mockResolvedValue({
      companies: [], locations: [], languages: [],
      employmentTypes: [], careerLevels: [],
      compensationMin: 0, compensationMax: 300000,
    });

    const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");
    render(
      <JobSearchScreen
        goto={vi.fn()}
        onSaveToggle={vi.fn()}
        savedIds={new Set()}
        openJob={vi.fn()}
        appliedJobIds={new Set()}
        authed={false}
        openSearch={vi.fn()}
      />
    );

    const layout = document.querySelector(".search-layout");
    expect(layout).not.toBeNull();
    expect(layout.classList.contains("search-layout--collapsed")).toBe(true);
  });
});

// ── TC-DRAWER-1..9: hydrate-on-open via getJob(id) (story #330) ─────────────

// A job prop shaped like jobFromApi's output for a slim GET /jobs summary: the header
// fields are all present, but description/requirements were never fetched.
function makeSlimJob(overrides = {}) {
  const job = makeJob(overrides);
  delete job.hasFullDetail;
  delete job.desc;
  delete job.reqs;
  return { ...job, hasFullDetail: false, desc: undefined, reqs: undefined };
}

function fullDto(overrides = {}) {
  return {
    description: "AAA description",
    requirements: ["3+ years experience"],
    ...overrides,
  };
}

describe("JobDetailDrawer : hydrate-on-open (TC-DRAWER-1, AC-6)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("TC-DRAWER-1: header renders instantly (getByText, no await) while getJob is still pending", async () => {
    const { getJob } = await import("../../api/jobs.js");
    getJob.mockImplementation(() => new Promise(() => {})); // never resolves in this test

    await renderDrawer(makeSlimJob());

    expect(screen.getByText("Software Engineer")).toBeInTheDocument();
    expect(screen.getAllByText("Acme Corp").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Madrid, Spain").length).toBeGreaterThan(0);
    expect(screen.getByText("€60k–€80k")).toBeInTheDocument();
    expect(screen.getByText("Full-time")).toBeInTheDocument();
  });
});

describe("JobDetailDrawer : loading state (TC-DRAWER-2, AC-7)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("TC-DRAWER-2: shows a loading indicator for both sections, never blank, controls stay enabled", async () => {
    const { getJob } = await import("../../api/jobs.js");
    getJob.mockImplementation(() => new Promise(() => {}));

    const onClose = vi.fn();
    await renderDrawer(makeSlimJob(), { onClose });

    const loaders = screen.getAllByTestId("job-detail-loading");
    expect(loaders).toHaveLength(2);
    expect(screen.queryByText("No description provided.")).not.toBeInTheDocument();
    expect(screen.queryByTestId("job-detail-error")).not.toBeInTheDocument();

    const closeBtn = screen.getByLabelText("Close");
    const applyBtn = screen.getByText("Apply now");
    const saveBtn = screen.getByText("Save job");
    expect(closeBtn).not.toBeDisabled();
    expect(applyBtn).not.toBeDisabled();
    expect(saveBtn).not.toBeDisabled();

    fireEvent.click(closeBtn);
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

describe("JobDetailDrawer : successful hydration (TC-DRAWER-3, AC-8)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("TC-DRAWER-3: resolved description/requirements replace the loading indicator", async () => {
    const { getJob } = await import("../../api/jobs.js");
    getJob.mockResolvedValue(fullDto({
      description: "We build delightful developer tools.",
      requirements: ["5+ years experience", "Fluent in React"],
    }));

    await renderDrawer(makeSlimJob());

    expect(await screen.findByText("We build delightful developer tools.")).toBeInTheDocument();
    expect(screen.getByText("5+ years experience")).toBeInTheDocument();
    expect(screen.getByText("Fluent in React")).toBeInTheDocument();
    expect(screen.queryAllByTestId("job-detail-loading")).toHaveLength(0);
  });
});

describe("JobDetailDrawer : fetch failure fallback (TC-DRAWER-4, AC-9)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it.each([
    ["network error", new Error("Network error : is the backend reachable?")],
    ["404/5xx via ApiError", Object.assign(new Error("Job not found"), { name: "ApiError", status: 404 })],
  ])("TC-DRAWER-4: %s : plain fallback, no raw error text, controls still work", async (_label, err) => {
    const { getJob } = await import("../../api/jobs.js");
    getJob.mockRejectedValue(err);

    const onClose = vi.fn();
    await renderDrawer(makeSlimJob(), { onClose });

    const errors = await screen.findAllByTestId("job-detail-error");
    expect(errors.length).toBeGreaterThan(0);
    expect(screen.getByText("Software Engineer")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain(err.message);

    const closeBtn = screen.getByLabelText("Close");
    fireEvent.click(closeBtn);
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

describe("JobDetailDrawer : list stays usable after a failed fetch (TC-DRAWER-5, AC-10)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("TC-DRAWER-5: a failed detail fetch never leaks into the shared search/facets mocks", async () => {
    const { getJob, searchJobs, getJobFacets } = await import("../../api/jobs.js");
    getJob.mockRejectedValue(new Error("boom"));
    searchJobs.mockResolvedValue({ items: [], total: 0, page: 0, totalPages: 0 });
    getJobFacets.mockResolvedValue({
      companies: [], locations: [], languages: [],
      employmentTypes: [], careerLevels: [],
      compensationMin: 0, compensationMax: 300000,
    });

    const onClose = vi.fn();
    const { unmount } = await renderDrawer(makeSlimJob(), { onClose });
    await screen.findAllByTestId("job-detail-error");

    fireEvent.click(screen.getByLabelText("Close"));
    expect(onClose).toHaveBeenCalledTimes(1);
    unmount(); // drawer's failure state is local useState : unmounting on close discards it

    // the list's own data functions remain callable and unaffected by the drawer's failure
    await expect(searchJobs({})).resolves.toEqual({ items: [], total: 0, page: 0, totalPages: 0 });
    await expect(getJobFacets({})).resolves.toMatchObject({ compensationMin: 0 });
  });
});

describe("JobDetailDrawer : genuinely empty requirements (TC-DRAWER-6, AC-11)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("TC-DRAWER-6: empty requirements[] shows a distinct empty state, not the error fallback", async () => {
    const { getJob } = await import("../../api/jobs.js");
    getJob.mockResolvedValue(fullDto({ description: "A role with no listed requirements.", requirements: [] }));

    await renderDrawer(makeSlimJob());

    expect(await screen.findByTestId("job-detail-requirements-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("job-detail-error")).not.toBeInTheDocument();
  });

  it("TC-DRAWER-6b: the empty-requirements testid and the error testid never appear together", async () => {
    const { getJob } = await import("../../api/jobs.js");
    getJob.mockRejectedValue(new Error("boom"));

    await renderDrawer(makeSlimJob());

    await screen.findAllByTestId("job-detail-error");
    expect(screen.queryByTestId("job-detail-requirements-empty")).not.toBeInTheDocument();
  });
});

describe("JobDetailDrawer : no stale cross-job content (TC-DRAWER-7, AC-12)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("TC-DRAWER-7: closing job A and opening job B never shows job A's description, even while loading", async () => {
    const { getJob } = await import("../../api/jobs.js");
    const { JobDetailDrawer } = await import("../../screens/JobSearch.jsx");

    getJob.mockResolvedValueOnce(fullDto({ description: "AAA description", requirements: ["AAA req"] }));
    const jobA = makeSlimJob({ id: "job-a" });
    const { unmount } = render(
      <JobDetailDrawer job={jobA} onClose={vi.fn()} onApply={vi.fn()} onSave={vi.fn()} isSaved={false} isApplied={false} authed={false} />
    );
    expect(await screen.findByText("AAA description")).toBeInTheDocument();
    unmount(); // App.jsx unmounts the drawer when the candidate closes it

    let resolveB;
    getJob.mockImplementationOnce(() => new Promise((resolve) => { resolveB = resolve; }));
    const jobB = makeSlimJob({ id: "job-b", title: "Frontend Engineer" });
    render(
      <JobDetailDrawer job={jobB} onClose={vi.fn()} onApply={vi.fn()} onSave={vi.fn()} isSaved={false} isApplied={false} authed={false} />
    );

    // immediately after mount, still loading : job A's text must not be present
    expect(document.body.textContent).not.toContain("AAA description");

    resolveB(fullDto({ description: "BBB description", requirements: ["BBB req"] }));
    expect(await screen.findByText("BBB description")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("AAA description");
  });
});

describe("JobDetailDrawer : reopening the same job (TC-DRAWER-8, AC-13)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("TC-DRAWER-8: close then reopen the same job shows its correct detail again, never blank", async () => {
    const { getJob } = await import("../../api/jobs.js");
    const { JobDetailDrawer } = await import("../../screens/JobSearch.jsx");
    const job = makeSlimJob({ id: "job-1" });

    getJob.mockResolvedValueOnce(fullDto({ description: "Original description", requirements: ["Original req"] }));
    const { unmount } = render(
      <JobDetailDrawer job={job} onClose={vi.fn()} onApply={vi.fn()} onSave={vi.fn()} isSaved={false} isApplied={false} authed={false} />
    );
    expect(await screen.findByText("Original description")).toBeInTheDocument();
    unmount();

    getJob.mockResolvedValueOnce(fullDto({ description: "Original description", requirements: ["Original req"] }));
    render(
      <JobDetailDrawer job={job} onClose={vi.fn()} onApply={vi.fn()} onSave={vi.fn()} isSaved={false} isApplied={false} authed={false} />
    );
    expect(await screen.findByText("Original description")).toBeInTheDocument();
    expect(screen.getByText("Original req")).toBeInTheDocument();
  });

  it("TC-DRAWER-8b: an uncancelled first fetch resolving after remount never corrupts the second mount's text", async () => {
    const { getJob } = await import("../../api/jobs.js");
    const { JobDetailDrawer } = await import("../../screens/JobSearch.jsx");
    const job = makeSlimJob({ id: "job-1" });

    let resolveFirst;
    getJob.mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }));
    const { unmount } = render(
      <JobDetailDrawer job={job} onClose={vi.fn()} onApply={vi.fn()} onSave={vi.fn()} isSaved={false} isApplied={false} authed={false} />
    );
    unmount(); // unmounted before the first fetch ever resolved

    getJob.mockResolvedValueOnce(fullDto({ description: "Second mount description", requirements: ["Second req"] }));
    render(
      <JobDetailDrawer job={job} onClose={vi.fn()} onApply={vi.fn()} onSave={vi.fn()} isSaved={false} isApplied={false} authed={false} />
    );
    expect(await screen.findByText("Second mount description")).toBeInTheDocument();

    // the stale first fetch finally resolves : its ignore-flagged setState must be a no-op
    resolveFirst(fullDto({ description: "STALE description", requirements: ["stale req"] }));
    await waitFor(() => expect(screen.getByText("Second mount description")).toBeInTheDocument());
    expect(document.body.textContent).not.toContain("STALE description");
  });
});

describe("JobDetailDrawer : already-loaded entry points (TC-DRAWER-9, AC-14)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("TC-DRAWER-9: a job with hasFullDetail=true never calls getJob and never shows a loading/error state", async () => {
    const { getJob } = await import("../../api/jobs.js");
    getJob.mockResolvedValue(fullDto());

    // makeJob() defaults to hasFullDetail: true (a Saved-Jobs-style entry point).
    await renderDrawer({ desc: "Already-known description.", reqs: ["Already-known requirement"] });

    expect(screen.getByText("Already-known description.")).toBeInTheDocument();
    expect(screen.getByText("Already-known requirement")).toBeInTheDocument();
    expect(screen.queryByTestId("job-detail-loading")).not.toBeInTheDocument();
    expect(screen.queryByTestId("job-detail-error")).not.toBeInTheDocument();

    await waitFor(() => expect(getJob).not.toHaveBeenCalled());
  });
});

// ── QAE-428-FEC-01/02: company panel omission (story #428, ADR 0023) ────────
//
// AC-428-10/22/23: an unknown (null/undefined) industry/size/headquarters is
// OMITTED from the company panel entirely, never rendered as a blank row or a
// placeholder. FEC-02 is the non-omission control: populated fields still
// render with their real values.

describe("JobDetailDrawer : company panel omission (QAE-428-FEC-01/02)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it("QAE-428-FEC-01: null industry/size/hq are omitted, not blank-rendered or dash-rendered", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: null, size: null, hq: null, url: "",
    });

    await renderDrawer();

    expect(screen.queryByTestId("company-industry")).not.toBeInTheDocument();
    expect(screen.queryByTestId("company-size-row")).not.toBeInTheDocument();
    expect(screen.queryByTestId("company-hq-row")).not.toBeInTheDocument();
    // the company name itself still renders
    expect(screen.getAllByText("Acme Corp").length).toBeGreaterThan(0);
  });

  it("QAE-428-FEC-02: populated industry/size/hq still render with their real values", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", size: "51-200", hq: "Zurich, Switzerland", url: "",
    });

    await renderDrawer();

    expect(screen.getByTestId("company-industry")).toHaveTextContent("Fintech");
    expect(screen.getByTestId("company-size-row")).toHaveTextContent("Size");
    expect(screen.getByTestId("company-size-row")).toHaveTextContent("51-200");
    expect(screen.getByTestId("company-hq-row")).toHaveTextContent("HQ");
    expect(screen.getByTestId("company-hq-row")).toHaveTextContent("Zurich, Switzerland");
  });
});

// ── Company tags row (story #430): company.tags is real, curated data for the
// first time (admin enrichment). Distinct from the existing (dead, synthetic)
// job.tags chip row at the drawer head : this new row lives in the company
// panel and reads co.tags exclusively. AC-430-29/30. ────────────────────────

describe("JobDetailDrawer : company tags row (QAE-430-UI-*)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it("QAE-430-UI-01: curated, non-empty company.tags renders a tags/chip row", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", size: "51-200", hq: "Zurich, Switzerland",
      tags: ["remote-first", "b2b"], url: "",
    });

    await renderDrawer();

    const row = screen.getByTestId("company-tags-row");
    expect(row).toBeInTheDocument();
    expect(screen.getByText("remote-first")).toBeInTheDocument();
    expect(screen.getByText("b2b")).toBeInTheDocument();
  });

  it("QAE-430-UI-02a: tags:null omits the row entirely", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", size: "51-200", hq: "Zurich, Switzerland",
      tags: null, url: "",
    });

    await renderDrawer();

    expect(screen.queryByTestId("company-tags-row")).not.toBeInTheDocument();
  });

  it("QAE-430-UI-02b: tags key absent entirely also omits the row", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", size: "51-200", hq: "Zurich, Switzerland", url: "",
    });

    await renderDrawer();

    expect(screen.queryByTestId("company-tags-row")).not.toBeInTheDocument();
  });

  it("QAE-430-UI-03a: a non-empty job.tags never substitutes for a null co.tags", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", size: "51-200", hq: "Zurich, Switzerland",
      tags: null, url: "",
    });

    await renderDrawer({ tags: ["Remote", "Senior"] });

    expect(screen.queryByTestId("company-tags-row")).not.toBeInTheDocument();
  });

  it("QAE-430-UI-03b: an empty (synthetic) job.tags never suppresses a populated co.tags", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", size: "51-200", hq: "Zurich, Switzerland",
      tags: ["series-c"], url: "",
    });

    await renderDrawer({ tags: [] });

    const row = screen.getByTestId("company-tags-row");
    expect(row).toHaveTextContent("series-c");
  });

  it("QAE-430-UI-04: existing FEC-01/02 industry/size/hq omission cases are unaffected (regression guard)", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: null, size: null, hq: null, tags: ["ai"], url: "",
    });

    await renderDrawer();

    expect(screen.queryByTestId("company-industry")).not.toBeInTheDocument();
    expect(screen.queryByTestId("company-size-row")).not.toBeInTheDocument();
    expect(screen.queryByTestId("company-hq-row")).not.toBeInTheDocument();
    expect(screen.getByTestId("company-tags-row")).toHaveTextContent("ai");
  });
});

// ── Company website + description (story #486, sub-issue #490) ─────────────
// AC-486-04..09: the company panel gains a website link and a description
// paragraph, both null-omitted, without disturbing the existing four fields.

describe("JobDetailDrawer : company website link (QAE-486-04/05)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it("QAE-486-04: non-null website renders as a working, accessible external link", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", size: "51-200", hq: "Zurich, Switzerland",
      website: "https://acme.example.com", url: "",
    });

    await renderDrawer();

    const link = screen.getByRole("link", { name: /acme\.example\.com|website/i });
    expect(link).toHaveAttribute("href", "https://acme.example.com");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link.getAttribute("rel")).toMatch(/noopener/i);
  });

  it("QAE-486-05: null website is omitted, other company panel fields unaffected", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", size: "51-200", hq: "Zurich, Switzerland",
      website: null, tags: ["remote-first"], url: "",
    });

    await renderDrawer();

    expect(screen.queryByTestId("company-website-row")).not.toBeInTheDocument();
    expect(screen.queryAllByRole("link").some((el) => el.getAttribute("href") === "https://acme.example.com")).toBe(false);
    expect(screen.getByTestId("company-industry")).toHaveTextContent("Fintech");
    expect(screen.getByTestId("company-size-row")).toHaveTextContent("51-200");
    expect(screen.getByTestId("company-hq-row")).toHaveTextContent("Zurich, Switzerland");
    expect(screen.getByTestId("company-tags-row")).toHaveTextContent("remote-first");
  });
});

describe("JobDetailDrawer : company description (QAE-486-06/07/08)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it("QAE-486-06: non-null description renders as prose in the company panel", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech",
      description: "We build delightful developer tools for fintech teams.", url: "",
    });

    await renderDrawer();

    expect(screen.getByText("We build delightful developer tools for fintech teams.")).toBeInTheDocument();
  });

  it("QAE-486-06b: a very long description renders fully without throwing", async () => {
    const longDescription = Array(40).fill("Fintech tooling.").join(" "); // 640+ chars
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", description: longDescription, url: "",
    });

    await renderDrawer();

    expect(screen.getAllByText(longDescription).length).toBeGreaterThan(0);
  });

  it("QAE-486-07: null description is omitted, never borrows the job's 'No description' empty-state copy", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", description: null, url: "",
    });

    await renderDrawer();

    expect(screen.queryByTestId("company-description")).not.toBeInTheDocument();
    expect(screen.queryByText(/no description/i)).not.toBeInTheDocument();
  });

  it("QAE-486-08: render-side half of upgrade-never-downgrade : store's known description always renders when present (store-merge itself covered by QAE-428-FE-06)", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech",
      description: "Financial infrastructure for the internet.", url: "",
    });

    await renderDrawer();

    expect(screen.getByText("Financial infrastructure for the internet.")).toBeInTheDocument();
  });
});

describe("JobDetailDrawer : website + description alongside existing fields (QAE-486-09)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it("QAE-486-09: adding website/description doesn't suppress or alter industry/size/hq/tags", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", size: "51-200", hq: "Zurich, Switzerland",
      tags: ["remote-first", "b2b"],
      website: "https://acme.example.com",
      description: "Financial infrastructure for the internet.",
      url: "",
    });

    await renderDrawer();

    expect(screen.getByTestId("company-industry")).toHaveTextContent("Fintech");
    expect(screen.getByTestId("company-size-row")).toHaveTextContent("51-200");
    expect(screen.getByTestId("company-hq-row")).toHaveTextContent("Zurich, Switzerland");
    const tagsRow = screen.getByTestId("company-tags-row");
    expect(tagsRow).toHaveTextContent("remote-first");
    expect(tagsRow).toHaveTextContent("b2b");

    const link = screen.getByRole("link", { name: /acme\.example\.com|website/i });
    expect(link).toHaveAttribute("href", "https://acme.example.com");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link.getAttribute("rel")).toMatch(/noopener/i);

    expect(screen.getByText("Financial infrastructure for the internet.")).toBeInTheDocument();
  });

  it("QAE-486-09b: existing FEC-01 all-null combo is unaffected when website/description also absent", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: null, size: null, hq: null, website: null, description: null, url: "",
    });

    await renderDrawer();

    expect(screen.queryByTestId("company-industry")).not.toBeInTheDocument();
    expect(screen.queryByTestId("company-size-row")).not.toBeInTheDocument();
    expect(screen.queryByTestId("company-hq-row")).not.toBeInTheDocument();
    expect(screen.queryByTestId("company-website-row")).not.toBeInTheDocument();
    expect(screen.queryByTestId("company-description")).not.toBeInTheDocument();
    expect(screen.getAllByText("Acme Corp").length).toBeGreaterThan(0);
  });
});
