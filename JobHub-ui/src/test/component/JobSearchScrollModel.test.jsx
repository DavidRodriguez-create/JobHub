/**
 * Component/CSS-source tests for the job-search scroll model (story #539, issue #547).
 * Cases: TC-539-01..05, TC-539-17
 *
 * Story #539 reverses #458: the filter column must never be sticky or internally
 * scrollable at any breakpoint, and the job-post list becomes the one bounded/scrollable
 * region. jsdom does not evaluate real stylesheet layout (no @media, no computed
 * position/scroll behaviour), so these cases read the real src/styles/styles.css with
 * fs.readFileSync and regex-match the relevant selector blocks, following the pattern the
 * retired JobSearchStickyFilters.test.jsx used for the equivalent #458 assertions.
 */
import React from "react";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { render, waitFor } from "@testing-library/react";
import { describe, it, expect, afterEach } from "vitest";

// ── Mocks (mirrors JobSearchScreen.test.jsx's scaffold, needed only for TC-539-04) ──

import { vi } from "vitest";

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
    coOf: () => ({ name: "N/A", industry: "N/A", size: "N/A", hq: "N/A", url: "" }),
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

const { searchJobs, getJobFacets } = await import("../../api/jobs.js");

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

function readStylesCss() {
  const stylesPath = path.join(
    path.dirname(fileURLToPath(import.meta.url)),
    "../../styles/styles.css"
  );
  return fs.readFileSync(stylesPath, "utf8");
}

// Every rule block whose selector chain touches .search-filters: the unscoped base rule,
// the @media (max-width: 860px) override, and the .search-layout--collapsed override.
function searchFiltersBlocks(css) {
  const blocks = [];
  const re = /\.search-filters\s*\{([^}]*)\}/g;
  let m;
  while ((m = re.exec(css)) !== null) blocks.push(m[1]);
  return blocks;
}

function resultsListBlocks(css) {
  const blocks = [];
  const re = /\.results-list\s*\{([^}]*)\}/g;
  let m;
  while ((m = re.exec(css)) !== null) blocks.push(m[1]);
  return blocks;
}

// ── TC-539-01: .search-filters is never sticky/fixed, at any breakpoint/collapsed state ──

describe("styles.css: .search-filters is never sticky or fixed (TC-539-01)", () => {
  it("no .search-filters block (base, 860px override, collapsed override) declares position: sticky or fixed", () => {
    const css = readStylesCss();
    const blocks = searchFiltersBlocks(css);
    expect(blocks.length).toBeGreaterThanOrEqual(3);
    blocks.forEach((block) => {
      expect(block).not.toMatch(/position:\s*(sticky|fixed)/);
    });
  });
});

// ── TC-539-02: .search-filters never carries its own bounded scroll ──────────────

describe("styles.css: .search-filters never has its own overflow-y/max-height (TC-539-02)", () => {
  it("no .search-filters block declares overflow(-y): auto|scroll or a max-height", () => {
    const css = readStylesCss();
    const blocks = searchFiltersBlocks(css);
    blocks.forEach((block) => {
      expect(block).not.toMatch(/overflow(-y)?:\s*(auto|scroll)/);
      expect(block).not.toMatch(/max-height/);
    });
  });
});

// ── TC-539-03: base .search-filters rule is normal flow (static/unset position) ──

describe("styles.css: base .search-filters rule is static or unset (TC-539-03)", () => {
  it("the unscoped base .search-filters block declares position: static, or has no position declaration", () => {
    const css = readStylesCss();
    const baseMatch = /^\.search-filters\s*\{([^}]*)\}/m.exec(css);
    expect(baseMatch).toBeTruthy();
    const base = baseMatch[1];
    const hasStatic = /position:\s*static/.test(base);
    const hasNoPosition = !/position:\s*\S+/.test(base);
    expect(hasStatic || hasNoPosition).toBe(true);
  });
});

// ── TC-539-04: results-list is the one bounded/scrollable region ─────────────────

describe("JobSearchScreen + styles.css: [data-testid='results-list'] is the bounded scroll container (TC-539-04)", () => {
  const originalInnerWidth = window.innerWidth;

  afterEach(() => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: originalInnerWidth,
    });
  });

  it("the DOM node's resolved class matches a styles.css rule declaring max-height and overflow-y: auto, on an un-collapsed desktop render (TC-458-S01 given-clause sanity, migrated)", async () => {
    Object.defineProperty(window, "innerWidth", {
      writable: true,
      configurable: true,
      value: 1280,
    });

    getJobFacets.mockResolvedValue(DEFAULT_FACETS);
    searchJobs.mockResolvedValue(EMPTY_SEARCH);

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

    await waitFor(() => expect(getJobFacets).toHaveBeenCalled());

    // Desktop-not-collapsed sanity (migrated from the retired JobSearchStickyFilters.test.jsx):
    // at 1280px the layout is not JS-collapsed and the filter column still renders.
    const layout = document.querySelector(".search-layout");
    expect(layout).not.toBeNull();
    expect(layout.classList.contains("search-layout--collapsed")).toBe(false);
    expect(document.querySelector(".search-filters")).not.toBeNull();

    const node = document.querySelector('[data-testid="results-list"]');
    expect(node).not.toBeNull();
    expect(node.classList.contains("results-list")).toBe(true);

    const css = readStylesCss();
    const blocks = resultsListBlocks(css);
    expect(blocks.length).toBeGreaterThanOrEqual(1);
    const base = blocks[0];
    expect(base).toMatch(/max-height:\s*\S+/);
    expect(base).toMatch(/overflow-y:\s*auto/);
  });
});

// ── TC-539-17: results-list has a floor so it never collapses to nothing on a very ──
// short viewport (defect surfaced in QAE end-review of #547: the unbounded calc()
// reaches 0/negative below roughly viewport-height 316px, the exact short-viewport
// regime story #539 is about).

describe("styles.css: .results-list has a height floor on very short viewports (TC-539-17)", () => {
  it("the base .results-list rule declares a min-height, or a max() floor inside its max-height calc", () => {
    const css = readStylesCss();
    const blocks = resultsListBlocks(css);
    expect(blocks.length).toBeGreaterThanOrEqual(1);
    const base = blocks[0];
    const hasMinHeight = /min-height:\s*\S+/.test(base);
    const hasMaxFloor = /max-height:\s*max\(/.test(base);
    expect(hasMinHeight || hasMaxFloor).toBe(true);
  });
});

// ── TC-539-05: results-list is never sticky/fixed, at any breakpoint ─────────────

describe("styles.css: .results-list is never sticky or fixed (TC-539-05)", () => {
  it("no .results-list block (base or any breakpoint override) declares position: sticky or fixed", () => {
    const css = readStylesCss();
    const blocks = resultsListBlocks(css);
    expect(blocks.length).toBeGreaterThanOrEqual(1);
    blocks.forEach((block) => {
      expect(block).not.toMatch(/position:\s*(sticky|fixed)/);
    });
  });
});
