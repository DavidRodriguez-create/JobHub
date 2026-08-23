/**
 * Component tests for reactive (drill-down) facets — Story #4
 * Cases: FE-F03..FE-F08
 *
 * Strategy:
 * - Mock api/jobs.js to control both searchJobs and getJobFacets
 * - Mock api/config.js so USE_API=true
 * - Mock data/mockData.js (empty store)
 * - Verify facets re-fetch on filter change with active filter state,
 *   count badges update, selections survive zero-count response,
 *   debounce coalesces rapid changes, saved-filter initial load,
 *   and language remains single-select.
 */
import React from "react";
import { render, screen, waitFor, fireEvent, act, cleanup, within } from "@testing-library/react";
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

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

vi.mock("../../components/WritingLoader.jsx", () => ({
  default: ({ label }) => <div data-testid="writing-loader">{label}</div>,
}));

vi.mock("../../components/RichText.jsx", () => ({
  default: ({ text }) => <div>{text}</div>,
}));

const { searchJobs, getJobFacets, listSavedFilters } = await import("../../api/jobs.js");
const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");

// ── Shared fixtures ────────────────────────────────────────────────────────

const DEFAULT_FACETS = {
  companies: [
    { value: "Acme Corp", count: 5 },
    { value: "Beta Ltd", count: 3 },
  ],
  locations: [
    { value: "Spain", count: 3 },
    { value: "France", count: 2 },
  ],
  languages: [
    { value: "English", count: 8 },
    { value: "Spanish", count: 2 },
  ],
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

const EMPTY_SEARCH = { items: [], total: 0, page: 0, totalPages: 0 };

function setupMocks(facetOverride = {}, searchOverride = {}, savedFiltersOverride = []) {
  getJobFacets.mockResolvedValue({ ...DEFAULT_FACETS, ...facetOverride });
  searchJobs.mockResolvedValue({ ...EMPTY_SEARCH, ...searchOverride });
  listSavedFilters.mockResolvedValue(savedFiltersOverride);
}

const DEFAULT_PROPS = {
  goto: vi.fn(),
  onSaveToggle: vi.fn(),
  savedIds: new Set(),
  openJob: vi.fn(),
  appliedJobIds: new Set(),
  authed: false,
  openSearch: vi.fn(),
};

async function renderAndSettle(props = {}) {
  vi.clearAllMocks();
  setupMocks();
  const result = render(<JobSearchScreen {...DEFAULT_PROPS} {...props} />);
  await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
  await waitFor(() => expect(searchJobs).toHaveBeenCalled());
  return result;
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  localStorage.clear();
});

// ── FE-F03: changing a filter triggers a NEW getJobFacets carrying that filter ──
describe("FE-F03 changing a filter triggers getJobFacets with the updated filter state", () => {
  it("selecting a company calls getJobFacets with company in the request", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Open Company multiselect, tick "Acme Corp", then Apply
    const companyTrigger = screen.getByText("All companies");
    fireEvent.click(companyTrigger);
    await waitFor(() => screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    // getJobFacets should be called again after the filter is applied
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    // The call must carry the selected company
    const calls = getJobFacets.mock.calls;
    const lastCallArg = calls[calls.length - 1][0];
    expect(Array.isArray(lastCallArg.company)).toBe(true);
    expect(lastCallArg.company).toContain("Acme Corp");
  });

  it("selecting a location calls getJobFacets with location in the request", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const locationTrigger = screen.getByText("All locations");
    fireEvent.click(locationTrigger);
    await waitFor(() => screen.getByText("Spain"));
    fireEvent.click(screen.getByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    const lastCallArg = getJobFacets.mock.calls[getJobFacets.mock.calls.length - 1][0];
    expect(Array.isArray(lastCallArg.location)).toBe(true);
    expect(lastCallArg.location).toContain("Spain");
  });
});

