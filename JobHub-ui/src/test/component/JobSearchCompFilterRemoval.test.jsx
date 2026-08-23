/**
 * Component tests for the compensation-filter removal, story #523.
 * Cases: TC-523-C01..C06 (docs/qa/523-comp-filter-removal-and-per-user-saved-filters-test-cases.md).
 * Mirrors JobSearchScreen.test.jsx's mocking style, USE_API=true.
 */
import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, afterEach } from "vitest";

vi.mock("../../api/config.js", () => ({ USE_API: true }));

vi.mock("../../api/jobs.js", () => ({
  searchJobs: vi.fn(),
  getJobFacets: vi.fn(),
  listSavedFilters: vi.fn(),
  createSavedFilter: vi.fn(),
  deleteSavedFilter: vi.fn(),
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

const DEFAULT_FACETS = {
  companies: [{ value: "Acme Corp", count: 5 }],
  locations: [{ value: "Spain", count: 3 }],
  languages: [{ value: "English", count: 8 }, { value: "Spanish", count: 2 }],
  employmentTypes: [{ value: "full-time", count: 12 }],
  careerLevels: [{ value: "senior", count: 7 }],
  compensationMin: 30000,
  compensationMax: 150000,
};

const EMPTY_SEARCH = { items: [], total: 0, page: 0, totalPages: 0 };

const DEFAULT_PROPS = {
  goto: vi.fn(),
  onSaveToggle: vi.fn(),
  savedIds: new Set(),
  openJob: vi.fn(),
  appliedJobIds: new Set(),
  authed: false,
  openSearch: vi.fn(),
};

function setupMocks(facetOverride = {}, searchOverride = {}, savedFiltersOverride = []) {
  getJobFacets.mockResolvedValue({ ...DEFAULT_FACETS, ...facetOverride });
  searchJobs.mockResolvedValue({ ...EMPTY_SEARCH, ...searchOverride });
  listSavedFilters.mockResolvedValue(savedFiltersOverride);
}

async function renderAndSettle(props = {}) {
  vi.clearAllMocks();
  setupMocks();
  const result = render(<JobSearchScreen {...DEFAULT_PROPS} {...props} />);
  await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
  await waitFor(() => expect(searchJobs).toHaveBeenCalled());
  return result;
}

afterEach(() => {
  vi.clearAllMocks();
});

// TC-523-C01 (AC-523-01)
describe("TC-523-C01: no compensation control anywhere in the filter panel", () => {
  it("renders no comp-slider-fill testid, no range input, and no 'compensation' text", async () => {
    await renderAndSettle();
    expect(document.querySelector('[data-testid="comp-slider-fill"]')).toBeNull();
    const filtersColumn = document.querySelector(".search-filters");
    expect(filtersColumn.querySelector('input[type="range"]')).toBeNull();
    expect(screen.queryByText(/compensation/i)).toBeNull();
  });
});

// TC-523-C02 (AC-523-02)
describe("TC-523-C02: no active-filter chip can ever represent a compensation range", () => {
  it("with several other filters active, no chip text matches the old €Nk format", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const companyTrigger = screen.getByText("All companies");
    fireEvent.click(companyTrigger);
    await waitFor(() => screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    const chips = document.querySelectorAll(".chip.active");
    expect([...chips].some((c) => /€\d+k/.test(c.textContent))).toBe(false);
  });
});

// TC-523-C03 (AC-523-03)
describe("TC-523-C03: neither searchJobs nor getJobFacets ever carries a compensation key", () => {
  it("across a matrix of filter interactions, compensationMin/compensationMax are absent, not undefined", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const input = screen.getByPlaceholderText("Title, company…");
    fireEvent.change(input, { target: { value: "engineer" } });

    const postedSelect = [...document.querySelectorAll("select")].find((s) =>
      [...s.options].some((o) => o.text === "Past week")
    );
    fireEvent.change(postedSelect, { target: { value: "week" } });

    const companyTrigger = screen.getByText("All companies");
    fireEvent.click(companyTrigger);
    await waitFor(() => screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => expect(searchJobs).toHaveBeenCalled(), { timeout: 2000 });
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    const lastSearchCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
    const lastFacetsCall = getJobFacets.mock.calls[getJobFacets.mock.calls.length - 1][0];
    expect(lastSearchCall).not.toHaveProperty("compensationMin");
    expect(lastSearchCall).not.toHaveProperty("compensationMax");
    expect(lastFacetsCall).not.toHaveProperty("compensationMin");
    expect(lastFacetsCall).not.toHaveProperty("compensationMax");
  });
});

// TC-523-C04 (AC-523-04)
describe("TC-523-C04: the empty-results message no longer mentions compensation", () => {
  it("shows the reworded description and never the word 'compensation'", async () => {
    vi.clearAllMocks();
    setupMocks({}, { items: [], total: 0, page: 0, totalPages: 0 });
    render(<JobSearchScreen {...DEFAULT_PROPS} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    await waitFor(() => expect(screen.getByText("No jobs match your filters")).toBeInTheDocument());
    expect(
      screen.getByText("Try broadening location or the posted-date range, or remove a filter.")
    ).toBeInTheDocument();
    expect(screen.queryByText(/compensation/i)).toBeNull();
  });
});

// TC-523-C06 (AC-523-06)
describe("TC-523-C06: Salary: high to low still issues sort=salary-desc", () => {
  it("switching the sort select fires searchJobs with sort: salary-desc", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const sortSelect = [...document.querySelectorAll("select")].find((s) =>
      [...s.options].some((o) => o.text === "Salary: high to low")
    );
    fireEvent.change(sortSelect, { target: { value: "salary" } });

    await waitFor(() => {
      expect(searchJobs).toHaveBeenCalled();
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.sort).toBe("salary-desc");
    });
  });
});
