/**
 * Component tests for JobRow (search results list card) — multiple locations display
 * Story #1, sub-issue #293. Cases: QAE-UI-DISPLAY-1, QAE-UI-DISPLAY-2, QAE-UI-DISPLAY-2B,
 * QAE-UI-EDGE-1 (list side), QAE-UI-EDGE-2 (list side).
 *
 * "+N" affordance mechanism chosen: a `data-testid="location-more"` element whose text
 * content is `+{N} more` where N = job.locations.length - 1 (additional openings beyond
 * the primary). Used consistently for both DISPLAY-2 (N=2) and DISPLAY-2B (N=1) so the
 * two cases test the same computation, not divergent markup.
 */
import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

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

async function renderRow(jobOverrides = {}) {
  const UI = await import("../../components/ui.jsx");
  const { JobRow } = UI;
  const job = makeJob(jobOverrides);
  return render(
    <JobRow job={job} onSave={vi.fn()} isSaved={false} isApplied={false} onOpen={vi.fn()} />
  );
}

describe("JobRow — multiple locations (QAE-UI-DISPLAY-1/2/2B, QAE-UI-EDGE-1/2)", () => {
  it("QAE-UI-DISPLAY-1: single-opening posting shows primary location unchanged, no +N affordance", async () => {
    await renderRow({
      location: "Madrid, Spain",
      locations: [{ country: "Spain", city: "Madrid", primary: true }],
    });
    expect(screen.getByText("Madrid, Spain")).toBeInTheDocument();
    expect(screen.queryByTestId("location-more")).not.toBeInTheDocument();
  });

  it("QAE-UI-DISPLAY-2: posting with 2 additional openings (3 total) shows a +2 affordance", async () => {
    await renderRow({
      location: "Barcelona, Spain",
      locations: [
        { country: "Spain", city: "Barcelona", primary: true },
        { country: "Netherlands", city: "Amsterdam", primary: false },
        { country: "France", city: "Paris", primary: false },
      ],
    });
    const affordance = screen.getByTestId("location-more");
    expect(affordance).toHaveTextContent("+2");
  });

  it("QAE-UI-DISPLAY-2B: posting with exactly 1 additional opening (2 total) shows a +1 affordance", async () => {
    await renderRow({
      location: "Barcelona, Spain",
      locations: [
        { country: "Spain", city: "Barcelona", primary: true },
        { country: "Netherlands", city: "Amsterdam", primary: false },
      ],
    });
    const affordance = screen.getByTestId("location-more");
    expect(affordance).toHaveTextContent("+1");
  });

  it("QAE-UI-EDGE-1: empty/absent locations renders without crashing and without a +N affordance", async () => {
    await renderRow({ location: undefined, locations: [] });
    expect(screen.getByText("Backend Engineer")).toBeInTheDocument();
    expect(screen.queryByTestId("location-more")).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain("undefined");
  });

  it("QAE-UI-EDGE-2: Remote-only posting renders 'Remote' with no +N affordance", async () => {
    await renderRow({
      location: "Remote",
      locations: [{ country: "Remote", city: null, primary: true }],
    });
    expect(screen.getByText("Remote")).toBeInTheDocument();
    expect(screen.queryByTestId("location-more")).not.toBeInTheDocument();
  });
});
