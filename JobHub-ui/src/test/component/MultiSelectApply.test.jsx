/**
 * Component tests for per-dropdown "Apply" on multi-select job filters — Story #38, sub-issue #48
 * Cases: FE-FA-01..FE-FA-11, FE-FA-13..FE-FA-26 (FE-FA-12 is a unit test, see
 * src/test/unit/multiSelectApply.test.js)
 *
 * Strategy:
 * - Mock api/jobs.js to control searchJobs and getJobFacets
 * - Mock api/config.js so USE_API=true
 * - Mock data/mockData.js (empty store)
 * - Render <JobSearchScreen>, settle the initial load, then clear mocks before each scenario
 *   so call-count assertions are scoped to the action under test.
 */
import React from "react";
import { render, screen, waitFor, fireEvent, within, cleanup } from "@testing-library/react";
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
    { value: "Germany", count: 2 },
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

// 30 results so the Pager shows multiple pages (page size default = 25).
const SOME_SEARCH = { items: [], total: 30, page: 0, totalPages: 2 };
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

async function renderAndSettle(props = {}, savedFiltersOverride = []) {
  vi.clearAllMocks();
  setupMocks({}, {}, savedFiltersOverride);
  const result = render(<JobSearchScreen {...DEFAULT_PROPS} {...props} />);
  await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
  await waitFor(() => expect(searchJobs).toHaveBeenCalled());
  // The keyword useDebounced(300ms) hook feeds the facets/search effects. Wait for it to
  // settle so tests start with a clean slate before the first user interaction.
  await new Promise((r) => setTimeout(r, 400));
  await waitFor(() => {});
  return result;
}

// ── Helpers ─────────────────────────────────────────────────────────────────

// Find the trigger <span> for a MultiSelect, excluding chips that may share the same text.
function findTriggerElement(triggerText) {
  const matches = screen.getAllByText(triggerText);
  if (matches.length === 1) return matches[0];
  return matches.find((el) => !el.closest(".chip")) || matches[0];
}

// Find a MultiSelect's wrapper <div> by its current trigger text (e.g. "All locations",
// "Spain" for a single selection, or "2 selected" for multiple).
function findMultiSelectWrap(triggerText) {
  const trigger = findTriggerElement(triggerText);
  return trigger.closest("div").parentElement;
}

// Open a dropdown by clicking its current trigger text; returns the wrapper element.
function openDropdown(triggerText) {
  const trigger = findTriggerElement(triggerText);
  fireEvent.click(trigger);
  return trigger.closest("div").parentElement;
}

// Get the Apply button scoped to a given dropdown wrapper.
function getApplyButton(wrap) {
  return within(wrap).getByRole("button", { name: /^Apply/ });
}

// Get the checkbox input for an option row identified by its visible label text,
// scoped to a given dropdown wrapper. Handles the case where trigger text matches an option.
function getOptionCheckbox(wrap, optionLabel) {
  const matches = within(wrap).getAllByText(optionLabel);
  for (const el of matches) {
    const row = el.closest("div");
    const cb = row?.querySelector('input[type="checkbox"]');
    if (cb) return cb;
  }
  throw new Error(`No checkbox found for option "${optionLabel}"`);
}

function clickOption(wrap, optionLabel) {
  const matches = within(wrap).getAllByText(optionLabel);
  const optionEl = matches.find((el) => {
    const row = el.closest("div");
    return row?.querySelector('input[type="checkbox"]');
  }) || matches[matches.length - 1];
  fireEvent.click(optionEl);
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

// ── FE-FA-01: ticking checkboxes does not refetch (AC-F01) ──
describe("FE-FA-01 ticking checkboxes does not refetch and shows pending ticks (AC-F01)", () => {
  it("ticking France then Germany in Location fires no extra getJobFacets/searchJobs calls", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));

    clickOption(wrap, "Spain");
    clickOption(wrap, "Germany");

    expect(getJobFacets).not.toHaveBeenCalled();
    expect(searchJobs).not.toHaveBeenCalled();

    expect(getOptionCheckbox(wrap, "Spain").checked).toBe(true);
    expect(getOptionCheckbox(wrap, "Germany").checked).toBe(true);
  });
});

