/**
 * Component test for the legacy jobhub_saved_filters key removal, story #523.
 * Case: TC-523-K01, parametrized over three sign-in states (AC-523-34).
 *
 * Isolated in its own file because it needs a different USE_API value per iteration
 * (vi.mock is hoisted and file-scoped, so this uses vi.doMock + vi.resetModules per
 * iteration instead of a single static vi.mock).
 */
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, afterEach } from "vitest";

const DEFAULT_FACETS = {
  companies: [{ value: "Acme Corp", count: 5 }],
  locations: [{ value: "Spain", count: 3 }],
  languages: [{ value: "English", count: 8 }],
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
  openSearch: vi.fn(),
};

afterEach(() => {
  localStorage.clear();
  vi.doUnmock("../../api/config.js");
  vi.resetModules();
});

// TC-523-K01, parametrized over 3 sign-in states
describe("TC-523-K01 the legacy jobhub_saved_filters key is removed on mount in every mode", () => {
  it.each([
    ["signed out, USE_API=true", { USE_API: true, authed: false }],
    ["signed in, USE_API=true", { USE_API: true, authed: true }],
    ["signed in, USE_API=false", { USE_API: false, authed: true }],
  ])("%s", async (_label, opts) => {
    vi.resetModules();
    vi.doMock("../../api/config.js", () => ({ USE_API: opts.USE_API }));
    vi.doMock("../../api/jobs.js", () => ({
      searchJobs: vi.fn().mockResolvedValue(EMPTY_SEARCH),
      getJobFacets: vi.fn().mockResolvedValue(DEFAULT_FACETS),
      listSavedFilters: vi.fn().mockResolvedValue([]),
      createSavedFilter: vi.fn(),
      deleteSavedFilter: vi.fn(),
      peekSearch: vi.fn(),
      prefetchSearch: vi.fn(),
      peekFacets: vi.fn(),
    }));
    vi.doMock("../../data/mockData.js", () => ({
      default: {
        companies: {}, jobs: [], applications: [], saved: [],
        byId: () => undefined,
        coOf: () => ({ name: "—", industry: "—", size: "—", hq: "—", url: "" }),
        appForJob: () => undefined,
        nextAppId: () => "APP-001",
      },
    }));
    vi.doMock("../../components/Icon.jsx", () => ({ default: ({ name }) => <span data-icon={name} /> }));
    vi.doMock("../../components/WritingLoader.jsx", () => ({ default: ({ label }) => <div>{label}</div> }));
    vi.doMock("../../components/RichText.jsx", () => ({ default: ({ text }) => <div>{text}</div> }));

    localStorage.setItem("jobhub_saved_filters", JSON.stringify([{ name: "Legacy", state: {} }]));
    const getItemSpy = vi.spyOn(Storage.prototype, "getItem");

    const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");
    render(<JobSearchScreen {...DEFAULT_PROPS} authed={opts.authed} />);

    // Flush the mount-time effects (including the cleanup effect) once, without polling
    // localStorage.getItem ourselves (that would pollute the spy's call count below).
    await waitFor(() => {});

    // Only removeItem clears the key; no code path ever reads its old contents. Snapshot
    // the spy's calls before our own (non-app) getItem check below.
    const getItemCalls = getItemSpy.mock.calls.filter((c) => c[0] === "jobhub_saved_filters");
    getItemSpy.mockRestore();

    expect(localStorage.getItem("jobhub_saved_filters")).toBeNull();
    expect(screen.queryByText("Legacy")).toBeNull();
    expect(getItemCalls.length).toBe(0);
  });
});
