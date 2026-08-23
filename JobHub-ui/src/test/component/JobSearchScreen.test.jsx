/**
 * Component tests for JobSearchScreen
 * Cases: FE-EMP-08 through FE-EMP-19
 *
 * Strategy:
 * - Mock ../api/jobs.js to control searchJobs + getJobFacets responses
 * - Mock ../api/config.js to control USE_API flag
 * - Mock ../data/mockData.js (empty store, not used in API mode)
 * - The component is rendered in isolation; no real network calls
 */
import React from "react";
import { render, screen, waitFor, fireEvent, within } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// ── Mocks ──────────────────────────────────────────────────────────────────

vi.mock("../../api/config.js", () => ({ USE_API: true }));

vi.mock("../../api/jobs.js", () => ({
  searchJobs: vi.fn(),
  getJobFacets: vi.fn(),
  listSavedFilters: vi.fn(),
  // Story #329 (client-side query cache): peekSearch/prefetchSearch/peekFacets are new
  // exports JobSearch.jsx now calls. Default vi.fn() (undefined return) is a cache miss,
  // matching this suite's existing behavior (no real caching involved).
  peekSearch: vi.fn(),
  prefetchSearch: vi.fn(),
  peekFacets: vi.fn(),
}));

// Mock mockData to avoid side effects from the global store singleton
vi.mock("../../data/mockData.js", () => ({
  default: {
    companies: {},
    jobs: [],
    applications: [],
    saved: [],
    byId: () => undefined,
    coOf: () => ({ name: "—", industry: "—", size: "—", hq: "—", url: "" }),
    appForJob: () => undefined,
    nextAppId: () => "APP-001",
  },
}));

// Mock Icon component to avoid SVG resolution issues in jsdom
vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

// Mock WritingLoader
vi.mock("../../components/WritingLoader.jsx", () => ({
  default: ({ label }) => <div data-testid="writing-loader">{label}</div>,
}));

// Mock RichText
vi.mock("../../components/RichText.jsx", () => ({
  default: ({ text }) => <div>{text}</div>,
}));

const { searchJobs, getJobFacets, listSavedFilters } = await import("../../api/jobs.js");

// ── Default mock responses ──────────────────────────────────────────────────

const DEFAULT_FACETS = {
  companies: [{ value: "Acme Corp", count: 5 }],
  locations: [{ value: "Spain", count: 3 }],
  languages: [{ value: "English", count: 8 }],
  employmentTypes: [
    { value: "full-time", count: 12 },
    { value: "contract", count: 4 },
  ],
  careerLevels: [
    { value: "senior", count: 7 },
    { value: "mid", count: 5 },
  ],
  compensationMin: 30000,
  compensationMax: 150000,
};

const EMPTY_SEARCH = {
  items: [],
  total: 0,
  page: 0,
  totalPages: 0,
};

function setupMocks(facetOverride = {}, searchOverride = {}, savedFiltersOverride = []) {
  getJobFacets.mockResolvedValue({ ...DEFAULT_FACETS, ...facetOverride });
  searchJobs.mockResolvedValue({ ...EMPTY_SEARCH, ...searchOverride });
  listSavedFilters.mockResolvedValue(savedFiltersOverride);
}

// ── Helpers ─────────────────────────────────────────────────────────────────