// ── FE-FA-02: Apply commits pending, resets page, fires exactly one refresh pair (AC-F02) ──
describe("FE-FA-02 Apply commits pending selection and fires exactly one refresh pair (AC-F02)", () => {
  it("applying France+Germany updates selectedLocations, fires one getJobFacets + one searchJobs, shows chips, closes dropdown", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));
    clickOption(wrap, "Spain");
    clickOption(wrap, "Germany");

    const applyBtn = getApplyButton(wrap);
    expect(applyBtn).not.toBeDisabled();
    fireEvent.click(applyBtn);

    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    const searchArg = searchJobs.mock.calls[0][0];
    expect(searchArg.location.sort()).toEqual(["Germany", "Spain"].sort());

    await waitFor(() => {
      const chips = document.querySelectorAll(".chip.active");
      expect([...chips].some((c) => c.textContent.includes("Spain"))).toBe(true);
      expect([...chips].some((c) => c.textContent.includes("Germany"))).toBe(true);
    });

    // Dropdown closed — option rows no longer in the document
    expect(within(wrap).queryByText("Spain")).not.toBeInTheDocument();
  });
});

// ── FE-FA-03: Apply resets page to 0 (AC-F02) ──
describe("FE-FA-03 Apply resets the page to 0 even if currently on a later page (AC-F02)", () => {
  it("searchJobs after Apply carries page:0 and the Pager shows page 1 active again", async () => {
    setupMocks({}, SOME_SEARCH);
    render(<JobSearchScreen {...DEFAULT_PROPS} />);
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await new Promise((r) => setTimeout(r, 400));
    await waitFor(() => {});

    // Go to page 2 via the Pager
    const page2Btn = screen.getAllByRole("button", { name: "2" })[0];
    vi.clearAllMocks();
    setupMocks({}, SOME_SEARCH);
    fireEvent.click(page2Btn);
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    vi.clearAllMocks();
    setupMocks({}, SOME_SEARCH);

    const wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));
    clickOption(wrap, "Spain");
    fireEvent.click(getApplyButton(wrap));

    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
    expect(lastCall.page).toBe(0);

    // Page-1 button is active again
    await waitFor(() => {
      const page1Btns = screen.getAllByRole("button", { name: "1" });
      expect(page1Btns.some((b) => b.style.background === "var(--color-brand-600)" || b.style.color === "rgb(255, 255, 255)")).toBe(true);
    });
  });
});

// ── FE-FA-04: Apply on one dropdown doesn't affect another, discards unrelated pending (AC-F03) ──
describe("FE-FA-04 Apply on Location does not affect Company applied state and discards Career level's unrelated pending (AC-F03)", () => {
  it("applying Location leaves Company applied untouched and discards Career level's pending", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Select "Acme Corp" for Company and apply it
    let wrap = openDropdown("All companies");
    await waitFor(() => within(wrap).getByText("Acme Corp"));
    clickOption(wrap, "Acme Corp");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    vi.clearAllMocks();
    setupMocks();

    // Open Career level, tick "Senior" (pending only, not applied)
    wrap = openDropdown("All career levels");
    await waitFor(() => within(wrap).getByText("Senior"));
    clickOption(wrap, "Senior");

    // Open Location instead — discards Career level's pending
    const locWrap = openDropdown("All locations");
    await waitFor(() => within(locWrap).getByText("Spain"));
    clickOption(locWrap, "Spain");
    fireEvent.click(getApplyButton(locWrap));

    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    // Find the call that carries the Location apply
    const searchCalls = searchJobs.mock.calls;
    const searchArg = searchCalls[searchCalls.length - 1][0];
    expect(searchArg.location).toEqual(["Spain"]);
    expect(searchArg.company).toEqual(["Acme Corp"]);

    // Re-open Company — still checked
    const companyWrap = openDropdown("Acme Corp");
    await waitFor(() => expect(within(companyWrap).getAllByText("Acme Corp").length).toBeGreaterThanOrEqual(1));
    expect(getOptionCheckbox(companyWrap, "Acme Corp").checked).toBe(true);
    fireEvent.mouseDown(document);

    // Re-open Career level — nothing checked (pending discarded, applied stayed empty)
    const careerWrap = openDropdown("All career levels");
    await waitFor(() => within(careerWrap).getByText("Senior"));
    expect(getOptionCheckbox(careerWrap, "Senior").checked).toBe(false);

    // No Career level chip present
    const chips = document.querySelectorAll(".chip.active");
    expect([...chips].some((c) => c.textContent.includes("Senior"))).toBe(false);
  });
});

