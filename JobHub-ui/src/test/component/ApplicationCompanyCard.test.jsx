/**
 * Component tests: the Company sidebar card on ApplicationDetailScreen (story #427,
 * sub-issue #441). Source: GitHub issue #440 comment, cases QAE-427-CARD-01..08
 * (CARD-09 is the pre-existing CR-UI-332 regression in ApplicationReminders.wire.test.jsx,
 * left untouched).
 *
 * Cases:
 *   QAE-427-CARD-01 (AC1) Card title is the real company name, never the literal "Company"
 *   QAE-427-CARD-02 (AC1) Company name also appears in the sidebar details, not only the header
 *   QAE-427-CARD-03 (AC2, regression control) Populated industry/size/hq all render
 *   QAE-427-CARD-04 (AC2) Each field independently omitted for null / missing key / blank string
 *   QAE-427-CARD-05 (AC2) All three empty at once: zero KV rows, card still renders
 *   QAE-427-CARD-06 (Tags removal) Tags row is gone regardless of job.tags contents
 *   QAE-427-CARD-07 (AC3) Blank company name degrades to a neutral, non-empty fallback label
 *   QAE-427-CARD-08 (AC3) Missing (undefined) company name behaves identically to CARD-07
 */
import React from "react";
import { render, screen, within } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

import DATA from "../../data/mockData.js";
import { ApplicationDetailScreen } from "../../screens/Applications.jsx";

const JOB_ID = "job-427-card";

function appWithoutApiId(overrides = {}) {
  return {
    apiId: undefined,
    jobId: JOB_ID,
    status: "applied",
    notes: "",
    timeline: [],
    appliedOn: "2026-06-01",
    lastUpdate: "Jun 1, 10:00 AM",
    ...overrides,
  };
}

function seedJob(overrides = {}) {
  DATA.jobs.push({
    id: JOB_ID,
    title: "Senior Engineer",
    co: "acme",
    location: "Remote",
    comp: "$120k",
    type: "Full-time",
    source: "LinkedIn",
    tags: [],
    ...overrides,
  });
}

function renderDetail(props = {}) {
  return render(
    <ApplicationDetailScreen
      app={appWithoutApiId()}
      goto={vi.fn()}
      onBack={vi.fn()}
      openSearch={vi.fn()}
      onDelete={vi.fn()}
      onStatusChange={vi.fn()}
      onNotesSave={vi.fn()}
      onEditSave={vi.fn()}
      onLogout={vi.fn()}
      {...props}
    />
  );
}

function getCompanyCard() {
  const card = document.querySelector(".company-card");
  expect(card).toBeTruthy();
  return card;
}

beforeEach(() => {
  DATA.jobs.length = 0;
  Object.keys(DATA.companies).forEach((k) => delete DATA.companies[k]);
});

describe("QAE-427-CARD-01 (AC1): card title is the real company name", () => {
  it("shows a card-header title equal to the company name, never the literal 'Company'", () => {
    seedJob();
    DATA.companies["acme"] = { name: "Acme Corp", industry: "Software", size: "201-500", hq: "Remote", url: "" };

    renderDetail();

    const card = getCompanyCard();
    const titleEl = within(card).getByText("Acme Corp", { selector: ".card-header .title" });
    expect(titleEl).toBeInTheDocument();

    const allTitles = Array.from(document.querySelectorAll(".card-header .title"));
    expect(allTitles.some((el) => el.textContent === "Company")).toBe(false);
  });
});

describe("QAE-427-CARD-02 (AC1): company name also appears in the sidebar details", () => {
  it("renders the company name at least twice, one instance inside a card header", () => {
    seedJob();
    DATA.companies["acme"] = { name: "Acme Corp", industry: "Software", size: "201-500", hq: "Remote", url: "" };

    renderDetail();

    const matches = screen.getAllByText("Acme Corp");
    expect(matches.length).toBeGreaterThanOrEqual(2);
    expect(matches.some((el) => el.closest(".card-header") !== null)).toBe(true);
  });
});

describe("QAE-427-CARD-03 (AC2, regression control): populated fields all render", () => {
  it("shows Industry/Size/HQ labels with their real values", () => {
    seedJob();
    DATA.companies["acme"] = { name: "Acme Corp", industry: "Software", size: "201-500", hq: "Remote", url: "" };

    renderDetail();

    const card = getCompanyCard();
    expect(within(card).getByText("Industry")).toBeInTheDocument();
    expect(within(card).getByText("Software")).toBeInTheDocument();
    expect(within(card).getByText("Size")).toBeInTheDocument();
    expect(within(card).getByText("201-500")).toBeInTheDocument();
    expect(within(card).getByText("HQ")).toBeInTheDocument();
    expect(within(card).getByText("Remote")).toBeInTheDocument();
  });
});