// Render the screen with default props and wait for facets+search to settle
async function renderScreen(props = {}, savedFiltersOverride = []) {
  // Reset mocks before each render
  vi.clearAllMocks();
  setupMocks({}, {}, savedFiltersOverride);

  const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");
  const defaultProps = {
    goto: vi.fn(),
    onSaveToggle: vi.fn(),
    savedIds: new Set(),
    openJob: vi.fn(),
    appliedJobIds: new Set(),
    authed: false,
    openSearch: vi.fn(),
  };
  const result = render(<JobSearchScreen {...defaultProps} {...props} />);

  // Wait for the initial facets + search to resolve
  await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
  await waitFor(() => expect(searchJobs).toHaveBeenCalled());

  return result;
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("JobSearchScreen employment-type and career-level filters", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  // FE-EMP-08: Employment Type MultiSelect renders options + counts with humanised labels
  it("FE-EMP-08 Employment Type MultiSelect renders options and humanised labels", async () => {
    await renderScreen();

    // Employment type field label should appear
    expect(screen.getByText("Employment type")).toBeInTheDocument();

    // Open the Employment Type multiselect by clicking its trigger
    const empTypeTrigger = screen.getByText("All employment types");
    fireEvent.click(empTypeTrigger);

    // Options should appear with humanised labels and counts
    await waitFor(() => {
      expect(screen.getByText("Full-time")).toBeInTheDocument();
    });
    expect(screen.getByText("Contract")).toBeInTheDocument();
    // Counts rendered in mono
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
  });

  // FE-EMP-09: Career Level MultiSelect renders options + counts
  it("FE-EMP-09 Career Level MultiSelect renders options and humanised labels", async () => {
    await renderScreen();

    // Career level field label
    expect(screen.getByText("Career level")).toBeInTheDocument();

    // Open the Career Level multiselect
    const careerTrigger = screen.getByText("All career levels");
    fireEvent.click(careerTrigger);

    await waitFor(() => {
      expect(screen.getByText("Senior")).toBeInTheDocument();
    });
    expect(screen.getByText("Mid")).toBeInTheDocument();
    expect(screen.getByText("7")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
  });

  // FE-EMP-10: selecting one emp-type calls searchJobs with it + page:0, no careerLevel
  it("FE-EMP-10 selecting one emp-type calls searchJobs with employmentType and no careerLevel", async () => {
    await renderScreen();
    vi.clearAllMocks();
    setupMocks();

    // Open dropdown, pick "Full-time", and Apply
    const empTypeTrigger = screen.getByText("All employment types");
    fireEvent.click(empTypeTrigger);
    await waitFor(() => screen.getByText("Full-time"));
    fireEvent.click(screen.getByText("Full-time"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => {
      expect(searchJobs).toHaveBeenCalled();
    });

    const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
    expect(lastCall.employmentType).toEqual(["full-time"]);
    expect(lastCall.page).toBe(0);
    expect(lastCall.careerLevel == null || (Array.isArray(lastCall.careerLevel) && lastCall.careerLevel.length === 0)).toBe(true);
  });

  // FE-EMP-11: selecting one career-level calls searchJobs with careerLevel, no employmentType
  it("FE-EMP-11 selecting one career-level calls searchJobs with careerLevel and no employmentType", async () => {
    await renderScreen();
    vi.clearAllMocks();
    setupMocks();

    const careerTrigger = screen.getByText("All career levels");
    fireEvent.click(careerTrigger);
    await waitFor(() => screen.getByText("Senior"));
    fireEvent.click(screen.getByText("Senior"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => {
      expect(searchJobs).toHaveBeenCalled();
    });

    const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
    expect(lastCall.careerLevel).toEqual(["senior"]);
    expect(lastCall.page).toBe(0);
    expect(lastCall.employmentType == null || (Array.isArray(lastCall.employmentType) && lastCall.employmentType.length === 0)).toBe(true);
  });

  // FE-EMP-12: multi-select sends both values as arrays in one call
  it("FE-EMP-12 selecting both emp-type and career-level sends both as arrays", async () => {
    await renderScreen();
    vi.clearAllMocks();
    setupMocks();

    // Apply employment type
    const empTrigger = screen.getByText("All employment types");
    fireEvent.click(empTrigger);
    await waitFor(() => screen.getByText("Full-time"));
    fireEvent.click(screen.getByText("Full-time"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    vi.clearAllMocks();
    setupMocks();

    // Apply career level
    const careerTrigger = screen.getByText("All career levels");
    fireEvent.click(careerTrigger);
    await waitFor(() => screen.getByText("Senior"));
    fireEvent.click(screen.getByText("Senior"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => {
      const calls = searchJobs.mock.calls;
      const lastCall = calls[calls.length - 1][0];
      expect(lastCall.employmentType).toEqual(["full-time"]);
      expect(lastCall.careerLevel).toEqual(["senior"]);
    });
  });

  // FE-EMP-13: emp-type chip shows display label; clearing it removes only that filter
  it("FE-EMP-13 emp-type chip shows humanised label and clears only employment type", async () => {
    await renderScreen();
    vi.clearAllMocks();
    setupMocks();

    // Apply employment type "Full-time"
    const empTrigger = screen.getByText("All employment types");
    fireEvent.click(empTrigger);
    await waitFor(() => screen.getByText("Full-time"));
    fireEvent.click(screen.getByText("Full-time"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    // Wait for chip to appear
    await waitFor(() => {
      const chips = document.querySelectorAll(".chip.active");
      expect([...chips].some((c) => c.textContent.includes("Full-time"))).toBe(true);
    });

    // Clear the chip
    vi.clearAllMocks();
    setupMocks();
    const chips = document.querySelectorAll(".chip.active");
    const empChip = [...chips].find((c) => c.textContent.includes("Full-time"));
    fireEvent.click(empChip);

    // After clearing, searchJobs is called without employmentType
    await waitFor(() => {
      expect(searchJobs).toHaveBeenCalled();
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.employmentType == null || (Array.isArray(lastCall.employmentType) && lastCall.employmentType.length === 0)).toBe(true);
    });

    // Chip should be removed from DOM
    const newChips = document.querySelectorAll(".chip.active");
    expect([...newChips].some((c) => c.textContent.includes("Full-time"))).toBe(false);
  });

  // FE-EMP-14: career-level chip clears independently
  it("FE-EMP-14 career-level chip clears independently leaving other filters intact", async () => {
    await renderScreen();
    vi.clearAllMocks();
    setupMocks();

    // Apply career level "Senior"
    const careerTrigger = screen.getByText("All career levels");
    fireEvent.click(careerTrigger);
    await waitFor(() => screen.getByText("Senior"));
    fireEvent.click(screen.getByText("Senior"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    // Wait for chip to appear
    await waitFor(() => {
      const chips = document.querySelectorAll(".chip.active");
      expect([...chips].some((c) => c.textContent.includes("Senior"))).toBe(true);
    });

    // Clear the chip
    vi.clearAllMocks();
    setupMocks();
    const chips = document.querySelectorAll(".chip.active");
    const careerChip = [...chips].find((c) => c.textContent.includes("Senior"));
    fireEvent.click(careerChip);

    await waitFor(() => {
      expect(searchJobs).toHaveBeenCalled();
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.careerLevel == null || (Array.isArray(lastCall.careerLevel) && lastCall.careerLevel.length === 0)).toBe(true);
    });

    const newChips = document.querySelectorAll(".chip.active");
    expect([...newChips].some((c) => c.textContent.includes("Senior"))).toBe(false);
  });

  // FE-EMP-15: clearAll clears both new dimensions
  it("FE-EMP-15 clearAll button clears both employmentType and careerLevel filters", async () => {
    await renderScreen();
    vi.clearAllMocks();
    setupMocks();

    // Apply employment type
    const empTrigger = screen.getByText("All employment types");
    fireEvent.click(empTrigger);
    await waitFor(() => screen.getByText("Full-time"));
    fireEvent.click(screen.getByText("Full-time"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    vi.clearAllMocks();
    setupMocks();

    // Apply career level
    const careerTrigger = screen.getByText("All career levels");
    fireEvent.click(careerTrigger);
    await waitFor(() => screen.getByText("Senior"));
    fireEvent.click(screen.getByText("Senior"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    // Both chips should be present
    await waitFor(() => {
      const chips = document.querySelectorAll(".chip.active");
      expect([...chips].some((c) => c.textContent.includes("Full-time"))).toBe(true);
      expect([...chips].some((c) => c.textContent.includes("Senior"))).toBe(true);
    });

    // Click "Clear all"
    vi.clearAllMocks();
    setupMocks();
    const clearAllBtn = screen.getByRole("button", { name: /clear all/i });
    fireEvent.click(clearAllBtn);

    await waitFor(() => {
      expect(searchJobs).toHaveBeenCalled();
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.employmentType == null || (Array.isArray(lastCall.employmentType) && lastCall.employmentType.length === 0)).toBe(true);
      expect(lastCall.careerLevel == null || (Array.isArray(lastCall.careerLevel) && lastCall.careerLevel.length === 0)).toBe(true);
    });

    // All chips should be gone
    const chips = document.querySelectorAll(".chip.active");
    expect([...chips].some((c) => c.textContent.includes("Full-time"))).toBe(false);
    expect([...chips].some((c) => c.textContent.includes("Senior"))).toBe(false);
  });

  // FE-EMP-16: saved-filter round-trip persists+restores both
  it("FE-EMP-16 saved-filter round-trip persists and restores employmentTypes and careerLevels", async () => {
    // Story #523: presets now come from listSavedFilters, not localStorage.
    const savedFilterDto = {
      id: "sf-1",
      name: "My filter",
      filters: { employmentType: ["freelance"], careerLevel: ["principal"] },
    };
    await renderScreen({ authed: true }, [savedFilterDto]);
    vi.clearAllMocks();
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    // Open saved filters dropdown (visible when authed=true and savedFilters.length > 0)
    const savedFiltersTrigger = await waitFor(() => screen.getByText("Saved filters"));
    fireEvent.click(savedFiltersTrigger);

    const filterItem = await waitFor(() => screen.getByText("My filter"));
    fireEvent.click(filterItem);

    // After applying, searchJobs must carry both filter dimensions
    await waitFor(() => {
      expect(searchJobs).toHaveBeenCalled();
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.employmentType).toEqual(["freelance"]);
      expect(lastCall.careerLevel).toEqual(["principal"]);
    });
  });

  // FE-EMP-17: empty employmentTypes bucket => Employment Type control still rendered (fix #61)
  it("FE-EMP-17 empty employmentTypes bucket: Employment Type field label is still present", async () => {
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue({
      ...DEFAULT_FACETS,
      employmentTypes: [],
    });
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");
    render(
      <JobSearchScreen
        goto={vi.fn()} onSaveToggle={vi.fn()} savedIds={new Set()}
        openJob={vi.fn()} appliedJobIds={new Set()} authed={false} openSearch={vi.fn()}
      />
    );

    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    // Fixed behavior: field renders unconditionally even with empty options
    expect(screen.getByText("Employment type")).toBeInTheDocument();
    expect(screen.getByText("All employment types")).toBeInTheDocument();
  });

  // FE-EMP-18: empty careerLevels bucket => Career Level control still rendered (fix #61)
  it("FE-EMP-18 empty careerLevels bucket: Career Level field label is still present", async () => {
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue({
      ...DEFAULT_FACETS,
      careerLevels: [],
    });
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");
    render(
      <JobSearchScreen
        goto={vi.fn()} onSaveToggle={vi.fn()} savedIds={new Set()}
        openJob={vi.fn()} appliedJobIds={new Set()} authed={false} openSearch={vi.fn()}
      />
    );

    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    // Fixed behavior: field renders unconditionally even with empty options
    expect(screen.getByText("Career level")).toBeInTheDocument();
    expect(screen.getByText("All career levels")).toBeInTheDocument();
  });

  // FE-EMP-19: when both facet buckets are empty, both controls still render (fix #61)
  it("FE-EMP-19 when both facet buckets are empty, both field labels are still present", async () => {
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue({
      ...DEFAULT_FACETS,
      employmentTypes: [],
      careerLevels: [],
    });
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");
    render(
      <JobSearchScreen
        goto={vi.fn()} onSaveToggle={vi.fn()} savedIds={new Set()}
        openJob={vi.fn()} appliedJobIds={new Set()} authed={false} openSearch={vi.fn()}
      />
    );

    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    // Fixed behavior: both fields render unconditionally
    expect(screen.getByText("Employment type")).toBeInTheDocument();
    expect(screen.getByText("Career level")).toBeInTheDocument();
    expect(screen.getByText("All employment types")).toBeInTheDocument();
    expect(screen.getByText("All career levels")).toBeInTheDocument();
  });
});

// ── Multiple locations (story #1, #293): QAE-UI-DISPLAY-5 ──────────────────────────────

// Story #523 removed the compensation range filter, which also removed an incidental
// second facets fetch that used to fire ~300ms after mount as the (now-deleted) comp
// bounds debounce settled. These tests relied on that quirk to pick up an overridden
// facets mock without a real interaction; they now trigger an explicit, non-debounced
// filter change (Posted) so getJobFacets is called again and the override takes effect.
function triggerFacetsRefetch() {
  const postedSelect = getPostedSelect();
  const next = postedSelect.value === "week" ? "month" : "week";
  fireEvent.change(postedSelect, { target: { value: next } });
}

describe("JobSearchScreen location filter — multiple locations (QAE-UI-DISPLAY-5)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  // QAE-UI-DISPLAY-5: selecting a country that a posting only has as a non-primary
  // opening (per the backend's "any opening matches" rule) still surfaces that posting
  // in the results, using the existing location MultiSelect — no new UI control.
  it("QAE-UI-DISPLAY-5 selecting a non-primary-only location returns the matching posting", async () => {
    await renderScreen({}, {
      // facets already include "Netherlands" (backend sources this from any opening)
    });

    setupMocks({
      locations: [
        { value: "Spain", count: 7 },
        { value: "Netherlands", count: 1 },
      ],
    });
    triggerFacetsRefetch();
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(2));

    const locationTrigger = screen.getByText("All locations");
    fireEvent.click(locationTrigger);
    await waitFor(() => screen.getByText("Netherlands"));

    // Wire the next searchJobs call to return the multi-opening posting matched via
    // its non-primary Netherlands opening.
    searchJobs.mockResolvedValue({
      items: [{
        id: "job-8",
        title: "Multi-Location Backend Engineer",
        url: "https://example.com/job-8",
        location: "Barcelona, Spain",
        locations: [
          { country: "Spain", city: "Barcelona", primary: true },
          { country: "Netherlands", city: "Amsterdam", primary: false },
        ],
        company: { name: "Stripe" },
      }],
      total: 1,
      page: 0,
      totalPages: 1,
    });

    fireEvent.click(screen.getByText("Netherlands"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => {
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.location).toEqual(["Netherlands"]);
    });

    await waitFor(() => {
      expect(screen.getByText("Multi-Location Backend Engineer")).toBeInTheDocument();
    });
  });

  // TC-319-UI-DISPLAY-01 (AC-319-DISPLAY-1): selecting two locations sends both as a
  // repeatable `location` param and surfaces every posting matching either one, each
  // rendered as exactly one card (no duplication for a posting matching both dimensions).
  it("TC-319-UI-DISPLAY-01 selecting two locations returns posts matching either, each exactly once", async () => {
    await renderScreen();

    setupMocks({
      locations: [
        { value: "Spain", count: 7 },
        { value: "Netherlands", count: 1 },
      ],
    });
    triggerFacetsRefetch();
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(2));

    const locationTrigger = screen.getByText("All locations");
    fireEvent.click(locationTrigger);
    await waitFor(() => screen.getByText("Netherlands"));

    // Job A: primary Spain only, no additional openings.
    // Job B: row-8-shaped, primary Spain + additional Netherlands.
    searchJobs.mockResolvedValue({
      items: [
        {
          id: "job-a",
          title: "Spain Only Engineer",
          url: "https://example.com/job-a",
          location: "Madrid, Spain",
          locations: [{ country: "Spain", city: "Madrid", primary: true }],
          company: { name: "Acme Corp" },
        },
        {
          id: "job-8",
          title: "Multi-Location Backend Engineer",
          url: "https://example.com/job-8",
          location: "Barcelona, Spain",
          locations: [
            { country: "Spain", city: "Barcelona", primary: true },
            { country: "Netherlands", city: "Amsterdam", primary: false },
          ],
          company: { name: "Stripe" },
        },
      ],
      total: 2,
      page: 0,
      totalPages: 1,
    });

    fireEvent.click(screen.getByText("Spain"));
    fireEvent.click(screen.getByText("Netherlands"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => {
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.location).toEqual(expect.arrayContaining(["Spain", "Netherlands"]));
      expect(lastCall.location).toHaveLength(2);
    });

    await waitFor(() => {
      expect(screen.getByText("Spain Only Engineer")).toBeInTheDocument();
      expect(screen.getAllByText("Multi-Location Backend Engineer")).toHaveLength(1);
    });
  });

  // TC-319-UI-DISPLAY-02 (AC-319-DISPLAY-2): extends QAE-UI-DISPLAY-5 — filtering by a
  // posting's non-primary opening returns it AND its card still shows the unchanged
  // primary location plus the "+N more" affordance, proving the filter match and the
  // display are independent (a non-primary match does not change what the card displays).
  it("TC-319-UI-DISPLAY-02 non-primary-only match still shows the unchanged primary location and +N affordance", async () => {
    await renderScreen();

    setupMocks({
      locations: [
        { value: "Spain", count: 7 },
        { value: "Netherlands", count: 1 },
      ],
    });
    triggerFacetsRefetch();
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(2));

    const locationTrigger = screen.getByText("All locations");
    fireEvent.click(locationTrigger);
    await waitFor(() => screen.getByText("Netherlands"));

    searchJobs.mockResolvedValue({
      items: [{
        id: "job-8",
        title: "Multi-Location Backend Engineer",
        url: "https://example.com/job-8",
        location: "Barcelona, Spain",
        locations: [
          { country: "Spain", city: "Barcelona", primary: true },
          { country: "Netherlands", city: "Amsterdam", primary: false },
        ],
        company: { name: "Stripe" },
      }],
      total: 1,
      page: 0,
      totalPages: 1,
    });

    fireEvent.click(screen.getByText("Netherlands"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => {
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.location).toEqual(["Netherlands"]);
    });

    await waitFor(() => {
      expect(screen.getByText("Multi-Location Backend Engineer")).toBeInTheDocument();
    });

    // The card shows the posting's unchanged primary location, not the matched one.
    expect(screen.getByText("Barcelona, Spain")).toBeInTheDocument();
    expect(screen.queryByText("Amsterdam, Netherlands")).not.toBeInTheDocument();
    // ...plus the "+N more" affordance (story #1's JobRowLocations mechanism) indicating
    // the additional, non-primary opening that made this posting match.
    expect(screen.getByTestId("location-more")).toHaveTextContent("+1");
  });

  // TC-319-UI-DISPLAY-03 (AC-319-DISPLAY-3): the location filter dropdown's counts are a
  // verbatim passthrough of `facets.locations` (backend-computed one-per-post), never a
  // client-recomputed value derived from counting `locations[]` entries locally.
  it("TC-319-UI-DISPLAY-03 location dropdown renders the backend's one-per-post facet count verbatim", async () => {
    await renderScreen();

    setupMocks({
      locations: [
        { value: "Spain", count: 2 },
      ],
    });
    triggerFacetsRefetch();
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(2));
    // Even if a locally-held job list carried 3 total `locations[]` entries across two
    // postings (one 2-opening Spain posting + one 1-opening Spain posting), the dropdown
    // must render the backend's count (2 postings), not a locally-recomputed 3.
    searchJobs.mockResolvedValue({
      items: [
        {
          id: "job-8",
          title: "Multi-Location Backend Engineer",
          url: "https://example.com/job-8",
          location: "Barcelona, Spain",
          locations: [
            { country: "Spain", city: "Barcelona", primary: true },
            { country: "Spain", city: "Madrid", primary: false },
          ],
          company: { name: "Stripe" },
        },
        {
          id: "job-9",
          title: "Spain Only Engineer",
          url: "https://example.com/job-9",
          location: "Valencia, Spain",
          locations: [{ country: "Spain", city: "Valencia", primary: true }],
          company: { name: "Acme Corp" },
        },
      ],
      total: 2,
      page: 0,
      totalPages: 1,
    });

    const locationTrigger = screen.getByText("All locations");
    fireEvent.click(locationTrigger);

    await waitFor(() => {
      const spainRow = screen.getByText("Spain").closest("div");
      expect(within(spainRow).getByText("2")).toBeInTheDocument();
    });
  });
});

// ── Story #331 (sub-issue #381): job search defaults to "Past 3 days" ──────────

function getPostedSelect() {
  const selects = document.querySelectorAll("select");
  return [...selects].find((s) => [...s.options].some((o) => o.text === "Past 3 days"));
}

describe("JobSearchScreen 3-day default (story #331)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  // TC-331-36 (AC-331-14): first load defaults to "Past 3 days", not "Any time".
  it("TC-331-36 first load defaults the Posted select to Past 3 days and requests postedWithin=3d", async () => {
    await renderScreen();

    const postedSelect = getPostedSelect();
    expect(postedSelect).toBeDefined();
    expect(postedSelect.value).toBe("3days");

    const firstCall = searchJobs.mock.calls[0][0];
    expect(firstCall.postedWithin).toBe("3d");
  });

  // TC-331-37 (AC-331-15): the default's effect is entirely the outbound request filter;
  // the UI does not re-filter the (mocked) API response by age on top of it.
  it("TC-331-37 renders every item from the mocked 3-day response with no client-side re-filtering", async () => {
    vi.clearAllMocks();
    setupMocks({}, {
      items: [
        {
          id: "job-recent",
          title: "Recent Frontend Engineer",
          url: "https://example.com/job-recent",
          location: "Berlin, Germany",
          locations: [{ country: "Germany", city: "Berlin", primary: true }],
          firstSeenAt: new Date().toISOString(),
          company: { name: "Acme Corp" },
        },
        {
          // Even though this item's firstSeenAt is far older than 3 days, the UI trusts
          // the (mocked) API response as-is: the 3-day narrowing is the outbound request
          // filter only, never a duplicate client-side age filter.
          id: "job-old-per-mock",
          title: "Old-Dated Backend Engineer",
          url: "https://example.com/job-old-per-mock",
          location: "Madrid, Spain",
          locations: [{ country: "Spain", city: "Madrid", primary: true }],
          firstSeenAt: "2020-01-01T00:00:00Z",
          company: { name: "Stripe" },
        },
      ],
      total: 2,
      page: 0,
      totalPages: 1,
    });

    const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");
    render(
      <JobSearchScreen
        goto={vi.fn()} onSaveToggle={vi.fn()} savedIds={new Set()}
        openJob={vi.fn()} appliedJobIds={new Set()} authed={false} openSearch={vi.fn()}
      />
    );

    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    await waitFor(() => {
      expect(screen.getByText("Recent Frontend Engineer")).toBeInTheDocument();
      expect(screen.getByText("Old-Dated Backend Engineer")).toBeInTheDocument();
    });
  });

  // TC-331-38 (AC-331-16): switching to "Any time" omits postedWithin and clears the chip.
  it("TC-331-38 switching Posted to Any time omits postedWithin and removes the Posted chip", async () => {
    await renderScreen();
    vi.clearAllMocks();
    setupMocks();

    const postedSelect = getPostedSelect();
    fireEvent.change(postedSelect, { target: { value: "any" } });

    await waitFor(() => {
      expect(searchJobs).toHaveBeenCalled();
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.postedWithin).toBeUndefined();
    });

    const chips = document.querySelectorAll(".chip.active");
    expect([...chips].some((c) => c.textContent.includes("Posted:"))).toBe(false);
  });

  // TC-331-39 (AC-331-17): switching to Today/Past week/Past 30 days still works as before.
  it.each([
    ["today", "today"],
    ["week", "week"],
    ["month", "month"],
  ])("TC-331-39 switching Posted to %s sends postedWithin=%s and re-fetches", async (uiValue, expectedParam) => {
    await renderScreen();
    vi.clearAllMocks();
    setupMocks();

    const postedSelect = getPostedSelect();
    fireEvent.change(postedSelect, { target: { value: uiValue } });

    await waitFor(() => {
      expect(searchJobs).toHaveBeenCalled();
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.postedWithin).toBe(expectedParam);
    });
  });

  // TC-331-40 (AC-331-18/24): applying a saved filter with a non-default Posted value
  // overrides the "Past 3 days" cold-start default, for both a concrete value ("month")
  // and the explicit "Any time" (omitted postedWithin) case.
  it("TC-331-40 applying a saved filter with posted='month' overrides the 3-day default", async () => {
    // Story #523: presets now come from listSavedFilters, not localStorage.
    const savedFilterDto = { id: "sf-month", name: "Monthly filter", filters: { postedWithin: "month" } };
    await renderScreen({ authed: true }, [savedFilterDto]);
    vi.clearAllMocks();
    setupMocks();

    const savedFiltersTrigger = await waitFor(() => screen.getByText("Saved filters"));
    fireEvent.click(savedFiltersTrigger);
    const filterItem = await waitFor(() => screen.getByText("Monthly filter"));
    fireEvent.click(filterItem);

    await waitFor(() => {
      expect(searchJobs).toHaveBeenCalled();
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.postedWithin).toBe("month");
    });

    const postedSelect = getPostedSelect();
    expect(postedSelect.value).toBe("month");
  });

  it("TC-331-40b applying a saved filter with posted='any' respects the explicit Any time selection", async () => {
    // Story #523: presets now come from listSavedFilters, not localStorage.
    const savedFilterDto = { id: "sf-any", name: "Any time filter", filters: {} };
    await renderScreen({ authed: true }, [savedFilterDto]);
    vi.clearAllMocks();
    setupMocks();

    const savedFiltersTrigger = await waitFor(() => screen.getByText("Saved filters"));
    fireEvent.click(savedFiltersTrigger);
    const filterItem = await waitFor(() => screen.getByText("Any time filter"));
    fireEvent.click(filterItem);

    await waitFor(() => {
      expect(searchJobs).toHaveBeenCalled();
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.postedWithin).toBeUndefined();
    });

    const postedSelect = getPostedSelect();
    expect(postedSelect.value).toBe("any");
  });

  // TC-331-41: cold-start chip regression lock, a "Posted: 3days" chip IS expected on
  // the new default (the chip condition is unchanged: postedFilter !== "any"). Do not
  // "fix" this by hiding the chip on the new default; it is intentional per the PDA/ADR.
  it("TC-331-41 cold start renders a Posted: 3days active-filter chip (intentional, not a bug)", async () => {
    await renderScreen();

    const chips = document.querySelectorAll(".chip.active");
    expect([...chips].some((c) => c.textContent.includes("Posted: 3days"))).toBe(true);
  });
});