// ── FE-FA-05: click-outside discards pending edits, no refetch (AC-F04) ──
describe("FE-FA-05 click-outside discards pending edits with no refetch (AC-F04)", () => {
  it("ticking Beta Ltd then clicking outside leaves selectedCompanies unchanged", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Apply "Acme Corp" first
    let wrap = openDropdown("All companies");
    await waitFor(() => within(wrap).getByText("Acme Corp"));
    clickOption(wrap, "Acme Corp");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    // Re-open, tick Beta Ltd too (pending only)
    wrap = openDropdown("Acme Corp");
    await waitFor(() => within(wrap).getByText("Beta Ltd"));
    clickOption(wrap, "Beta Ltd");
    expect(getOptionCheckbox(wrap, "Beta Ltd").checked).toBe(true);

    // Click outside
    fireEvent.mouseDown(document);

    expect(getJobFacets).not.toHaveBeenCalled();
    expect(searchJobs).not.toHaveBeenCalled();

    // Re-open — only "Acme Corp" checked
    const wrap2 = openDropdown("Acme Corp");
    await waitFor(() => within(wrap2).getByText("Beta Ltd"));
    expect(getOptionCheckbox(wrap2, "Acme Corp").checked).toBe(true);
    expect(getOptionCheckbox(wrap2, "Beta Ltd").checked).toBe(false);
  });
});

// ── FE-FA-06: opening a different dropdown discards the previous one's pending (AC-F05) ──
describe("FE-FA-06 opening a different dropdown discards the previous dropdown's pending edits (AC-F05)", () => {
  it("opening Location while Career level has pending edits discards Career level's pending with no refetch", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const careerWrap = openDropdown("All career levels");
    await waitFor(() => within(careerWrap).getByText("Senior"));
    clickOption(careerWrap, "Senior");
    expect(getOptionCheckbox(careerWrap, "Senior").checked).toBe(true);

    // Open Location's trigger without applying Career level
    const locWrap = openDropdown("All locations");

    expect(getJobFacets).not.toHaveBeenCalled();
    expect(searchJobs).not.toHaveBeenCalled();

    // Location dropdown is open with nothing checked (applied empty)
    await waitFor(() => within(locWrap).getByText("Spain"));
    expect(getOptionCheckbox(locWrap, "Spain").checked).toBe(false);
    expect(getOptionCheckbox(locWrap, "Germany").checked).toBe(false);

    // No Career level chip present
    const chips = document.querySelectorAll(".chip.active");
    expect([...chips].some((c) => c.textContent.includes("Senior"))).toBe(false);
  });
});

// ── FE-FA-07: Esc discards pending edits like click-outside (AC-F06) ──
describe("FE-FA-07 Esc discards pending edits like click-outside (AC-F06)", () => {
  it("pressing Esc with Employment type pending edits leaves selectedEmploymentTypes unchanged", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Apply "Full-time" first
    let wrap = openDropdown("All employment types");
    await waitFor(() => within(wrap).getByText("Full-time"));
    clickOption(wrap, "Full-time");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    // Re-open, additionally tick "Contract" (pending only)
    wrap = openDropdown("Full-time");
    await waitFor(() => within(wrap).getByText("Contract"));
    clickOption(wrap, "Contract");
    expect(getOptionCheckbox(wrap, "Contract").checked).toBe(true);

    fireEvent.keyDown(document, { key: "Escape" });

    expect(getJobFacets).not.toHaveBeenCalled();
    expect(searchJobs).not.toHaveBeenCalled();

    const chips = document.querySelectorAll(".chip.active");
    expect([...chips].some((c) => c.textContent.includes("Full-time"))).toBe(true);
    expect([...chips].some((c) => c.textContent.includes("Contract"))).toBe(false);
  });
});

// ── FE-FA-08: Apply disabled, label "Apply", when dropdown opens with no edits (AC-F07) ──
describe("FE-FA-08 Apply is rendered but disabled with plain label when dropdown opens with no edits (AC-F07)", () => {
  it("opening Location (applied = {Spain}) with no changes shows a disabled 'Apply' button", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Apply "Spain" first
    let wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));
    clickOption(wrap, "Spain");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    // Re-open, no changes
    wrap = openDropdown("Spain");
    await waitFor(() => expect(within(wrap).getAllByText("Spain").length).toBeGreaterThanOrEqual(1));
    expect(getOptionCheckbox(wrap, "Spain").checked).toBe(true);

    const applyBtn = within(wrap).getByRole("button", { name: "Apply" });
    expect(applyBtn).toBeDisabled();
  });
});

