/**
 * Component tests: Employment type and Career level facets always visible
 * Cases: C1, C2, C3, C4 (story #61 / ticket #126)
 *
 * These tests assert the FIXED behavior: both fields render unconditionally,
 * regardless of whether the facets API has resolved, returned empty buckets,
 * or rejected.
 */
import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
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
    coOf: () => ({ name: "-", industry: "-", size: "-", hq: "-", url: "" }),
    appForJob: () => undefined,
    nextAppId: () => "APP-001",
  },
}));

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

vi.mock("../../components/WritingLoader.jsx", () => ({
  default: ({ label }) => <div>{label}</div>,
}));

vi.mock("../../components/RichText.jsx", () => ({
  default: ({ text }) => <div>{text}</div>,
}));

const { searchJobs, getJobFacets } = await import("../../api/jobs.js");
const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");

// ── Shared fixtures ────────────────────────────────────────────────────────

const DEFAULT_PROPS = {
  goto: vi.fn(),
  onSaveToggle: vi.fn(),
  savedIds: new Set(),
  openJob: vi.fn(),
  appliedJobIds: new Set(),
  authed: false,
  openSearch: vi.fn(),
};

const NON_EMPTY_FACETS = {
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

const EMPTY_SEARCH = { items: [], total: 0, page: 0, totalPages: 0 };

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

afterEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

// ── C1: Initial paint before facets resolve ────────────────────────────────

describe("C1 Employment type and Career level are present on initial paint before facets resolve", () => {
  it("renders both field labels synchronously while getJobFacets is still in-flight", () => {
    // getJobFacets never resolves — holds the fetch in-flight
    getJobFacets.mockReturnValue(new Promise(() => {}));
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    render(<JobSearchScreen {...DEFAULT_PROPS} />);

    // Assert synchronously — no waitFor — these must be present on first paint
    expect(screen.getByText("Employment type")).toBeInTheDocument();
    expect(screen.getByText("Career level")).toBeInTheDocument();

    // Regression guard: fields that were already unconditional must also be present
    expect(screen.getByText("Company")).toBeInTheDocument();
    expect(screen.getByText("Location")).toBeInTheDocument();
    expect(screen.getByText("Language")).toBeInTheDocument();
  });
});

// ── C2: Fields remain after facets resolve with non-empty options ──────────

describe("C2 Employment type and Career level remain after facets resolve with non-empty options", () => {
  it("field labels are present and options are accessible after facets resolve", async () => {
    getJobFacets.mockResolvedValue(NON_EMPTY_FACETS);
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    render(<JobSearchScreen {...DEFAULT_PROPS} />);

    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    // Labels still present after resolution
    expect(screen.getByText("Employment type")).toBeInTheDocument();
    expect(screen.getByText("Career level")).toBeInTheDocument();

    // Open the Employment type MultiSelect and confirm options are accessible
    const empTrigger = screen.getByText("All employment types");
    fireEvent.click(empTrigger);
    await waitFor(() => expect(screen.getByText("Full-time")).toBeInTheDocument());

    // Close and open the Career level MultiSelect
    // (click outside to close first, then open career level)
    const careerTrigger = screen.getByText("All career levels");
    fireEvent.click(careerTrigger);
    await waitFor(() => expect(screen.getByText("Senior")).toBeInTheDocument());
  });
});

// ── C3: Fields remain after facets resolve with EMPTY options ──────────────

describe("C3 Employment type and Career level remain after facets resolve with EMPTY options", () => {
  it("field labels persist even when facets returns empty employmentTypes and careerLevels", async () => {
    getJobFacets.mockResolvedValue({
      ...NON_EMPTY_FACETS,
      employmentTypes: [],
      careerLevels: [],
    });
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    render(<JobSearchScreen {...DEFAULT_PROPS} />);

    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    // Both labels must still be present with empty option arrays
    expect(screen.getByText("Employment type")).toBeInTheDocument();
    expect(screen.getByText("Career level")).toBeInTheDocument();

    // Regression guard: always-unconditional fields still present
    expect(screen.getByText("Company")).toBeInTheDocument();
  });
});

// ── C4: Fields remain when facets API rejects ──────────────────────────────

describe("C4 Employment type and Career level remain when facets API rejects", () => {
  it("field labels persist and component stays mounted after getJobFacets rejects", async () => {
    // Suppress the expected console.error from the rejection handler
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});

    getJobFacets.mockRejectedValue(new Error("network error"));
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    render(<JobSearchScreen {...DEFAULT_PROPS} />);

    // Wait for the rejection to have been processed (getJobFacets was called)
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    // Give React time to settle after the rejection
    await waitFor(() => {
      expect(screen.getByText("Employment type")).toBeInTheDocument();
    });
    expect(screen.getByText("Career level")).toBeInTheDocument();

    // Component is still mounted — no error boundary fired
    expect(screen.getByText("Company")).toBeInTheDocument();

    consoleError.mockRestore();
  });
});