describe("QAE-427-CARD-04 (AC2): each field independently omitted", () => {
  it("(a) null industry: Industry row absent, Size/HQ still present", () => {
    seedJob();
    DATA.companies["acme"] = { name: "Acme Corp", industry: null, size: "201-500", hq: "Remote", url: "" };

    renderDetail();

    const card = getCompanyCard();
    expect(within(card).queryByText("Industry")).not.toBeInTheDocument();
    expect(within(card).getByText("Size")).toBeInTheDocument();
    expect(within(card).getByText("201-500")).toBeInTheDocument();
    expect(within(card).getByText("HQ")).toBeInTheDocument();
    expect(within(card).getByText("Remote")).toBeInTheDocument();
  });

  it("(b) size key omitted entirely (reads as undefined): Size row absent, Industry/HQ still present", () => {
    seedJob();
    DATA.companies["acme"] = { name: "Acme Corp", industry: "Software", hq: "Remote", url: "" };

    renderDetail();

    const card = getCompanyCard();
    expect(within(card).queryByText("Size")).not.toBeInTheDocument();
    expect(within(card).getByText("Industry")).toBeInTheDocument();
    expect(within(card).getByText("Software")).toBeInTheDocument();
    expect(within(card).getByText("HQ")).toBeInTheDocument();
    expect(within(card).getByText("Remote")).toBeInTheDocument();
  });

  it("(c) blank string hq: HQ row absent, Industry/Size still present", () => {
    seedJob();
    DATA.companies["acme"] = { name: "Acme Corp", industry: "Software", size: "201-500", hq: "", url: "" };

    renderDetail();

    const card = getCompanyCard();
    expect(within(card).queryByText("HQ")).not.toBeInTheDocument();
    expect(within(card).getByText("Industry")).toBeInTheDocument();
    expect(within(card).getByText("Software")).toBeInTheDocument();
    expect(within(card).getByText("Size")).toBeInTheDocument();
    expect(within(card).getByText("201-500")).toBeInTheDocument();
  });
});

describe("QAE-427-CARD-05 (AC2): all three fields empty at once", () => {
  it("renders the card with none of the Industry/Size/HQ labels present", () => {
    seedJob();
    DATA.companies["acme"] = { name: "Acme Corp", industry: null, size: null, hq: null, url: "" };

    renderDetail();

    const card = getCompanyCard();
    expect(within(card).getByText("Acme Corp", { selector: ".card-header .title" })).toBeInTheDocument();
    expect(within(card).queryByText("Industry")).not.toBeInTheDocument();
    expect(within(card).queryByText("Size")).not.toBeInTheDocument();
    expect(within(card).queryByText("HQ")).not.toBeInTheDocument();
  });
});

describe("QAE-427-CARD-06: Tags row removed entirely", () => {
  it("(a) empty tags array: no Tags label anywhere near the company details", () => {
    seedJob({ tags: [] });
    DATA.companies["acme"] = { name: "Acme Corp", industry: "Software", size: "201-500", hq: "Remote", url: "" };

    renderDetail();

    const card = getCompanyCard();
    expect(within(card).queryByText("Tags")).not.toBeInTheDocument();
  });

  it("(b) non-empty tags: still no Tags label, and none of the tag values render inside the card", () => {
    seedJob({ tags: ["Remote", "Senior"] });
    DATA.companies["acme"] = { name: "Acme Corp", industry: "Software", size: "201-500", hq: "San Francisco", url: "" };

    renderDetail();

    const card = getCompanyCard();
    expect(within(card).queryByText("Tags")).not.toBeInTheDocument();
    expect(within(card).queryByText("Remote")).not.toBeInTheDocument();
    expect(within(card).queryByText("Senior")).not.toBeInTheDocument();
  });
});

describe("QAE-427-CARD-07 (AC3): blank company name degrades gracefully", () => {
  it("does not crash, shows a non-empty fallback title, and the rest of the screen renders normally", () => {
    seedJob();
    DATA.companies["acme"] = { name: "", industry: null, size: null, hq: null, url: "" };

    expect(() => renderDetail()).not.toThrow();

    const card = getCompanyCard();
    const titleEl = card.querySelector(".card-header .title");
    expect(titleEl).toBeTruthy();
    expect(titleEl.textContent.trim()).not.toBe("");

    expect(screen.getByRole("heading", { name: "Senior Engineer" })).toBeInTheDocument();
  });
});