// ── FE-FA-09: Apply enabled with "Apply (N)" label, N = symmetric difference (AC-F08) ──
describe('FE-FA-09 Apply becomes enabled with "Apply (N)" label, N = symmetric-difference size (AC-F08)', () => {
  it('ticking France and unticking Spain (applied={Spain}) shows "Apply (2)"', async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    let wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));
    clickOption(wrap, "Spain");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    wrap = openDropdown("Spain");
    await waitFor(() => within(wrap).getByText("Germany"));
    clickOption(wrap, "Germany"); // tick France-equivalent (Germany used as the 2nd option)
    clickOption(wrap, "Spain"); // untick Spain

    const applyBtn = within(wrap).getByRole("button", { name: "Apply (2)" });
    expect(applyBtn).not.toBeDisabled();
  });
});

// ── FE-FA-10: toggling back to applied state re-disables Apply (AC-F09) ──
describe("FE-FA-10 toggling back to applied state re-disables Apply and reverts label to plain Apply (AC-F09)", () => {
  it("ticking then unticking Germany again (applied={Spain}) disables Apply with plain label", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    let wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));
    clickOption(wrap, "Spain");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    wrap = openDropdown("Spain");
    await waitFor(() => within(wrap).getByText("Germany"));

    // Tick Germany — Apply enabled with "Apply (1)"
    clickOption(wrap, "Germany");
    expect(within(wrap).getByRole("button", { name: "Apply (1)" })).not.toBeDisabled();

    // Untick Germany again — back to applied state
    clickOption(wrap, "Germany");
    const applyBtn = within(wrap).getByRole("button", { name: "Apply" });
    expect(applyBtn).toBeDisabled();
  });
});

// ── FE-FA-11: re-opening immediately after Apply starts with Apply disabled (AC-F10, BR-7) ──
describe("FE-FA-11 re-opening immediately after Apply starts with Apply disabled and pending == new applied (AC-F10, BR-7)", () => {
  it("after applying {Spain, Germany} for Location, re-opening shows both checked and Apply disabled", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    let wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));
    clickOption(wrap, "Spain");
    clickOption(wrap, "Germany");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    // Re-open immediately
    wrap = openDropdown("2 selected");
    await waitFor(() => within(wrap).getByText("Spain"));
    expect(getOptionCheckbox(wrap, "Spain").checked).toBe(true);
    expect(getOptionCheckbox(wrap, "Germany").checked).toBe(true);

    const applyBtn = within(wrap).getByRole("button", { name: "Apply" });
    expect(applyBtn).toBeDisabled();
  });
});

// ── FE-FA-13: multiple ticks cause zero additional facet/search calls (AC-F11) ──
describe("FE-FA-13 multiple ticks in one dropdown cause zero additional facet/search calls (AC-F11)", () => {
  it("ticking three Location checkboxes in sequence fires no getJobFacets/searchJobs calls", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));

    clickOption(wrap, "Spain");
    clickOption(wrap, "Germany");
    clickOption(wrap, "Spain"); // untick again — still just pending mutation

    expect(getJobFacets).not.toHaveBeenCalled();
    expect(searchJobs).not.toHaveBeenCalled();
  });
});

// ── FE-FA-14: Apply triggers one refresh with the new applied state (AC-F12, BR-9) ──
describe("FE-FA-14 Apply triggers one refresh sending the newly applied Location state (AC-F12, BR-9)", () => {
  it("applying pending {France/Germany} sends location in both getJobFacets and searchJobs calls", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Germany"));
    clickOption(wrap, "Germany");
    fireEvent.click(getApplyButton(wrap));

    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    expect(getJobFacets.mock.calls[0][0].location).toEqual(["Germany"]);
    expect(searchJobs.mock.calls[0][0].location).toEqual(["Germany"]);
  });
});

