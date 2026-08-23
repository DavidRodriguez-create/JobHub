/**
 * Component tests for per-user saved filters, story #523, USE_API=true.
 * Cases: TC-523-D01..D06, E01..E08, F01..F02, G01..G04, H01..H03, I01..I03, K02,
 * L01..L03 (docs/qa/523-comp-filter-removal-and-per-user-saved-filters-test-cases.md).
 * Mirrors JobSearchScreen.test.jsx's mocking style.
 */
import React from "react";
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react";
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

const { searchJobs, getJobFacets, listSavedFilters, createSavedFilter, deleteSavedFilter } =
  await import("../../api/jobs.js");
const { ApiError } = await import("../../api/client.js");
const { JobSearchScreen } = await import("../../screens/JobSearch.jsx");

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
  authed: false,
  openSearch: vi.fn(),
};

function preset(id, name, filters) {
  return { id, name, filters, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" };
}

function setupMocks({ facets = {}, search = {}, savedFilters = [] } = {}) {
  getJobFacets.mockResolvedValue({ ...DEFAULT_FACETS, ...facets });
  searchJobs.mockResolvedValue({ ...EMPTY_SEARCH, ...search });
  listSavedFilters.mockResolvedValue(savedFilters);
  // createSavedFilter/deleteSavedFilter are deliberately left untouched here: vi.clearAllMocks()
  // (called just before this) only clears call history, not implementations, so a test that
  // configures them (mockResolvedValue/mockRejectedValue/mockImplementation) BEFORE calling
  // renderApi keeps that configuration. Do not add a mockReset() here, it would silently wipe
  // a test's own setup and make create/delete resolve to undefined ("success") by default.
}

async function renderApi(props = {}, mockOpts = {}) {
  vi.clearAllMocks();
  setupMocks(mockOpts);
  const result = render(<JobSearchScreen {...DEFAULT_PROPS} {...props} />);
  await waitFor(() => expect(searchJobs).toHaveBeenCalled());
  return result;
}

async function openSaveDialog() {
  const btn = await waitFor(() => screen.getByText("Save filter"));
  fireEvent.click(btn);
  return screen.getByPlaceholderText("Filter name…");
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// ── Section D: loading presets ──────────────────────────────────────────────

describe("saved filters loading (USE_API=true)", () => {
  // TC-523-D01
  it("TC-523-D01 loads presets from listSavedFilters, not localStorage, exactly once", async () => {
    const getItemSpy = vi.spyOn(Storage.prototype, "getItem");
    await renderApi({ authed: true }, { savedFilters: [preset("p1", "Remote EU", {})] });

    await waitFor(() => expect(listSavedFilters).toHaveBeenCalledTimes(1));
    const trigger = await screen.findByText("Saved filters");
    fireEvent.click(trigger);
    expect(await screen.findByText("Remote EU")).toBeInTheDocument();
    expect(getItemSpy).not.toHaveBeenCalledWith("jobhub_saved_filters");
    getItemSpy.mockRestore();
  });

  // TC-523-D02 (shared with AC-523-08/AC-523-22)
  it("TC-523-D02 an authed account with zero presets shows no Saved filters control", async () => {
    await renderApi({ authed: true }, { savedFilters: [] });
    await waitFor(() => expect(listSavedFilters).toHaveBeenCalled());
    expect(screen.queryByText("Saved filters")).toBeNull();
  });

  // TC-523-D03 (500)
  it("TC-523-D03 a 500 shows the saved-filters-error message; the rest of the screen still works", async () => {
    vi.clearAllMocks();
    setupMocks();
    listSavedFilters.mockRejectedValue(new ApiError(500, "boom"));
    render(<JobSearchScreen {...DEFAULT_PROPS} authed={true} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    const err = await screen.findByTestId("saved-filters-error");
    expect(err).toHaveTextContent("Couldn't load your saved filters. Reload the page to try again.");
    expect(screen.queryByText("Saved filters")).toBeNull();
    expect(screen.getByTestId("results-list")).toBeInTheDocument();

    const companyTrigger = screen.getByText("All companies");
    fireEvent.click(companyTrigger);
    await waitFor(() => screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => {
      const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
      expect(lastCall.company).toEqual(["Acme Corp"]);
    });
  });

  // TC-523-D04 (401)
  it("TC-523-D04 a 401 shows the identical generic saved-filters-error message", async () => {
    vi.clearAllMocks();
    setupMocks();
    listSavedFilters.mockRejectedValue(new ApiError(401, "expired"));
    render(<JobSearchScreen {...DEFAULT_PROPS} authed={true} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    const err = await screen.findByTestId("saved-filters-error");
    expect(err).toHaveTextContent("Couldn't load your saved filters. Reload the page to try again.");
  });

  // TC-523-D05 (network)
  it("TC-523-D05 a network failure shows the identical generic saved-filters-error message", async () => {
    vi.clearAllMocks();
    setupMocks();
    listSavedFilters.mockRejectedValue(new ApiError(0, "Network error — is the backend reachable?"));
    render(<JobSearchScreen {...DEFAULT_PROPS} authed={true} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    const err = await screen.findByTestId("saved-filters-error");
    expect(err).toHaveTextContent("Couldn't load your saved filters. Reload the page to try again.");
  });

  // TC-523-D06
  it("TC-523-D06 no stale previous-user preset reappears when a second account's load fails", async () => {
    vi.clearAllMocks();
    setupMocks({ savedFilters: [preset("a1", "A's filter", {})] });
    const { rerender } = render(<JobSearchScreen {...DEFAULT_PROPS} authed={true} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    fireEvent.click(await screen.findByText("Saved filters"));
    expect(await screen.findByText("A's filter")).toBeInTheDocument();

    rerender(<JobSearchScreen {...DEFAULT_PROPS} authed={false} />);
    expect(screen.queryByText("A's filter")).toBeNull();

    listSavedFilters.mockRejectedValue(new ApiError(500, "boom"));
    rerender(<JobSearchScreen {...DEFAULT_PROPS} authed={true} />);

    await screen.findByTestId("saved-filters-error");
    expect(screen.queryByText("A's filter")).toBeNull();
    expect(screen.queryByText("Saved filters")).toBeNull();
  });
});

// ── Section E: saving a preset ──────────────────────────────────────────────

describe("saving a preset (USE_API=true)", () => {
  // TC-523-E01
  it("TC-523-E01 saving creates a preset via createSavedFilter and it appears without a second list call", async () => {
    createSavedFilter.mockImplementation(async ({ name, filters }) =>
      preset("new-1", name, filters)
    );
    await renderApi({ authed: true });

    const input = screen.getByPlaceholderText("Title, company…");
    fireEvent.change(input, { target: { value: "react" } });
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    const nameInput = await openSaveDialog();
    fireEvent.change(nameInput, { target: { value: "React roles" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(createSavedFilter).toHaveBeenCalledTimes(1));
    const call = createSavedFilter.mock.calls[0][0];
    expect(call.name).toBe("React roles");
    expect(call.filters.keyword).toBe("react");

    fireEvent.click(await screen.findByText("Saved filters"));
    await waitFor(() => expect(screen.getByText("React roles")).toBeInTheDocument());
    expect(listSavedFilters).toHaveBeenCalledTimes(1); // mount only, not re-triggered by save
  });

  // TC-523-E02 (load-bearing body shape)
  it("TC-523-E02 the createSavedFilter body carries no sort/compensation keys even with Salary sort active", async () => {
    createSavedFilter.mockResolvedValue(preset("new-2", "X", {}));
    await renderApi({ authed: true });

    const sortSelect = [...document.querySelectorAll("select")].find((s) =>
      [...s.options].some((o) => o.text === "Salary: high to low")
    );
    fireEvent.change(sortSelect, { target: { value: "salary" } });
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    const nameInput = await openSaveDialog();
    fireEvent.change(nameInput, { target: { value: "Salary sorted" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(createSavedFilter).toHaveBeenCalledTimes(1));
    const filters = createSavedFilter.mock.calls[0][0].filters;
    expect(filters).not.toHaveProperty("sort");
    expect(filters).not.toHaveProperty("compensationMin");
    expect(filters).not.toHaveProperty("compensationMax");
  });

  // TC-523-E03 (apply side)
  it("TC-523-E03 applying a preset never changes the currently-selected sort", async () => {
    await renderApi({ authed: true }, { savedFilters: [preset("p1", "Saved", {})] });

    const sortSelect = [...document.querySelectorAll("select")].find((s) =>
      [...s.options].some((o) => o.text === "Salary: high to low")
    );
    fireEvent.change(sortSelect, { target: { value: "salary" } });
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    const savedFiltersTrigger = screen.getByText("Saved filters");
    fireEvent.click(savedFiltersTrigger);
    const filterItem = await waitFor(() => screen.getByText("Saved"));
    fireEvent.click(filterItem);

    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    expect(sortSelect.value).toBe("salary");
  });

  // TC-523-E04 (AC-523-12: genuine no-active-filters state)
  it("TC-523-E04 with no active filters (Posted=Any time), Save filter is not offered", async () => {
    await renderApi({ authed: true });

    const postedSelect = [...document.querySelectorAll("select")].find((s) =>
      [...s.options].some((o) => o.text === "Any time")
    );
    fireEvent.change(postedSelect, { target: { value: "any" } });
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    expect(screen.queryByText("Save filter")).toBeNull();
  });

  // TC-523-E05 (AC-523-13: client-side < 5 gate)
  it("TC-523-E05 with 5 presets already loaded, Save filter is not offered, no request attempted", async () => {
    const five = Array.from({ length: 5 }, (_, i) => preset("p" + i, "Preset " + i, {}));
    await renderApi({ authed: true }, { savedFilters: five });

    expect(screen.queryByText("Save filter")).toBeNull();
    expect(createSavedFilter).not.toHaveBeenCalled();
  });

  // TC-523-E06 (400 ceiling, load-bearing)
  it("TC-523-E06 a 400 from the server keeps the dialog open with the fixed ceiling copy", async () => {
    const four = Array.from({ length: 4 }, (_, i) => preset("p" + i, "Preset " + i, {}));
    createSavedFilter.mockRejectedValue(new ApiError(400, "server says no"));
    await renderApi({ authed: true }, { savedFilters: four });

    const nameInput = await openSaveDialog();
    fireEvent.change(nameInput, { target: { value: "One too many" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(createSavedFilter).toHaveBeenCalledTimes(1));
    expect(screen.getByPlaceholderText("Filter name…")).toHaveValue("One too many");
    expect(screen.getByTestId("save-filter-error")).toHaveTextContent(
      "You already have 5 saved filters. Delete one first."
    );
    expect(screen.getByText("4/5 saved")).toBeInTheDocument();
  });

  // TC-523-E07 (500 and network)
  it.each([500, 0])("TC-523-E07 a %i failure keeps the dialog open with the generic retry copy", async (status) => {
    createSavedFilter.mockRejectedValue(new ApiError(status, "boom"));
    await renderApi({ authed: true });

    const nameInput = await openSaveDialog();
    fireEvent.change(nameInput, { target: { value: "My filter" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(createSavedFilter).toHaveBeenCalledTimes(1));
    expect(screen.getByPlaceholderText("Filter name…")).toHaveValue("My filter");
    expect(screen.getByTestId("save-filter-error")).toHaveTextContent(
      "Couldn't save this filter. Please try again."
    );
    expect(screen.getByText("0/5 saved")).toBeInTheDocument();
  });

  // TC-523-E08 (double-submit guard, load-bearing)
  it("TC-523-E08 Save is disabled while a save is in flight and createSavedFilter is called exactly once", async () => {
    let resolveCreate;
    createSavedFilter.mockImplementation(
      () => new Promise((res) => { resolveCreate = res; })
    );
    await renderApi({ authed: true });

    const nameInput = await openSaveDialog();
    fireEvent.change(nameInput, { target: { value: "In flight" } });
    const saveBtn = screen.getByRole("button", { name: "Save" });
    fireEvent.click(saveBtn);

    await waitFor(() => expect(saveBtn).toBeDisabled());
    fireEvent.click(saveBtn);
    fireEvent.keyDown(nameInput, { key: "Enter" });

    expect(createSavedFilter).toHaveBeenCalledTimes(1);
    resolveCreate(preset("new-x", "In flight", {}));
  });
});

// ── Section F: applying a preset ────────────────────────────────────────────

describe("applying a preset (USE_API=true)", () => {
  // TC-523-F01
  it("TC-523-F01 applying a preset restores all seven dimensions, re-searches, and resets to page 0", async () => {
    const dto = preset("p1", "Full preset", {
      keyword: "react",
      company: ["Acme Corp"],
      location: ["Spain"],
      employmentType: ["full-time"],
      careerLevel: ["senior"],
      language: ["Spanish"],
      postedWithin: "week",
    });
    await renderApi({ authed: true }, { savedFilters: [dto] });
    vi.clearAllMocks();
    setupMocks({ savedFilters: [dto] });

    const savedFiltersTrigger = screen.getByText("Saved filters");
    fireEvent.click(savedFiltersTrigger);
    const filterItem = await waitFor(() => screen.getByText("Full preset"));
    fireEvent.click(filterItem);

    // `keyword` rides the 300ms dQuery debounce, so the first post-apply searchJobs call
    // may still carry the pre-apply (empty) keyword; wait for a call that carries it.
    await waitFor(
      () => expect(searchJobs.mock.calls.some((c) => c[0].keyword === "react")).toBe(true),
      { timeout: 2000 }
    );
    const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
    expect(lastCall.keyword).toBe("react");
    expect(lastCall.company).toEqual(["Acme Corp"]);
    expect(lastCall.location).toEqual(["Spain"]);
    expect(lastCall.employmentType).toEqual(["full-time"]);
    expect(lastCall.careerLevel).toEqual(["senior"]);
    expect(lastCall.language).toEqual(["Spanish"]);
    expect(lastCall.postedWithin).toBe("week");
    expect(lastCall.page).toBe(0);

    expect(screen.getByPlaceholderText("Title, company…")).toHaveValue("react");
  });

  // TC-523-F02
  it("TC-523-F02 applying a preset leaves the comp figure and the sort control untouched", async () => {
    const dto = preset("p1", "Comp preset", { company: ["Acme Corp"] });
    const searchWithComp = {
      items: [{
        id: "job-1",
        title: "Backend Engineer",
        url: "https://example.com/job-1",
        location: "Madrid, Spain",
        company: { name: "Acme Corp" },
        compensationMin: 50000,
        compensationMax: 70000,
      }],
      total: 1,
      page: 0,
      totalPages: 1,
    };
    await renderApi({ authed: true }, { savedFilters: [dto], search: searchWithComp });

    const sortSelect = [...document.querySelectorAll("select")].find((s) =>
      [...s.options].some((o) => o.text === "Sort: Newest first")
    );
    expect(sortSelect.value).toBe("newest");

    vi.clearAllMocks();
    setupMocks({ savedFilters: [dto], search: searchWithComp });

    const savedFiltersTrigger = screen.getByText("Saved filters");
    fireEvent.click(savedFiltersTrigger);
    const filterItem = await waitFor(() => screen.getByText("Comp preset"));
    fireEvent.click(filterItem);

    await waitFor(() => expect(screen.getByText("Backend Engineer")).toBeInTheDocument());
    expect(screen.getByText("€50k–€70k")).toBeInTheDocument();
    expect(sortSelect.value).toBe("newest");
  });
});

// ── Section G: deleting a preset ────────────────────────────────────────────

describe("deleting a preset (USE_API=true)", () => {
  function openSavedFiltersPopover() {
    fireEvent.click(screen.getByText("Saved filters"));
  }

  // TC-523-G01
  it("TC-523-G01 deleting calls deleteSavedFilter with the preset id and removes it immediately", async () => {
    deleteSavedFilter.mockResolvedValue(undefined);
    await renderApi({ authed: true }, { savedFilters: [preset("p1", "Only preset", {})] });

    openSavedFiltersPopover();
    await waitFor(() => screen.getByText("Only preset"));
    const trashIcon = document.querySelector('[data-icon="trash"]');
    fireEvent.click(trashIcon);

    await waitFor(() => expect(deleteSavedFilter).toHaveBeenCalledWith("p1"));
    await waitFor(() => expect(screen.queryByText("Only preset")).toBeNull());
    expect(listSavedFilters).toHaveBeenCalledTimes(1);
  });

  // TC-523-G02 (404-is-success, load-bearing)
  it("TC-523-G02 a 404 on delete still removes the preset and shows no error", async () => {
    deleteSavedFilter.mockRejectedValue(new ApiError(404, "already gone"));
    await renderApi({ authed: true }, { savedFilters: [preset("p1", "Ghost preset", {})] });

    openSavedFiltersPopover();
    await waitFor(() => screen.getByText("Ghost preset"));
    fireEvent.click(document.querySelector('[data-icon="trash"]'));

    await waitFor(() => expect(screen.queryByText("Ghost preset")).toBeNull());
    expect(screen.queryByTestId("saved-filters-error")).toBeNull();
  });

  // TC-523-G03 (500/network)
  it.each([500, 0])("TC-523-G03 a %i on delete leaves the preset visible and shows saved-filters-error", async (status) => {
    deleteSavedFilter.mockRejectedValue(new ApiError(status, "boom"));
    await renderApi({ authed: true }, { savedFilters: [preset("p1", "Stubborn preset", {})] });

    openSavedFiltersPopover();
    await waitFor(() => screen.getByText("Stubborn preset"));
    fireEvent.click(document.querySelector('[data-icon="trash"]'));

    await waitFor(() => expect(deleteSavedFilter).toHaveBeenCalled());
    await screen.findByTestId("saved-filters-error");
    // The dropdown itself may have closed optimistically (SavedFiltersDropdown's own
    // "close when deleting the last row" behaviour is unmodified by this story and does not
    // know the delete failed); re-open it to confirm the preset's DATA was not removed.
    openSavedFiltersPopover();
    expect(screen.getByText("Stubborn preset")).toBeInTheDocument();
  });

  // TC-523-G04 (AC-523-22, same as D02)
  it("TC-523-G04 deleting the only remaining preset removes the Saved filters control entirely", async () => {
    deleteSavedFilter.mockResolvedValue(undefined);
    await renderApi({ authed: true }, { savedFilters: [preset("p1", "Only preset", {})] });

    openSavedFiltersPopover();
    await waitFor(() => screen.getByText("Only preset"));
    fireEvent.click(document.querySelector('[data-icon="trash"]'));

    await waitFor(() => expect(screen.queryByText("Saved filters")).toBeNull());
  });
});

// ── Section H: per-user isolation (highest priority) ────────────────────────

describe("per-user isolation across a same-tab sign-out/sign-in (USE_API=true)", () => {
  // TC-523-H01 (the money case, load-bearing)
  it("TC-523-H01 user A's preset never leaks into user B's session on the same mounted instance", async () => {
    vi.clearAllMocks();
    setupMocks({ savedFilters: [preset("a1", "A's filter", {})] });
    const { rerender } = render(<JobSearchScreen {...DEFAULT_PROPS} authed={true} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    fireEvent.click(await screen.findByText("Saved filters"));
    expect(await screen.findByText("A's filter")).toBeInTheDocument();

    rerender(<JobSearchScreen {...DEFAULT_PROPS} authed={false} />);
    expect(screen.queryByText("A's filter")).toBeNull();

    listSavedFilters.mockResolvedValue([preset("b1", "B's filter", {})]);
    rerender(<JobSearchScreen {...DEFAULT_PROPS} authed={true} />);
    expect(screen.queryByText("A's filter")).toBeNull();

    fireEvent.click(await screen.findByText("Saved filters"));
    expect(await screen.findByText("B's filter")).toBeInTheDocument();
    expect(screen.queryByText("A's filter")).toBeNull();
  });

  // TC-523-H02 (narrower, synchronous)
  it("TC-523-H02 signing out alone synchronously clears the Saved filters control, no async wait needed", async () => {
    vi.clearAllMocks();
    setupMocks({ savedFilters: [preset("a1", "A's filter", {})] });
    const { rerender } = render(<JobSearchScreen {...DEFAULT_PROPS} authed={true} />);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    fireEvent.click(await screen.findByText("Saved filters"));
    expect(await screen.findByText("A's filter")).toBeInTheDocument();

    rerender(<JobSearchScreen {...DEFAULT_PROPS} authed={false} />);
    expect(screen.queryByText("Saved filters")).toBeNull();
    expect(screen.queryByText("A's filter")).toBeNull();
  });

  // TC-523-H03
  it("TC-523-H03 saving as a fresh user B never sends any owner/id field", async () => {
    createSavedFilter.mockResolvedValue(preset("new-b", "B's new preset", {}));
    await renderApi({ authed: true }, { savedFilters: [] });

    const nameInput = await openSaveDialog();
    fireEvent.change(nameInput, { target: { value: "B's new preset" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(createSavedFilter).toHaveBeenCalledTimes(1));
    const body = createSavedFilter.mock.calls[0][0];
    expect(Object.keys(body).sort()).toEqual(["filters", "name"]);
    expect(body).not.toHaveProperty("id");
    expect(body).not.toHaveProperty("userId");
  });
});

// ── Section I: anonymous visitors ───────────────────────────────────────────

describe("anonymous visitors (USE_API=true)", () => {
  // TC-523-I01
  it("TC-523-I01 no Saved filters and no Save filter control render for an anonymous visitor", async () => {
    await renderApi({ authed: false });

    const companyTrigger = screen.getByText("All companies");
    fireEvent.click(companyTrigger);
    await waitFor(() => screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    expect(screen.queryByText("Saved filters")).toBeNull();
    expect(screen.queryByText("Save filter")).toBeNull();
  });

  // TC-523-I02 (fully inert, load-bearing)
  it("TC-523-I02 listSavedFilters/createSavedFilter/deleteSavedFilter are never called for an anonymous visitor", async () => {
    await renderApi({ authed: false });

    const input = screen.getByPlaceholderText("Title, company…");
    fireEvent.change(input, { target: { value: "engineer" } });
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    fireEvent.change(input, { target: { value: "" } });
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    expect(listSavedFilters).not.toHaveBeenCalled();
    expect(createSavedFilter).not.toHaveBeenCalled();
    expect(deleteSavedFilter).not.toHaveBeenCalled();
  });

  // TC-523-I03 (AC-523-30/BR-5)
  it("TC-523-I03 the legacy key is removed even when signed out", async () => {
    localStorage.setItem("jobhub_saved_filters", JSON.stringify([{ name: "Leaked", state: {} }]));
    await renderApi({ authed: false });

    expect(localStorage.getItem("jobhub_saved_filters")).toBeNull();
    expect(screen.queryByText("Leaked")).toBeNull();
    localStorage.clear();
  });
});

// ── Section K (partial): legacy preset non-reconstruction ──────────────────

describe("legacy preset non-reconstruction (USE_API=true, AC-523-35)", () => {
  // TC-523-K02
  it("TC-523-K02 the discarded legacy presets are not reconstructed under any name", async () => {
    localStorage.setItem("jobhub_saved_filters", JSON.stringify([{ name: "Old preset", state: {} }]));
    await renderApi({ authed: true }, { savedFilters: [] });

    expect(screen.queryByText("Saved filters")).toBeNull();
    expect(screen.queryByText("Old preset")).toBeNull();
    localStorage.clear();
  });
});

// ── Section L: an old preset with stale compensation values ─────────────────

describe("an old preset carrying stale compensation values (USE_API=true)", () => {
  const staleDto = preset("stale-1", "Old comp preset", {
    company: ["Acme Corp"],
    location: ["Spain"],
    careerLevel: ["senior"],
    employmentType: ["full-time"],
    language: ["Spanish"],
    postedWithin: "week",
    compensationMin: 30000,
    compensationMax: 150000,
  });

  // TC-523-L01
  it("TC-523-L01 applying it sends no compensation keys and shows no comp control", async () => {
    await renderApi({ authed: true }, { savedFilters: [staleDto] });
    vi.clearAllMocks();
    setupMocks({ savedFilters: [staleDto] });

    const savedFiltersTrigger = screen.getByText("Saved filters");
    fireEvent.click(savedFiltersTrigger);
    const filterItem = await waitFor(() => screen.getByText("Old comp preset"));
    fireEvent.click(filterItem);

    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
    expect(lastCall).not.toHaveProperty("compensationMin");
    expect(lastCall).not.toHaveProperty("compensationMax");
    expect(document.querySelector('[data-testid="comp-slider-fill"]')).toBeNull();
  });

  // TC-523-L02
  it("TC-523-L02 delete-then-recreate never resurrects the old comp values", async () => {
    deleteSavedFilter.mockResolvedValue(undefined);
    createSavedFilter.mockImplementation(async ({ name, filters }) => preset("re-1", name, filters));
    await renderApi({ authed: true }, { savedFilters: [staleDto] });

    fireEvent.click(screen.getByText("Saved filters"));
    await waitFor(() => screen.getByText("Old comp preset"));
    fireEvent.click(document.querySelector('[data-icon="trash"]'));
    await waitFor(() => expect(screen.queryByText("Saved filters")).toBeNull());

    const companyTrigger = screen.getByText("All companies");
    fireEvent.click(companyTrigger);
    await waitFor(() => screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByText("Acme Corp"));
    fireEvent.click(screen.getByRole("button", { name: /^Apply/ }));
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    const nameInput = await openSaveDialog();
    fireEvent.change(nameInput, { target: { value: "Old comp preset" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(createSavedFilter).toHaveBeenCalledTimes(1));
    const filters = createSavedFilter.mock.calls[0][0].filters;
    expect(filters).not.toHaveProperty("compensationMin");
    expect(filters).not.toHaveProperty("compensationMax");
  });

  // TC-523-L03
  it("TC-523-L03 every non-comp dimension restores exactly, unaffected by the ignored comp fields", async () => {
    await renderApi({ authed: true }, { savedFilters: [staleDto] });
    vi.clearAllMocks();
    setupMocks({ savedFilters: [staleDto] });

    const savedFiltersTrigger = screen.getByText("Saved filters");
    fireEvent.click(savedFiltersTrigger);
    const filterItem = await waitFor(() => screen.getByText("Old comp preset"));
    fireEvent.click(filterItem);

    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
    expect(lastCall.company).toEqual(["Acme Corp"]);
    expect(lastCall.location).toEqual(["Spain"]);
    expect(lastCall.careerLevel).toEqual(["senior"]);
    expect(lastCall.employmentType).toEqual(["full-time"]);
    expect(lastCall.language).toEqual(["Spanish"]);
    expect(lastCall.postedWithin).toBe("week");
  });
});