// ── Story #539: job-search scroll model (DOM cases) ─────────────────────────
// Cases: TC-539-06, TC-539-07, TC-539-08, TC-539-10, TC-539-11
// Spec: docs/specs/539-job-search-scroll.md · Cases: docs/testing/539-job-search-scroll-cases.md

describe("JobSearchScreen scroll model (story #539)", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  function makeJob(id) {
    return {
      id,
      title: `Job ${id}`,
      url: `https://example.com/${id}`,
      location: "Madrid, Spain",
      locations: [{ country: "Spain", city: "Madrid", primary: true }],
      company: { name: "Acme Corp" },
    };
  }

  // TC-539-06: active filter chips and the sort control live outside the results list.
  it("TC-539-06 active filter chips and the sort select are not contained within the results list", async () => {
    await renderScreen();
    vi.clearAllMocks();
    setupMocks({}, { items: [makeJob("j1")], total: 1, page: 0, totalPages: 1 });

    // Apply a filter (the Posted select is the first native <select> on the page) so at
    // least one active chip renders.
    fireEvent.change(document.querySelector("select"), { target: { value: "today" } });
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    const resultsList = document.querySelector('[data-testid="results-list"]');
    expect(resultsList).not.toBeNull();

    const chip = document.querySelector(".chip.active");
    expect(chip).not.toBeNull();
    expect(resultsList.contains(chip)).toBe(false);

    const sortSelect = [...document.querySelectorAll("select")].find((s) =>
      s.querySelector('option[value="newest"]')
    );
    expect(sortSelect).toBeTruthy();
    expect(resultsList.contains(sortSelect)).toBe(false);
  });

  // TC-539-07: neither pager instance (top or bottom) is contained within the results list.
  it("TC-539-07 neither the top nor the bottom pager is contained within the results list", async () => {
    await renderScreen();
    vi.clearAllMocks();
    setupMocks({}, { items: [makeJob("j1")], total: 1, page: 0, totalPages: 1 });

    // Trigger a refetch that yields results via a filter change (the Posted select is the
    // first native <select> on the page).
    fireEvent.change(document.querySelector("select"), { target: { value: "today" } });
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByText("Job j1")).toBeInTheDocument());

    const resultsList = document.querySelector('[data-testid="results-list"]');
    const pagerButtons = screen.getAllByLabelText("Previous page");
    expect(pagerButtons.length).toBe(2);
    pagerButtons.forEach((btn) => {
      expect(resultsList.contains(btn)).toBe(false);
    });
  });

  // TC-539-08: in collapsed (single-column) mode, filters and the results list are still
  // normal-flow siblings; no JS-applied inline sticky/fixed position on either.
  it("TC-539-08 collapsed layout keeps filters and results list as normal-flow siblings with no inline sticky/fixed position", async () => {
    const originalInnerWidth = window.innerWidth;
    Object.defineProperty(window, "innerWidth", { writable: true, configurable: true, value: 700 });

    vi.clearAllMocks();
    setupMocks();
    const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");
    render(
      <JobSearchScreen
        goto={vi.fn()} onSaveToggle={vi.fn()} savedIds={new Set()} openJob={vi.fn()}
        appliedJobIds={new Set()} authed={false} openSearch={vi.fn()}
      />
    );
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    const layout = document.querySelector(".search-layout");
    expect(layout.classList.contains("search-layout--collapsed")).toBe(true);

    const filters = document.querySelector(".search-filters");
    const resultsList = document.querySelector('[data-testid="results-list"]');
    expect(filters).not.toBeNull();
    expect(resultsList).not.toBeNull();
    expect(["sticky", "fixed"]).not.toContain(filters.style.position);
    expect(["sticky", "fixed"]).not.toContain(resultsList.style.position);

    Object.defineProperty(window, "innerWidth", { writable: true, configurable: true, value: originalInnerWidth });
  });

  // TC-539-10: empty results render Empty inside the results list, no bottom pager.
  it("TC-539-10 empty results render the empty state inside the results list with no bottom pager", async () => {
    await renderScreen(); // default EMPTY_SEARCH: items: [], total: 0

    const resultsList = document.querySelector('[data-testid="results-list"]');
    const empty = resultsList.querySelector(".empty");
    expect(empty).not.toBeNull();
    // Only the top pager renders (it is unconditional): the bottom pager's total > 0
    // guard means there is exactly one "Previous page" button, not two.
    expect(screen.queryAllByLabelText("Previous page").length).toBe(1);
  });

  // TC-539-11: results-list keeps the identical class list regardless of row count.
  it("TC-539-11 the results list keeps the identical className for a short page and a full page", async () => {
    await renderScreen();
    vi.clearAllMocks();
    setupMocks({}, { items: [makeJob("a"), makeJob("b"), makeJob("c")], total: 3, page: 0, totalPages: 1 });
    fireEvent.change(document.querySelector("select"), { target: { value: "today" } });
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByText("Job a")).toBeInTheDocument());
    const shortClassName = document.querySelector('[data-testid="results-list"]').className;

    vi.clearAllMocks();
    setupMocks({}, {
      items: Array.from({ length: 20 }, (_, i) => makeJob(`p${i}`)),
      total: 20, page: 0, totalPages: 1,
    });
    fireEvent.change(document.querySelector("select"), { target: { value: "week" } });
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByText("Job p0")).toBeInTheDocument());
    const fullClassName = document.querySelector('[data-testid="results-list"]').className;

    expect(fullClassName).toBe(shortClassName);
  });
});