// ── FE-FA-15: chips show only applied values, not pending (AC-F13) ──
describe("FE-FA-15 chips show only applied values, not pending (AC-F13)", () => {
  it("Company dropdown open with pending Beta Ltd shows only an Acme Corp chip", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Apply "Acme Corp"
    let wrap = openDropdown("All companies");
    await waitFor(() => within(wrap).getByText("Acme Corp"));
    clickOption(wrap, "Acme Corp");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    // Re-open, additionally tick Beta Ltd (pending only)
    wrap = openDropdown("Acme Corp");
    await waitFor(() => within(wrap).getByText("Beta Ltd"));
    clickOption(wrap, "Beta Ltd");

    const chips = document.querySelectorAll(".chip.active");
    expect([...chips].some((c) => c.textContent.includes("Acme Corp"))).toBe(true);
    expect([...chips].some((c) => c.textContent.includes("Beta Ltd"))).toBe(false);
  });
});

// ── FE-FA-16: chip "x" removes one value from applied state immediately (AC-F14) ──
describe("FE-FA-16 chip x removes one value from applied state immediately, page resets, exactly one refresh (AC-F14)", () => {
  it("removing the Germany chip leaves only the Spain chip and fires one refresh with location=[Spain]", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Apply {Spain, Germany}
    let wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));
    clickOption(wrap, "Spain");
    clickOption(wrap, "Germany");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    const chips = document.querySelectorAll(".chip.active");
    const germanyChip = [...chips].find((c) => c.textContent.includes("Germany"));
    fireEvent.click(germanyChip);

    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    const lastCall = searchJobs.mock.calls[0][0];
    expect(lastCall.location).toEqual(["Spain"]);
    expect(lastCall.page).toBe(0);

    const newChips = document.querySelectorAll(".chip.active");
    expect([...newChips].some((c) => c.textContent.includes("Spain"))).toBe(true);
    expect([...newChips].some((c) => c.textContent.includes("Germany"))).toBe(false);
  });
});

// ── FE-FA-17: chip "x" does not discard an unrelated open dropdown's pending edits (AC-F15) ──
describe("FE-FA-17 chip x does not discard an unrelated dropdown's open pending edits (AC-F15)", () => {
  it("removing a Location chip while Career level has pending edits leaves Career level's pending intact", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Apply {Spain, Germany} for Location
    let wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));
    clickOption(wrap, "Spain");
    clickOption(wrap, "Germany");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    // Open Career level, tick "Senior" (pending only)
    const careerWrap = openDropdown("All career levels");
    await waitFor(() => within(careerWrap).getByText("Senior"));
    clickOption(careerWrap, "Senior");

    // Remove the Germany chip
    const chips = document.querySelectorAll(".chip.active");
    const germanyChip = [...chips].find((c) => c.textContent.includes("Germany"));
    fireEvent.click(germanyChip);

    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    // Career level dropdown still open, "Senior" still checked
    expect(within(careerWrap).getByText("Senior")).toBeInTheDocument();
    expect(getOptionCheckbox(careerWrap, "Senior").checked).toBe(true);
  });
});

// ── FE-FA-18: trigger "x clear" (dropdown closed) clears applied state, one refresh (AC-F16) ──
describe('FE-FA-18 trigger "x clear" (dropdown closed) clears applied state for that dimension, one refresh (AC-F16)', () => {
  it("clicking the trigger x for Career level clears selectedCareerLevels and fires one refresh", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Apply {senior, mid}
    let wrap = openDropdown("All career levels");
    await waitFor(() => within(wrap).getByText("Senior"));
    clickOption(wrap, "Senior");
    clickOption(wrap, "Mid");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    // Trigger now shows "2 selected" with an "x" — find the wrapper and its clear icon
    const triggerWrap = findMultiSelectWrap("2 selected");
    const triggerRow = within(triggerWrap).getByText("2 selected").closest("div");
    const clearIcon = triggerRow.querySelector('[data-icon="x"]');
    fireEvent.click(clearIcon);

    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    const lastCall = searchJobs.mock.calls[0][0];
    expect(lastCall.careerLevel == null || (Array.isArray(lastCall.careerLevel) && lastCall.careerLevel.length === 0)).toBe(true);

    const chips = document.querySelectorAll(".chip.active");
    expect([...chips].some((c) => c.textContent.includes("Senior"))).toBe(false);
    expect([...chips].some((c) => c.textContent.includes("Mid"))).toBe(false);

    // Dropdown remains closed
    expect(within(triggerWrap).queryByText("Senior")).not.toBeInTheDocument();
  });
});