// ── FE-F04: count badges update after a filter change ──
describe("FE-F04 count badges update to reflect the facets response after a filter change", () => {
  it("company count badge changes to the new facets response value", async () => {
    await renderAndSettle();

    // Open company dropdown — initial counts from DEFAULT_FACETS
    const companyTrigger = screen.getByText("All companies");
    fireEvent.click(companyTrigger);
    await waitFor(() => screen.getByText("Acme Corp"));
    // Initially Acme Corp count = 5
    expect(screen.getByText("5")).toBeInTheDocument();
    // Close by clicking trigger again (toggle)
    fireEvent.click(companyTrigger);
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: /^Apply/ })).not.toBeInTheDocument()
    );

    // Change facets mock to return updated count for Acme Corp
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue({
      ...DEFAULT_FACETS,
      companies: [
        { value: "Acme Corp", count: 2 },
        { value: "Beta Ltd", count: 1 },
      ],
    });
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    // Apply a location to trigger re-fetch
    const locationTrigger = screen.getByText("All locations");
    fireEvent.click(locationTrigger);
    await waitFor(() => screen.getByText("Spain"));
    fireEvent.click(screen.getByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    // Wait for getJobFacets to be called with updated facets
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    // Open company dropdown again — should show updated counts
    const companyTrigger2 = screen.getByText("All companies");
    fireEvent.click(companyTrigger2);
    await waitFor(() => {
      // Count for Acme Corp should now be 2
      const counts = screen.getAllByText("2");
      expect(counts.length).toBeGreaterThanOrEqual(1);
    });
  });
});

