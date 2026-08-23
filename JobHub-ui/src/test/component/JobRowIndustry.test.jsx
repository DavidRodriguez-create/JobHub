/**
 * Component tests for JobRow (search results list card) — industry segment.
 * Story #486, sub-issue #490. Cases: QAE-486-01/02/03.
 *
 * Follows the JobRowLocations.test.jsx render pattern: mock ../../data/mockData.js,
 * vi.spyOn(DATA, "coOf") per test for industry variance, renderRow(jobOverrides)
 * importing { JobRow } from ../../components/ui.jsx.
 */
import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, afterEach } from "vitest";
import DATA from "../../data/mockData.js";

vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

vi.mock("../../data/mockData.js", () => ({
  default: {
    companies: {},
    jobs: [],
    coOf: () => ({ name: "Acme Corp", industry: "Technology", size: "100-500", hq: "Madrid, Spain", url: "" }),
  },
}));

function makeJob(overrides = {}) {
  return {
    id: "job-1",
    co: "acme",
    title: "Backend Engineer",
    location: "Madrid, Spain",
    comp: "€60k–€80k",
    compMin: 60,
    compMax: 80,
    type: "Full-time",
    postedDays: 2,
    source: "Greenhouse",
    remote: false,
    tags: [],
    country: "Spain",
    language: "English",
    desc: "",
    reqs: [],
    url: "https://example.com/job",
    locations: [{ country: "Spain", city: "Madrid", primary: true }],
    ...overrides,
  };
}

async function renderRow(jobOverrides = {}, props = {}) {
  const UI = await import("../../components/ui.jsx");
  const { JobRow } = UI;
  const job = makeJob(jobOverrides);
  const defaultProps = { job, onSave: vi.fn(), isSaved: false, isApplied: false, onOpen: vi.fn() };
  return render(<JobRow {...defaultProps} {...props} />);
}

describe("JobRow — industry segment (QAE-486-01/02/03)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("QAE-486-01: non-null industry renders as a segment in the meta line", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", size: "100-500", hq: "Madrid, Spain", url: "",
    });

    await renderRow();

    expect(screen.getByTestId("job-row-industry")).toHaveTextContent("Fintech");
  });

  it("QAE-486-02: null industry is omitted, no dash/undefined, rest of the row unaffected", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: null, size: "100-500", hq: "Madrid, Spain", url: "",
    });

    await renderRow({ location: "Madrid, Spain", comp: "€60k–€80k", postedDays: 2 });

    expect(screen.queryByTestId("job-row-industry")).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain("undefined");
    expect(document.body.textContent).not.toMatch(/[-–—]\s*$/);
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Madrid, Spain")).toBeInTheDocument();
    expect(screen.getByText("€60k–€80k")).toBeInTheDocument();
    expect(screen.getByText("Posted 2d ago")).toBeInTheDocument();
  });

  it("QAE-486-03: additive alongside +N more / NEW / APPLIED, correct DOM order (name < industry < location)", async () => {
    vi.spyOn(DATA, "coOf").mockReturnValue({
      name: "Acme Corp", industry: "Fintech", size: "100-500", hq: "Madrid, Spain", url: "",
    });

    await renderRow(
      {
        location: "Barcelona, Spain",
        postedDays: 2,
        locations: [
          { country: "Spain", city: "Barcelona", primary: true },
          { country: "Netherlands", city: "Amsterdam", primary: false },
          { country: "France", city: "Paris", primary: false },
        ],
      },
      { isApplied: true }
    );

    const more = screen.getByTestId("location-more");
    expect(more).toHaveTextContent("+2");
    expect(screen.getByText("Barcelona, Spain")).toBeInTheDocument();
    expect(screen.getByText("€60k–€80k")).toBeInTheDocument();
    expect(screen.getByText("Posted 2d ago")).toBeInTheDocument();
    expect(screen.getByText("NEW")).toBeInTheDocument();
    expect(screen.getByText("APPLIED")).toBeInTheDocument();

    const industry = screen.getByTestId("job-row-industry");
    expect(industry).toHaveTextContent("Fintech");

    const nameNode = screen.getByText("Acme Corp");
    const locationNode = screen.getByText("Barcelona, Spain");
    // eslint-disable-next-line no-bitwise
    expect(nameNode.compareDocumentPosition(industry) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    // eslint-disable-next-line no-bitwise
    expect(industry.compareDocumentPosition(locationNode) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });
});