// ── FE-FA-19: trigger "x clear" while open resets pending too (AC-F17) ──
describe('FE-FA-19 trigger "x clear" while its own dropdown is open resets pending to empty too (AC-F17)', () => {
  it("clicking trigger x while Career level is open with pending edits clears both, disables Apply", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Apply {senior}
    let wrap = openDropdown("All career levels");
    await waitFor(() => within(wrap).getByText("Senior"));
    clickOption(wrap, "Senior");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    // Re-open and additionally tick "Mid" (pending = {senior, mid}, Apply enabled "Apply (1)")
    wrap = openDropdown("Senior");
    await waitFor(() => within(wrap).getByText("Mid"));
    clickOption(wrap, "Mid");
    expect(within(wrap).getByRole("button", { name: "Apply (1)" })).not.toBeDisabled();

    // Click the trigger's x clear (trigger still shows "Senior" — applied count, not pending)
    const triggerRow = wrap.children[0];
    const clearIcon = triggerRow.querySelector('[data-icon="x"]');
    fireEvent.click(clearIcon);

    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    const lastCall = searchJobs.mock.calls[searchJobs.mock.calls.length - 1][0];
    expect(lastCall.careerLevel == null || (Array.isArray(lastCall.careerLevel) && lastCall.careerLevel.length === 0)).toBe(true);

    // All checkboxes unchecked, Apply disabled with plain label
    await waitFor(() => {
      expect(getOptionCheckbox(wrap, "Senior").checked).toBe(false);
      expect(getOptionCheckbox(wrap, "Mid").checked).toBe(false);
      expect(within(wrap).getByRole("button", { name: "Apply" })).toBeDisabled();
    });
  });
});

// ── FE-FA-20: "Clear all" discards open dropdown's pending and resets all four (AC-F18) ──
describe('FE-FA-20 "Clear all" discards an open dropdown\'s pending edits and resets all four multi-selects in one shot (AC-F18)', () => {
  it("clicking Clear all with Location pending {Spain} resets all multi-selects and fires one refresh", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Apply {Acme Corp} for Company
    let wrap = openDropdown("All companies");
    await waitFor(() => within(wrap).getByText("Acme Corp"));
    clickOption(wrap, "Acme Corp");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    // Open Location, tick Spain (pending only, not applied)
    const locWrap = openDropdown("All locations");
    await waitFor(() => within(locWrap).getByText("Spain"));
    clickOption(locWrap, "Spain");

    // Click Clear all
    const clearAllBtn = screen.getByRole("button", { name: /clear all/i });
    fireEvent.click(clearAllBtn);

    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    const lastCall = searchJobs.mock.calls[0][0];
    expect(lastCall.page).toBe(0);
    expect(lastCall.company == null || lastCall.company.length === 0).toBe(true);
    expect(lastCall.location == null || lastCall.location.length === 0).toBe(true);
    expect(lastCall.employmentType == null || lastCall.employmentType.length === 0).toBe(true);
    expect(lastCall.careerLevel == null || lastCall.careerLevel.length === 0).toBe(true);

    // No chips for any of the four dimensions
    const chips = document.querySelectorAll(".chip.active");
    expect(chips.length).toBe(0);

    // Re-open Location — nothing checked (pending discarded too)
    const reopenWrap = openDropdown("All locations");
    await waitFor(() => within(reopenWrap).getByText("Spain"));
    expect(getOptionCheckbox(reopenWrap, "Spain").checked).toBe(false);
  });
});

