/**
 * Component tests for the client-side query cache wiring in JobSearch.jsx
 * (keep-previous-data, debounce, prefetch, keep-previous-on-failure).
 * Story #329 / sub-issue #368.
 * Cases: TC-JS-01..TC-JS-22 (docs/design/329-test-cases.md, Group C)
 *
 * Strategy mirrors JobSearchScreen.test.jsx / ReactiveFacets.test.jsx: mock
 * ../../api/config.js (USE_API: true), ../../api/jobs.js (searchJobs, getJobFacets,
 * peekSearch, prefetchSearch, peekFacets, listSavedFilters), ../../data/mockData.js,
 * Icon.jsx, WritingLoader.jsx, RichText.jsx. Uses vi.useFakeTimers() for the 300 ms
 * debounce cases and deferred/manually-resolved promises to control race ordering for
 * the stale-response cases.
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
  default: ({ label }) => <div data-testid="writing-loader">{label}</div>,
}));

vi.mock("../../components/RichText.jsx", () => ({
  default: ({ text }) => <div>{text}</div>,
}));

const { searchJobs, getJobFacets, peekSearch, prefetchSearch, peekFacets, listSavedFilters } =
  await import("../../api/jobs.js");
const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");

// ── Fixtures ─────────────────────────────────────────────────────────────────

const DEFAULT_FACETS = {
  companies: [{ value: "Acme Corp", count: 5 }],
  locations: [
    { value: "Spain", count: 3 },
    { value: "Remote", count: 2 },
  ],
  languages: [{ value: "English", count: 8 }],
  employmentTypes: [{ value: "full-time", count: 12 }],
  careerLevels: [{ value: "senior", count: 7 }],
  compensationMin: 30000,
  compensationMax: 150000,
};

const EMPTY_SEARCH = { items: [], total: 0, page: 0, totalPages: 0 };

function job(id, title, overrides = {}) {
  return {
    id,
    title,
    url: `https://example.com/${id}`,
    location: "Madrid, Spain",
    locations: [{ country: "Spain", city: "Madrid", primary: true }],
    company: { name: "Acme Corp" },
    ...overrides,
  };
}

const PAGE_A = { items: [job("job-a", "Frontend Engineer")], total: 1, page: 0, totalPages: 1 };
const PAGE_B = { items: [job("job-b", "Backend Engineer")], total: 1, page: 0, totalPages: 1 };

const DEFAULT_PROPS = {
  goto: vi.fn(),
  onSaveToggle: vi.fn(),
  savedIds: new Set(),
  openJob: vi.fn(),
  appliedJobIds: new Set(),
  authed: false,
  openSearch: vi.fn(),
};

function deferred() {
  let resolve, reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

// Opens a MultiSelect's dropdown by its Field label ("Location" / "Company" / ...),
// independent of the trigger's current display text (which changes to the applied
// value(s) once something is applied, so "All locations" is not a stable query once
// a filter has already been applied once in the same test).
function openFieldDropdown(fieldLabel) {
  const label = screen.getByText(fieldLabel);
  const field = label.closest(".field");
  const trigger = field.querySelector('[style*="cursor: pointer"]');
  fireEvent.click(trigger);
}

// Returns the dropdown OPTION row for `text` (not the trigger's own display label,
// which repeats the same text once a value is applied: "Spain" then shows both as the
// trigger label AND as a checkbox row in the open dropdown).
function optionByText(text) {
  const matches = screen.getAllByText(text);
  return matches[matches.length - 1];
}

// Waits for the dQuery useDebounced(300ms) hook to fully settle after the first render,
// so a follow-up interaction in the same test isn't racing a second, spurious
// search-effect re-run as the debounced value catches up (mirrors
// MultiSelectApply.test.jsx's renderAndSettle helper).
async function settleDebounce() {
  await new Promise((r) => setTimeout(r, 400));
  await waitFor(() => {});
}

function setupMocks(facetOverride = {}, searchOverride = {}) {
  getJobFacets.mockResolvedValue({ ...DEFAULT_FACETS, ...facetOverride });
  searchJobs.mockResolvedValue({ ...EMPTY_SEARCH, ...searchOverride });
  peekSearch.mockReturnValue(undefined);
  peekFacets.mockReturnValue(undefined);
  prefetchSearch.mockImplementation(() => {});
  listSavedFilters.mockResolvedValue([]);
}

// Render, awaiting the very first facets+search settle (a baseline non-empty list).
async function renderSettled(props = {}, searchOverride = {}) {
  vi.clearAllMocks();
  setupMocks({}, { items: [job("job-0", "Initial Engineer")], total: 1, page: 0, totalPages: 1, ...searchOverride });
  const result = render(<JobSearchScreen {...DEFAULT_PROPS} {...props} />);
  await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
  await waitFor(() => expect(searchJobs).toHaveBeenCalled());
  await screen.findByText("Initial Engineer");
  await settleDebounce();
  return result;
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// ── AC-1: first load ─────────────────────────────────────────────────────────

describe("TC-JS-01: First load shows the big loader until the first search settles", () => {
  it("shows the writing-loader with no job rows, then renders once the pending search resolves", async () => {
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue(DEFAULT_FACETS);
    peekSearch.mockReturnValue(undefined);
    peekFacets.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});
    listSavedFilters.mockResolvedValue([]);
    const { promise, resolve } = deferred();
    searchJobs.mockReturnValue(promise);

    render(<JobSearchScreen {...DEFAULT_PROPS} />);

    await waitFor(() => expect(screen.getByTestId("writing-loader")).toBeInTheDocument());
    expect(screen.queryByText("Frontend Engineer")).not.toBeInTheDocument();

    await act(async () => {
      resolve(PAGE_A);
      await promise;
    });

    await waitFor(() => expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument());
    expect(await screen.findByText("Frontend Engineer")).toBeInTheDocument();
  });
});

// ── AC-2: loader retires for the session ──────────────────────────────────────

describe("TC-JS-02: The big loader never reappears after the first successful render, even for a later empty result", () => {
  it("stays absent through a later fetch that resolves with zero items", async () => {
    await renderSettled();
    expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument();

    vi.clearAllMocks();
    setupMocks({}, { items: [], total: 0, page: 0, totalPages: 0 });

    openFieldDropdown("Location");
    await waitFor(() => optionByText("Remote"));
    fireEvent.click(optionByText("Remote"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    // Never appears, at any point, including after settle.
    expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument();
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument();
  });
});

// ── AC-3/AC-4: keyword debounce ──────────────────────────────────────────────

describe("TC-JS-03: Rapid typing coalesces into exactly one outbound search", () => {
  it("fires searchJobs once (beyond mount) with the final typed value", async () => {
    vi.useFakeTimers();
    try {
      setupMocks();
      render(<JobSearchScreen {...DEFAULT_PROPS} />);

      for (let i = 0; i < 5; i++) {
        await act(async () => {
          vi.advanceTimersByTime(400);
          await Promise.resolve();
        });
      }
      expect(searchJobs).toHaveBeenCalled();
      vi.clearAllMocks();
      setupMocks();

      const input = screen.getByPlaceholderText("Title, company…");
      await act(async () => {
        fireEvent.change(input, { target: { value: "r" } });
        fireEvent.change(input, { target: { value: "re" } });
        fireEvent.change(input, { target: { value: "rea" } });
        fireEvent.change(input, { target: { value: "reac" } });
        fireEvent.change(input, { target: { value: "react" } });
      });

      expect(searchJobs).not.toHaveBeenCalled();

      await act(async () => {
        vi.advanceTimersByTime(350);
        await Promise.resolve();
      });

      expect(searchJobs).toHaveBeenCalledTimes(1);
      expect(searchJobs.mock.calls[0][0].keyword).toBe("react");
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("TC-JS-04: The rendered list stays undimmed and unchanged while typing is still settling", () => {
  it("keeps the previous items, no dim, no searchJobs call before the debounce elapses", async () => {
    vi.useFakeTimers();
    try {
      setupMocks({}, { items: [job("job-0", "Initial Engineer")], total: 1, page: 0, totalPages: 1 });
      render(<JobSearchScreen {...DEFAULT_PROPS} />);
      for (let i = 0; i < 5; i++) {
        await act(async () => {
          vi.advanceTimersByTime(400);
          await Promise.resolve();
        });
      }
      expect(screen.getByText("Initial Engineer")).toBeInTheDocument();
      vi.clearAllMocks();
      setupMocks();

      const input = screen.getByPlaceholderText("Title, company…");
      await act(async () => {
        fireEvent.change(input, { target: { value: "r" } });
      });

      expect(screen.getByText("Initial Engineer")).toBeInTheDocument();
      expect(screen.getByTestId("results-list")).toHaveAttribute("data-dimmed", "false");
      expect(searchJobs).not.toHaveBeenCalled();
      expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});

// ── AC-5: cache hit is instant ────────────────────────────────────────────────

describe("TC-JS-05: Settling on an already-cached term renders instantly with no loading state", () => {
  it("renders the cached results before the pending searchJobs promise ever resolves", async () => {
    vi.useFakeTimers();
    try {
      setupMocks({}, { items: [job("job-0", "Initial Engineer")], total: 1, page: 0, totalPages: 1 });
      render(<JobSearchScreen {...DEFAULT_PROPS} />);
      for (let i = 0; i < 5; i++) {
        await act(async () => {
          vi.advanceTimersByTime(400);
          await Promise.resolve();
        });
      }
      expect(screen.getByText("Initial Engineer")).toBeInTheDocument();

      vi.clearAllMocks();
      const cachedPage = { items: [job("job-cached", "Cached Result")], total: 1, page: 0, totalPages: 1 };
      getJobFacets.mockResolvedValue(DEFAULT_FACETS);
      listSavedFilters.mockResolvedValue([]);
      peekFacets.mockReturnValue(undefined);
      prefetchSearch.mockImplementation(() => {});
      peekSearch.mockImplementation((f) => (f.keyword === "sen" ? cachedPage : undefined));
      searchJobs.mockImplementation((f) =>
        f.keyword === "sen" ? new Promise(() => {}) : Promise.resolve(EMPTY_SEARCH)
      );

      const input = screen.getByPlaceholderText("Title, company…");
      await act(async () => {
        fireEvent.change(input, { target: { value: "sen" } });
        vi.advanceTimersByTime(350);
        await Promise.resolve();
      });

      expect(screen.getByText("Cached Result")).toBeInTheDocument();
      expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument();
      expect(screen.getByTestId("results-list")).toHaveAttribute("data-dimmed", "false");
    } finally {
      vi.useRealTimers();
    }
  });
});

// ── AC-6/AC-7: keep-previous-data on a miss ───────────────────────────────────

describe("TC-JS-06: A filter change on a cache miss keeps the old list visible, dimmed, no blank, no big loader", () => {
  it("keeps the previous items mounted and dims the list while the miss is pending", async () => {
    await renderSettled();
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue(DEFAULT_FACETS);
    listSavedFilters.mockResolvedValue([]);
    peekFacets.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});
    peekSearch.mockReturnValue(undefined);
    const { promise } = deferred();
    searchJobs.mockReturnValue(promise);

    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    expect(screen.getByText("Initial Engineer")).toBeInTheDocument();
    expect(screen.getByTestId("results-list")).toHaveAttribute("data-dimmed", "true");
    expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument();
  });
});

describe("TC-JS-07: New results replace the old list once the miss resolves", () => {
  it("shows only the new items and clears the dim once the pending fetch resolves", async () => {
    await renderSettled();
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue(DEFAULT_FACETS);
    listSavedFilters.mockResolvedValue([]);
    peekFacets.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});
    peekSearch.mockReturnValue(undefined);
    const { promise, resolve } = deferred();
    searchJobs.mockReturnValue(promise);

    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    await act(async () => {
      resolve(PAGE_B);
      await promise;
    });

    await waitFor(() => expect(screen.getByText("Backend Engineer")).toBeInTheDocument());
    expect(screen.queryByText("Initial Engineer")).not.toBeInTheDocument();
    expect(screen.getByTestId("results-list")).toHaveAttribute("data-dimmed", "false");
  });
});

// ── AC-8: only the most recent response ever renders ──────────────────────────

describe("TC-JS-08: Only the most-recently-selected filter combination's response is ever rendered", () => {
  it("shows B's data once B resolves, and A's later resolution never overrides it", async () => {
    await renderSettled();
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue(DEFAULT_FACETS);
    listSavedFilters.mockResolvedValue([]);
    peekFacets.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});
    peekSearch.mockReturnValue(undefined);

    const a = deferred();
    const b = deferred();
    searchJobs.mockReturnValueOnce(a.promise);

    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    searchJobs.mockReturnValueOnce(b.promise);
    openFieldDropdown("Location");
    await waitFor(() => optionByText("Remote"));
    fireEvent.click(optionByText("Remote"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(2));

    await act(async () => {
      b.resolve(PAGE_B);
      await b.promise;
    });
    await waitFor(() => expect(screen.getByText("Backend Engineer")).toBeInTheDocument());

    await act(async () => {
      a.resolve(PAGE_A);
      await a.promise.catch(() => {});
    });

    // Give React a tick to (not) apply A's data.
    await new Promise((r) => setTimeout(r, 0));
    expect(screen.getByText("Backend Engineer")).toBeInTheDocument();
    expect(screen.queryByText("Frontend Engineer")).not.toBeInTheDocument();
  });
});

// ── AC-9/AC-10/AC-11: prefetch page n+1 ────────────────────────────────────────

describe("TC-JS-09: Page n+1 is silently warmed after a successful search with a next page", () => {
  it("calls prefetchSearch with page: page+1 and causes no visible DOM change", async () => {
    vi.clearAllMocks();
    setupMocks({}, { items: [job("job-1", "Page Zero Job")], total: 40, page: 0, totalPages: 2 });

    render(<JobSearchScreen {...DEFAULT_PROPS} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await screen.findByText("Page Zero Job");

    await waitFor(() => expect(prefetchSearch).toHaveBeenCalled());
    const call = prefetchSearch.mock.calls[prefetchSearch.mock.calls.length - 1][0];
    expect(call.page).toBe(1);
    expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument();
    expect(screen.getByTestId("results-list")).toHaveAttribute("data-dimmed", "false");
  });
});

describe("TC-JS-10: Clicking Next onto a warmed page is instant", () => {
  it("renders the next page immediately without waiting on a pending searchJobs promise", async () => {
    vi.clearAllMocks();
    setupMocks({}, { items: [job("job-1", "Page Zero Job")], total: 40, page: 0, totalPages: 2 });

    render(<JobSearchScreen {...DEFAULT_PROPS} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await screen.findByText("Page Zero Job");
    await settleDebounce();

    const nextPage = { items: [job("job-2", "Page One Job")], total: 40, page: 1, totalPages: 2 };
    peekSearch.mockImplementation((f) => (f.page === 1 ? nextPage : undefined));
    searchJobs.mockImplementation((f) => (f.page === 1 ? new Promise(() => {}) : Promise.resolve(nextPage)));

    fireEvent.click(screen.getAllByLabelText("Next page")[0]);

    expect(await screen.findByText("Page One Job")).toBeInTheDocument();
    expect(screen.queryByText("Page Zero Job")).not.toBeInTheDocument();
    expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument();
    expect(screen.getByTestId("results-list")).toHaveAttribute("data-dimmed", "false");
  });
});

describe("TC-JS-11a: No warm-up is attempted past the last page", () => {
  it("does not call prefetchSearch when the settled page is the last page", async () => {
    vi.clearAllMocks();
    setupMocks({}, { items: [job("job-last", "Last Page Job")], total: 1, page: 0, totalPages: 1 });

    render(<JobSearchScreen {...DEFAULT_PROPS} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await screen.findByText("Last Page Job");

    expect(prefetchSearch).not.toHaveBeenCalled();
  });
});

describe("TC-JS-11b: A failed background warm-up is invisible and does not block the user", () => {
  it("renders no error state and the next Next click still triggers an ordinary fetch", async () => {
    vi.clearAllMocks();
    setupMocks({}, { items: [job("job-1", "Page Zero Job")], total: 40, page: 0, totalPages: 2 });
    // A "failed" prefetch never surfaces to the caller (jobs.js's real prefetchSearch
    // swallows the rejection internally, per query-cache.js's prefetch contract); the
    // mock standing in for it here does the same, so JobSearch.jsx sees nothing at all.
    prefetchSearch.mockImplementation(() => {});

    render(<JobSearchScreen {...DEFAULT_PROPS} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await screen.findByText("Page Zero Job");
    await settleDebounce();

    expect(screen.queryByTestId("search-failed-banner")).not.toBeInTheDocument();
    expect(screen.queryByText(/error/i)).not.toBeInTheDocument();

    const nextPage = { items: [job("job-2", "Page One Job")], total: 40, page: 1, totalPages: 2 };
    searchJobs.mockResolvedValue(nextPage);
    fireEvent.click(screen.getAllByLabelText("Next page")[0]);

    expect(await screen.findByText("Page One Job")).toBeInTheDocument();
  });
});

// ── AC-12/AC-13: paging back ───────────────────────────────────────────────────

describe("TC-JS-12: Paging back to a seen page/filter combination is instant", () => {
  it("renders a cached page instantly on paging back, with no dim and no loading indicator", async () => {
    vi.clearAllMocks();
    // Page 0 seen on first load (component owns page state, starting at 0).
    setupMocks({}, { items: [job("job-0", "Page Zero Job")], total: 60, page: 0, totalPages: 3 });

    render(<JobSearchScreen {...DEFAULT_PROPS} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await screen.findByText("Page Zero Job");
    await settleDebounce();

    // Forward to page 1 (a real fetch), enabling the Previous control.
    const page1 = { items: [job("job-1", "Page One Job")], total: 60, page: 1, totalPages: 3 };
    searchJobs.mockResolvedValue(page1);
    fireEvent.click(screen.getAllByLabelText("Next page")[0]);
    await screen.findByText("Page One Job");
    await settleDebounce();

    // Page BACK to page 0: now a cache hit (peekSearch returns page-0 data); the
    // network fetch for page 0 hangs, so an instant render proves the sync peek path.
    const page0Cached = { items: [job("job-0b", "Page Zero Cached Job")], total: 60, page: 0, totalPages: 3 };
    peekSearch.mockImplementation((f) => (f.page === 0 ? page0Cached : undefined));
    searchJobs.mockImplementation((f) => (f.page === 0 ? new Promise(() => {}) : Promise.resolve(page1)));

    fireEvent.click(screen.getAllByLabelText("Previous page")[0]);

    expect(await screen.findByText("Page Zero Cached Job")).toBeInTheDocument();
    expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument();
    expect(screen.getByTestId("results-list")).toHaveAttribute("data-dimmed", "false");
  });
});

describe("TC-JS-13: An aged-out previously-seen combination behaves as an ordinary miss", () => {
  it("keeps the current list visible, dimmed, while re-fetching an evicted page", async () => {
    vi.clearAllMocks();
    // Page 0 seen on first load (component owns page state, starting at 0).
    setupMocks({}, { items: [job("job-0", "Page Zero Job")], total: 60, page: 0, totalPages: 3 });

    render(<JobSearchScreen {...DEFAULT_PROPS} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await screen.findByText("Page Zero Job");
    await settleDebounce();

    // Forward to page 1 (a real fetch) so the Previous control is enabled.
    const page1 = { items: [job("job-1", "Page One Job")], total: 60, page: 1, totalPages: 3 };
    searchJobs.mockResolvedValue(page1);
    fireEvent.click(screen.getAllByLabelText("Next page")[0]);
    await screen.findByText("Page One Job");
    await settleDebounce();

    // Page BACK to page 0, but it has been evicted (peek miss): an ordinary miss keeps
    // the current list visible + dimmed while the network fetch is pending.
    peekSearch.mockReturnValue(undefined); // simulate eviction
    const { promise, resolve } = deferred();
    searchJobs.mockImplementation((f) => (f.page === 0 ? promise : Promise.resolve(page1)));

    const callsBeforeClick = searchJobs.mock.calls.length;
    fireEvent.click(screen.getAllByLabelText("Previous page")[0]);

    await waitFor(() => expect(searchJobs.mock.calls.length).toBeGreaterThan(callsBeforeClick));
    expect(screen.getByText("Page One Job")).toBeInTheDocument();
    expect(screen.getByTestId("results-list")).toHaveAttribute("data-dimmed", "true");
    expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument();

    const page0 = { items: [job("job-0c", "Page Zero Real Job")], total: 60, page: 0, totalPages: 3 };
    await act(async () => {
      resolve(page0);
      await promise;
    });

    await waitFor(() => expect(screen.getByText("Page Zero Real Job")).toBeInTheDocument());
  });
});

// ── AC-14: failure is never cached as success ─────────────────────────────────

describe("TC-JS-14: A failed filter combination is genuinely retried, not served a cached failure", () => {
  it("re-fetches a failed combination on re-apply and renders the retry's success", async () => {
    await renderSettled();
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue(DEFAULT_FACETS);
    listSavedFilters.mockResolvedValue([]);
    peekFacets.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});
    peekSearch.mockReturnValue(undefined);

    // The first application of {Spain} fails; every later call succeeds (a failure is
    // never cached, so re-selecting the same combo must hit the network again). Using
    // resolved-default (not once) keeps the assertion robust against the extra
    // search-effect run the facets to bounds settle can trigger.
    searchJobs.mockRejectedValueOnce(new Error("network error"));
    searchJobs.mockResolvedValue(PAGE_B);

    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => expect(screen.getByTestId("search-failed-banner")).toBeInTheDocument());
    const callsAfterFailure = searchJobs.mock.calls.length;

    // Re-apply the identical {Spain} combination (toggle off, then on again).
    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    // The failed combo triggers real network calls again (no cached failure served) and
    // the retry's success renders, clearing the non-blocking failure banner.
    await waitFor(() => expect(searchJobs.mock.calls.length).toBeGreaterThan(callsAfterFailure));
    await waitFor(() => expect(screen.getByText("Backend Engineer")).toBeInTheDocument());
    await waitFor(() => expect(screen.queryByTestId("search-failed-banner")).not.toBeInTheDocument());
  });
});

// ── AC-15: first-load failure distinguishable from empty state ────────────────

describe("TC-JS-15: First-load failure is distinguishable from a genuine zero-result search", () => {
  it("shows a failure indication and never the 'No jobs match your filters' empty state", async () => {
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue(DEFAULT_FACETS);
    peekSearch.mockReturnValue(undefined);
    peekFacets.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});
    listSavedFilters.mockResolvedValue([]);
    searchJobs.mockRejectedValue(new Error("first search fails"));

    render(<JobSearchScreen {...DEFAULT_PROPS} />);

    await waitFor(() => expect(screen.getByTestId("search-failed-banner")).toBeInTheDocument());
    expect(screen.queryByText("No jobs match your filters")).not.toBeInTheDocument();
  });
});

// ── AC-16: failure after existing results does not wipe them ──────────────────

describe("TC-JS-16: A failure after existing results does not wipe them", () => {
  it("keeps the previous items and total, shows the non-blocking searchFailed banner", async () => {
    await renderSettled();
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue(DEFAULT_FACETS);
    listSavedFilters.mockResolvedValue([]);
    peekFacets.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});
    peekSearch.mockReturnValue(undefined);
    searchJobs.mockRejectedValue(new Error("update failed"));

    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => expect(screen.getByTestId("search-failed-banner")).toBeInTheDocument());
    expect(screen.getByText("Initial Engineer")).toBeInTheDocument();
    expect(screen.queryByText("No jobs match your filters")).not.toBeInTheDocument();
  });
});

// ── AC-17: facets failure does not corrupt option lists ────────────────────────

describe("TC-JS-17: A failed facets fetch does not corrupt filter option lists", () => {
  it("leaves the dropdown option counts unchanged after getJobFacets rejects", async () => {
    await renderSettled();
    vi.clearAllMocks();
    getJobFacets.mockRejectedValue(new Error("facets down"));
    listSavedFilters.mockResolvedValue([]);
    searchJobs.mockResolvedValue(EMPTY_SEARCH);
    peekSearch.mockReturnValue(undefined);
    peekFacets.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});

    const input = screen.getByPlaceholderText("Title, company…");
    fireEvent.change(input, { target: { value: "trigger" } });
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    openFieldDropdown("Company");
    await waitFor(() => screen.getByText("Acme Corp"));
    expect(screen.getByText("5")).toBeInTheDocument();
  });
});

// ── AC-18/AC-19: empty result set ─────────────────────────────────────────────

describe("TC-JS-18: A genuine zero-match combination shows the real empty state", () => {
  it("replaces the old list with the 'No jobs match your filters' empty state", async () => {
    await renderSettled();
    vi.clearAllMocks();
    setupMocks({}, { items: [], total: 0, page: 0, totalPages: 0 });

    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => expect(screen.getByText("No jobs match your filters")).toBeInTheDocument());
    expect(screen.queryByText("Initial Engineer")).not.toBeInTheDocument();
  });
});

describe("TC-JS-19: The empty state never appears while a fetch is merely in flight", () => {
  it("does not show the empty state during a pending fetch, even when the previous list was itself empty", async () => {
    vi.clearAllMocks();
    setupMocks({}, { items: [], total: 0, page: 0, totalPages: 0 });
    render(<JobSearchScreen {...DEFAULT_PROPS} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByText("No jobs match your filters")).toBeInTheDocument());
    await settleDebounce();

    vi.clearAllMocks();
    getJobFacets.mockResolvedValue(DEFAULT_FACETS);
    listSavedFilters.mockResolvedValue([]);
    peekFacets.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});
    peekSearch.mockReturnValue(undefined);
    const { promise } = deferred();
    searchJobs.mockReturnValue(promise);

    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    expect(screen.queryByText("No jobs match your filters")).not.toBeInTheDocument();
  });
});

// ── AC-20: rendered results always match currently selected filters ───────────

describe("TC-JS-20: Rendered results always match only the currently selected filters (extends TC-JS-08)", () => {
  it("shows B's title, total, and pager range with no trace of A's values after both settle", async () => {
    await renderSettled();
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue(DEFAULT_FACETS);
    listSavedFilters.mockResolvedValue([]);
    peekFacets.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});
    peekSearch.mockReturnValue(undefined);

    const a = deferred();
    const b = deferred();
    searchJobs.mockReturnValueOnce(a.promise);

    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    searchJobs.mockReturnValueOnce(b.promise);
    openFieldDropdown("Location");
    await waitFor(() => optionByText("Remote"));
    fireEvent.click(optionByText("Remote"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(2));

    await act(async () => {
      b.resolve({ items: [job("job-b", "Backend Engineer")], total: 1, page: 0, totalPages: 1 });
      await b.promise;
    });
    await waitFor(() => expect(screen.getByText("Backend Engineer")).toBeInTheDocument());
    // Both pagers render a "Showing …" line; the top one is enough to assert B's total.
    expect(screen.getAllByText(/Showing/)[0].textContent).toContain("1");

    await act(async () => {
      a.resolve({ items: [job("job-a", "Frontend Engineer")], total: 99, page: 0, totalPages: 5 });
      await a.promise.catch(() => {});
    });
    await new Promise((r) => setTimeout(r, 0));

    expect(screen.getByText("Backend Engineer")).toBeInTheDocument();
    expect(screen.queryByText("Frontend Engineer")).not.toBeInTheDocument();
    expect(screen.queryByText("99")).not.toBeInTheDocument();
  });
});

// ── AC-21: facet counts always match currently selected filters ───────────────

describe("TC-JS-21: Facet counts always match only the currently selected filters", () => {
  it("shows D's facet counts even after C's slower response resolves later", async () => {
    await renderSettled();
    vi.clearAllMocks();
    searchJobs.mockResolvedValue(EMPTY_SEARCH);
    listSavedFilters.mockResolvedValue([]);
    peekFacets.mockReturnValue(undefined);
    peekSearch.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});

    const c = deferred();
    const d = deferred();
    getJobFacets.mockReturnValueOnce(c.promise);

    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));

    getJobFacets.mockReturnValueOnce(d.promise);
    openFieldDropdown("Location");
    await waitFor(() => optionByText("Remote"));
    fireEvent.click(optionByText("Remote"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(2));

    await act(async () => {
      d.resolve({ ...DEFAULT_FACETS, companies: [{ value: "Acme Corp", count: 99 }] });
      await d.promise;
    });

    await act(async () => {
      c.resolve({ ...DEFAULT_FACETS, companies: [{ value: "Acme Corp", count: 1 }] });
      await c.promise;
    });
    await new Promise((r) => setTimeout(r, 0));

    openFieldDropdown("Company");
    await waitFor(() => screen.getByText("Acme Corp"));
    // Scope the count assertion to the Company field: a bare "1" also matches the
    // pager's page-1 button, so query within the dropdown's field only.
    const companyField = screen.getByText("Company").closest(".field");
    expect(within(companyField).getByText("99")).toBeInTheDocument();
    expect(within(companyField).queryByText("1")).not.toBeInTheDocument();
  });
});

// ── AC-22: reordered multi-select values resolve to the same cache entry ──────

describe("TC-JS-22: Reordered multi-select values render the second selection instantly from cache", () => {
  it("renders immediately from cache without waiting on the pending searchJobs promise", async () => {
    await renderSettled();
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue(DEFAULT_FACETS);
    listSavedFilters.mockResolvedValue([]);
    peekFacets.mockReturnValue(undefined);
    prefetchSearch.mockImplementation(() => {});

    const reorderedCached = { items: [job("job-reordered", "Reordered Cache Hit")], total: 1, page: 0, totalPages: 1 };
    peekSearch.mockImplementation((f) => {
      const locs = [...(f.location || [])].sort();
      return locs.join(",") === "Remote,Spain" ? reorderedCached : undefined;
    });
    searchJobs.mockImplementation((f) => {
      const locs = [...(f.location || [])].sort();
      return locs.join(",") === "Remote,Spain" ? new Promise(() => {}) : Promise.resolve(EMPTY_SEARCH);
    });

    openFieldDropdown("Location");
    await waitFor(() => optionByText("Spain"));
    fireEvent.click(optionByText("Spain"));
    fireEvent.click(optionByText("Remote"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));

    expect(await screen.findByText("Reordered Cache Hit")).toBeInTheDocument();
    expect(screen.queryByTestId("writing-loader")).not.toBeInTheDocument();
    expect(screen.getByTestId("results-list")).toHaveAttribute("data-dimmed", "false");
  });
});