describe("QAE-427-CARD-08 (AC3): missing (undefined) company name behaves identically", () => {
  it("does not crash and shows the same non-empty fallback title as a blank name", () => {
    seedJob();
    DATA.companies["acme"] = { name: undefined, industry: null, size: null, hq: null, url: "" };

    expect(() => renderDetail()).not.toThrow();

    const card = getCompanyCard();
    const titleEl = card.querySelector(".card-header .title");
    expect(titleEl).toBeTruthy();
    expect(titleEl.textContent.trim()).not.toBe("");

    expect(screen.getByRole("heading", { name: "Senior Engineer" })).toBeInTheDocument();
  });
});

// ── Website row (story #486, sub-issue #490): QAE-486-10/11/12 ──────────────

describe("QAE-486-10 (AC-486-10): website present renders a working link alongside Industry/Size/HQ", () => {
  it("shows a role=link element with href/target/rel, and the existing three rows still resolve", () => {
    seedJob();
    DATA.companies["acme"] = {
      name: "Acme Corp", industry: "Software", size: "201-500", hq: "Remote",
      website: "https://acme.example.com", url: "",
    };

    renderDetail();

    const card = getCompanyCard();
    const link = within(card).getByRole("link");
    expect(link).toHaveAttribute("href", "https://acme.example.com");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link.getAttribute("rel")).toMatch(/noopener/i);

    expect(within(card).getByText("Industry")).toBeInTheDocument();
    expect(within(card).getByText("Software")).toBeInTheDocument();
    expect(within(card).getByText("Size")).toBeInTheDocument();
    expect(within(card).getByText("201-500")).toBeInTheDocument();
    expect(within(card).getByText("HQ")).toBeInTheDocument();
    expect(within(card).getByText("Remote")).toBeInTheDocument();
  });
});

describe("QAE-486-11 (AC-486-11): website null/missing is gracefully absent", () => {
  it("(a) website null, Industry/Size/HQ populated: no link, no dash", () => {
    seedJob();
    DATA.companies["acme"] = {
      name: "Acme Corp", industry: "Software", size: "201-500", hq: "Remote",
      website: null, url: "",
    };

    renderDetail();

    const card = getCompanyCard();
    expect(within(card).queryAllByRole("link").some((el) => el.getAttribute("href") === "https://acme.example.com")).toBe(false);
    expect(within(card).queryByText(/^-$/)).not.toBeInTheDocument();
    expect(within(card).getByText("Industry")).toBeInTheDocument();
    expect(within(card).getByText("Size")).toBeInTheDocument();
    expect(within(card).getByText("HQ")).toBeInTheDocument();
  });

  it("(b) website null and Industry/Size/HQ also all null: website omission independent of the other three", () => {
    seedJob();
    DATA.companies["acme"] = { name: "Acme Corp", industry: null, size: null, hq: null, website: null, url: "" };

    renderDetail();

    const card = getCompanyCard();
    expect(within(card).queryByRole("link")).not.toBeInTheDocument();
    expect(within(card).queryByText("Industry")).not.toBeInTheDocument();
    expect(within(card).queryByText("Size")).not.toBeInTheDocument();
    expect(within(card).queryByText("HQ")).not.toBeInTheDocument();
  });
});

describe("QAE-486-12 (AC-486-12): tags exclusion regression lock, website populated at the same time", () => {
  it("no Tags label or tag values render even when a non-empty tags array and a website coexist", () => {
    seedJob({ tags: ["Remote", "Senior"] });
    DATA.companies["acme"] = {
      name: "Acme Corp", industry: "Software", size: "201-500", hq: "Remote",
      website: "https://acme.example.com", tags: ["fintech", "b2b"], url: "",
    };

    renderDetail();

    const card = getCompanyCard();
    expect(within(card).queryByText("Tags")).not.toBeInTheDocument();
    expect(within(card).queryByText("fintech")).not.toBeInTheDocument();
    expect(within(card).queryByText("b2b")).not.toBeInTheDocument();
    expect(within(card).queryByText("Remote")).toBeInTheDocument(); // this is the HQ value, not a tag
    expect(within(card).getByRole("link")).toHaveAttribute("href", "https://acme.example.com");
  });
});