// ── FE-FA-21: applying a saved filter overwrites all four, discards open pending (AC-F19) ──
describe("FE-FA-21 applying a saved filter overwrites all four multi-selects and discards an open dropdown's pending edits (AC-F19)", () => {
  it("selecting a saved filter sets the saved companies/locations/careerLevels/employmentTypes and fires one refresh", async () => {
    // Story #523: presets now come from listSavedFilters, not localStorage.
    const saved = {
      id: "sf-1",
      name: "My filter",
      filters: { company: ["Beta Ltd"], location: ["Germany"], careerLevel: ["senior"] },
    };

    await renderAndSettle({ authed: true }, [saved]);
    vi.clearAllMocks();
    setupMocks();

    // Apply {Acme Corp} for Company
    let wrap = openDropdown("All companies");
    await waitFor(() => within(wrap).getByText("Acme Corp"));
    clickOption(wrap, "Acme Corp");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    // Re-open Company, additionally tick Beta Ltd (pending = {Acme Corp, Beta Ltd}, not applied)
    wrap = openDropdown("Acme Corp");
    await waitFor(() => within(wrap).getByText("Beta Ltd"));
    clickOption(wrap, "Beta Ltd");

    // Apply the saved filter
    const savedFiltersTrigger = screen.getByText("Saved filters");
    fireEvent.click(savedFiltersTrigger);
    const filterItem = await waitFor(() => screen.getByText("My filter"));
    fireEvent.click(filterItem);

    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    const lastCall = searchJobs.mock.calls[0][0];
    expect(lastCall.page).toBe(0);
    expect(lastCall.company).toEqual(["Beta Ltd"]);
    expect(lastCall.location).toEqual(["Germany"]);
    expect(lastCall.careerLevel).toEqual(["senior"]);
    expect(lastCall.employmentType == null || lastCall.employmentType.length === 0).toBe(true);

    await waitFor(() => {
      const chips = document.querySelectorAll(".chip.active");
      expect([...chips].some((c) => c.textContent.includes("Beta Ltd"))).toBe(true);
      expect([...chips].some((c) => c.textContent.includes("Germany"))).toBe(true);
      expect([...chips].some((c) => c.textContent.includes("Senior"))).toBe(true);
      expect([...chips].some((c) => c.textContent.includes("Acme Corp"))).toBe(false);
    });
  });
});

// ── FE-FA-22: empty option list => Career level field still rendered, Apply disabled (AC-F20, BR-8 / fix #61) ──
describe("FE-FA-22 Apply is disabled / dropdown not rendered when a dropdown's option list is empty (AC-F20, BR-8)", () => {
  it("Career level field label is still rendered when careerLevels facet is empty (fixed behavior)", async () => {
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue({ ...DEFAULT_FACETS, careerLevels: [] });
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    render(<JobSearchScreen {...DEFAULT_PROPS} />);
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    // Fixed behavior (#61): field label renders unconditionally even with empty options
    expect(screen.getByText("Career level")).toBeInTheDocument();
    expect(screen.getByText("All career levels")).toBeInTheDocument();
  });
});

// ── FE-FA-23: Company dropdown with a "No results" search shows disabled Apply (AC-F20, BR-8) ──
describe('FE-FA-23 Company dropdown with a search term yielding "No results" shows disabled Apply (AC-F20, BR-8)', () => {
  it("typing a non-matching search term shows 'No results' and a disabled Apply button", async () => {
    // More than 5 company options so the search input renders.
    const manyCompanies = {
      companies: Array.from({ length: 6 }, (_, i) => ({ value: `Company ${i}`, count: i + 1 })),
    };
    vi.clearAllMocks();
    getJobFacets.mockResolvedValue({ ...DEFAULT_FACETS, ...manyCompanies });
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

    render(<JobSearchScreen {...DEFAULT_PROPS} />);
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());

    vi.clearAllMocks();
    setupMocks(manyCompanies);

    const wrap = openDropdown("All companies");
    await waitFor(() => within(wrap).getByPlaceholderText("Search…"));

    fireEvent.change(within(wrap).getByPlaceholderText("Search…"), { target: { value: "zzzznotfound" } });

    await waitFor(() => within(wrap).getByText("No results"));

    const applyBtn = within(wrap).getByRole("button", { name: "Apply" });
    expect(applyBtn).toBeDisabled();
  });
});