// ── FE-F05: a selected value omitted from the facets response stays selected ──
describe("FE-F05 a selected value absent from facets response stays selected", () => {
  it("selected company stays in the multiselect even if facets response omits it (zero count)", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Apply "Acme Corp"
    const companyTrigger = screen.getByText("All companies");
    fireEvent.click(companyTrigger);
    await waitFor(() => screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    // Now mock facets to omit "Acme Corp" entirely (zero count → not returned)
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue({
      ...DEFAULT_FACETS,
      companies: [{ value: "Beta Ltd", count: 3 }], // Acme Corp absent
    });
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    // Apply a location to trigger re-fetch with updated facets
    const locationTrigger = screen.getByText("All locations");
    fireEvent.click(locationTrigger);
    await waitFor(() => screen.getByText("Spain"));
    fireEvent.click(screen.getByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    // The chip for "Acme Corp" must still be present (selection was not wiped)
    await waitFor(() => {
      const chips = document.querySelectorAll(".chip.active");
      expect([...chips].some((c) => c.textContent.includes("Acme Corp"))).toBe(true);
    });
  });
});

// ── FE-F06: rapid filter changes are debounced → only one getJobFacets call ──
describe("FE-F06 rapid filter changes are debounced into a single getJobFacets call", () => {
  it("typing rapidly in keyword fires getJobFacets only once after debounce settles", async () => {
    vi.useFakeTimers();
    try {
      setupMocks();
      render(<JobSearchScreen {...DEFAULT_PROPS} />);

      // Flush the initial async effects that fire on mount
      // We advance timers and flush all pending promises iteratively
      for (let i = 0; i < 5; i++) {
        await act(async () => {
          vi.advanceTimersByTime(400);
          await Promise.resolve();
        });
      }

      // Now getJobFacets should have been called for the initial load
      expect(getJobFacets).toHaveBeenCalled();
      vi.clearAllMocks();
      setupMocks();

      // Type each character rapidly — debounce is 300 ms
      const input = screen.getByPlaceholderText("Title, company…");
      await act(async () => {
        fireEvent.change(input, { target: { value: "r" } });
        fireEvent.change(input, { target: { value: "re" } });
        fireEvent.change(input, { target: { value: "rea" } });
        fireEvent.change(input, { target: { value: "reac" } });
        fireEvent.change(input, { target: { value: "react" } });
      });

      // Before the debounce fires, getJobFacets must NOT have been called again
      expect(getJobFacets).not.toHaveBeenCalled();

      // Advance time past the 300 ms debounce
      await act(async () => {
        vi.advanceTimersByTime(350);
        await Promise.resolve();
      });

      // Allow any pending microtasks to flush
      await act(async () => {
        await Promise.resolve();
      });

      // Only one call (all keystrokes coalesced)
      expect(getJobFacets).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });
});

// ── FE-F07: initial load with a pre-applied saved filter calls facets WITH that state ──
describe("FE-F07 initial load with a pre-applied saved filter fires facets WITH that state", () => {
  it("applying a saved filter immediately fires facets with that filter state", async () => {
    // Story #523: presets now come from listSavedFilters, not localStorage.
    const saved = {
      id: "sf-1",
      name: "My filter",
      filters: { company: ["Acme Corp"], location: ["Spain"] },
    };
    setupMocks({}, {}, [saved]);
    render(<JobSearchScreen {...DEFAULT_PROPS} authed={true} />);

    // Wait for initial mount to settle
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
    vi.clearAllMocks();
    setupMocks();

    // Apply the saved filter
    const savedFiltersTrigger = screen.getByText("Saved filters");
    fireEvent.click(savedFiltersTrigger);
    const filterItem = await waitFor(() => screen.getByText("My filter"));
    fireEvent.click(filterItem);

    // facets must be called with the saved filter state
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
    const calls = getJobFacets.mock.calls;
    const arg = calls[calls.length - 1][0];
    expect(arg.company).toContain("Acme Corp");
    expect(arg.location).toContain("Spain");
  });
});

// ── FE-F09: selecting a second language REPLACES the first (no union) ──
describe("FE-F09 selecting a second language replaces the first (single-select, no union)", () => {
  it("selecting English then Spanish calls getJobFacets with language=['Spanish'] only", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const selects = document.querySelectorAll("select");
    const languageSelect = [...selects].find(
      (s) => [...s.options].some((o) => o.text === "All languages")
    );
    expect(languageSelect).toBeDefined();

    // First selection: English
    fireEvent.change(languageSelect, { target: { value: "English" } });
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    // Second selection: Spanish — must REPLACE English, not union
    vi.clearAllMocks();
    setupMocks();
    fireEvent.change(languageSelect, { target: { value: "Spanish" } });

    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    const calls = getJobFacets.mock.calls;
    const arg = calls[calls.length - 1][0];

    // Must be exactly ['Spanish'] — not ['English', 'Spanish']
    expect(Array.isArray(arg.language)).toBe(true);
    expect(arg.language).toEqual(["Spanish"]);
    expect(arg.language).not.toContain("English");
  });
});

// ── FE-F08: language stays single-select; re-fetch sends language=[one] ──
describe("FE-F08 language is single-select and re-fetch sends language=[selectedLanguage]", () => {
  it("the language control is a single <select>, not a MultiSelect", async () => {
    await renderAndSettle();

    // The language field label must be present
    expect(screen.getByText("Language")).toBeInTheDocument();

    // The language select must exist with "All languages" as one of its options
    const selects = document.querySelectorAll("select");
    const languageSelect = [...selects].find(
      (s) => [...s.options].some((o) => o.text === "All languages")
    );
    expect(languageSelect).toBeDefined();
    // Native single-select (no multiple attribute)
    expect(languageSelect.multiple).toBe(false);
  });

  it("selecting a language calls getJobFacets with language=[thatLanguage]", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Find the language select and pick "Spanish"
    const selects = document.querySelectorAll("select");
    const languageSelect = [...selects].find(
      (s) => [...s.options].some((o) => o.text === "All languages")
    );
    expect(languageSelect).toBeDefined();

    fireEvent.change(languageSelect, { target: { value: "Spanish" } });

    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    const calls = getJobFacets.mock.calls;
    const arg = calls[calls.length - 1][0];
    // language should be an array with one element (not multi-select)
    expect(Array.isArray(arg.language)).toBe(true);
    expect(arg.language).toEqual(["Spanish"]);
  });

  it("selecting a language does NOT send multiple languages", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const selects = document.querySelectorAll("select");
    const languageSelect = [...selects].find(
      (s) => [...s.options].some((o) => o.text === "All languages")
    );

    fireEvent.change(languageSelect, { target: { value: "English" } });
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    const arg = getJobFacets.mock.calls[getJobFacets.mock.calls.length - 1][0];
    // Must be a single-element array
    expect(arg.language).toHaveLength(1);
    expect(arg.language[0]).toBe("English");
  });
});