// ── FE-FA-24: a pending selection absent from facets still appears, checked (AC-F21, BR-10) ──
describe("FE-FA-24 a pending selection absent from the last facets response still appears, checked (AC-F21, BR-10)", () => {
  it("ticking 'France' (not in DEFAULT_FACETS.locations) shows it checked in the option list", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));

    // "France" is not part of DEFAULT_FACETS.locations — simulate ticking it via
    // a saved-filter style applied value isn't possible here, so we assert the
    // merge behaviour using an option that IS absent: directly verify the
    // dropdown renders only known facets initially, then that ticking and
    // re-rendering keeps a not-yet-applied pending value.
    // Since MultiSelect only knows about `options`, the merge must come from
    // `applied`/`pending` passed in by JobSearch. We simulate this by applying
    // Location={France} via a saved filter (overwrites applied to a value
    // absent from facets.locations) and then opening the dropdown.

    // Story #523: presets now come from listSavedFilters, not localStorage.
    const saved = {
      id: "sf-france",
      name: "France filter",
      filters: { location: ["France"] },
    };
    // Discard current dropdown state first
    fireEvent.mouseDown(document);

    // re-render with authed to access saved filters
    cleanup();
    vi.clearAllMocks();
    setupMocks({}, {}, [saved]);
    render(<JobSearchScreen {...DEFAULT_PROPS} authed={true} />);
    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());
    await waitFor(() => expect(searchJobs).toHaveBeenCalled());
    await new Promise((r) => setTimeout(r, 400));
    await waitFor(() => {});

    vi.clearAllMocks();
    setupMocks();

    const savedFiltersTrigger = screen.getByText("Saved filters");
    fireEvent.click(savedFiltersTrigger);
    const filterItem = await waitFor(() => screen.getByText("France filter"));
    fireEvent.click(filterItem);

    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    // Open Location dropdown — "France" must appear, checked, even though absent from
    // DEFAULT_FACETS.locations
    const locWrap = openDropdown("France");
    await waitFor(() => expect(within(locWrap).getAllByText("France").length).toBeGreaterThanOrEqual(1));
    expect(getOptionCheckbox(locWrap, "France").checked).toBe(true);

    expect(getJobFacets).toHaveBeenCalledTimes(1);
    expect(searchJobs).toHaveBeenCalledTimes(1);
  });
});

// ── FE-FA-25: tick-then-untick same option, close via click-outside: no refetch (AC-F22) ──
describe("FE-FA-25 tick-then-untick same option then click-outside causes no refetch (AC-F22)", () => {
  it("ticking and unticking 'Contract' then clicking outside leaves selectedEmploymentTypes unchanged", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    // Apply {full-time}
    let wrap = openDropdown("All employment types");
    await waitFor(() => within(wrap).getByText("Full-time"));
    clickOption(wrap, "Full-time");
    fireEvent.click(getApplyButton(wrap));
    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    vi.clearAllMocks();
    setupMocks();

    wrap = openDropdown("Full-time");
    await waitFor(() => within(wrap).getByText("Contract"));
    clickOption(wrap, "Contract");
    clickOption(wrap, "Contract");

    expect(within(wrap).getByRole("button", { name: "Apply" })).toBeDisabled();

    fireEvent.mouseDown(document);

    expect(getJobFacets).not.toHaveBeenCalled();
    expect(searchJobs).not.toHaveBeenCalled();

    const chips = document.querySelectorAll(".chip.active");
    expect([...chips].some((c) => c.textContent.includes("Full-time"))).toBe(true);
    expect([...chips].some((c) => c.textContent.includes("Contract"))).toBe(false);
  });
});

// ── FE-FA-26: single-choice controls trigger their own immediate refresh independent of pending (out-of-scope confirmation) ──
describe("FE-FA-26 single-choice controls (Posted) still trigger their own immediate refresh independent of multi-select pending (BR-4)", () => {
  it("changing Posted while Location has unapplied pending edits fires its own refresh and does not affect the pending edits", async () => {
    await renderAndSettle();
    vi.clearAllMocks();
    setupMocks();

    const wrap = openDropdown("All locations");
    await waitFor(() => within(wrap).getByText("Spain"));
    clickOption(wrap, "Spain");

    // Change "Posted" select
    const selects = document.querySelectorAll("select");
    const postedSelect = [...selects].find((s) => [...s.options].some((o) => o.text === "Past week"));
    expect(postedSelect).toBeDefined();
    fireEvent.change(postedSelect, { target: { value: "week" } });

    await waitFor(() => expect(getJobFacets).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(searchJobs).toHaveBeenCalledTimes(1));

    const lastCall = searchJobs.mock.calls[0][0];
    expect(lastCall.postedWithin).toBe("week");
    // Location pending edit unaffected — still empty applied (postedWithin's own
    // refresh used last-applied location, which was empty)
    expect(lastCall.location == null || lastCall.location.length === 0).toBe(true);

    // Spain is still shown checked (pending edit preserved)
    expect(getOptionCheckbox(wrap, "Spain").checked).toBe(true);
  });
});
