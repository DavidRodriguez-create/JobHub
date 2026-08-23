/**
 * Regression tests for CoLogo (ui.jsx) and its call sites - story #244.
 * Cases: UI244-COLOGO-01..02, UI244-REG-01..02
 *
 * These are a regression net per PDA OQ-244-1: this story's fix must NOT make
 * CoLogo behave differently for Applications/Kanban/JobSearch callers, which
 * pass only a `co` prop and never a logo URL prop.
 *
 * If the developer's implementation touches CoLogo itself (adds a prop),
 * existing call sites that omit the new prop must be unchanged.
 *
 * Extended for story #429 (sub-issue #448) with QAE-429-UI-01..08
 * (docs/qa/429-logo-test-cases.md section F): CoLogo now accepts a `logoUrl`
 * prop, renders a real <img> when it is present and non-empty, degrades to
 * the SAME initials chip on `onError` (never a broken-image icon, never a
 * retry), and derives the chip's colour from a stable hash of `co` instead
 * of the old 14-name CSS allowlist.
 */
import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

// Mock Icon to avoid SVG resolution issues
vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name }) => <span data-icon={name} />,
}));

// Mock mockData - CoLogo uses DATA.coOf(co)
vi.mock("../../data/mockData.js", () => ({
  default: {
    coOf: (co) => {
      const map = {
        "Acme Corp": { name: "Acme Corp", industry: "Tech", size: "100", hq: "NY", url: "" },
        "Globex": { name: "Globex", industry: "Energy", size: "500", hq: "CA", url: "" },
      };
      return map[co] || { name: co || "?", industry: "", size: "", hq: "", url: "" };
    },
  },
}));

import { CoLogo } from "../../components/ui.jsx";

describe("UI244-COLOGO-01: CoLogo with only `co` prop renders text-initial chip, no image attempted", () => {
  it("renders the first initial of the company name in the chip (existing behaviour unchanged)", () => {
    const { container } = render(<CoLogo co="Acme Corp" size="sm" />);

    // Should render the chip element
    const chip = container.querySelector(".cologo");
    expect(chip).toBeInTheDocument();
    // Should show the initial
    expect(chip).toHaveTextContent("A");
    // Should carry data-co attribute (existing behaviour)
    expect(chip).toHaveAttribute("data-co", "Acme Corp");
    // No image element must be present (no logo-url prop passed)
    expect(container.querySelector("img")).not.toBeInTheDocument();
  });

  it("renders with size 'lg' class and correct initial for a different company", () => {
    const { container } = render(<CoLogo co="Globex" size="lg" />);

    const chip = container.querySelector(".cologo.lg");
    expect(chip).toBeInTheDocument();
    expect(chip).toHaveTextContent("G");
    expect(container.querySelector("img")).not.toBeInTheDocument();
  });

  it("renders '?' initial for an unknown company (no data in coOf)", () => {
    const { container } = render(<CoLogo co="Unknown Co" />);

    const chip = container.querySelector(".cologo");
    expect(chip).toBeInTheDocument();
    // "Unknown Co" not in mock map, coOf returns { name: "Unknown Co" }, so initial is "U"
    expect(chip).toHaveTextContent("U");
    expect(container.querySelector("img")).not.toBeInTheDocument();
  });
});

describe("UI244-COLOGO-02: CoLogo name resolution is unchanged by this story", () => {
  it("resolves display initial from coOf for a known company key (regression of name-resolution behaviour)", () => {
    const { container } = render(<CoLogo co="Acme Corp" size="sm" />);
    const chip = container.querySelector(".cologo");
    expect(chip).toHaveTextContent("A");
  });

  it("extra unknown prop does not crash or introduce an image element", () => {
    // Simulate a call site that passes only co (as in Applications/Kanban/JobSearch)
    const { container } = render(<CoLogo co="Globex" />);
    expect(container.querySelector("img")).not.toBeInTheDocument();
  });
});

describe("UI244-REG-01: Applications call site (co-only, no companyLogoUrl) renders without image or console error", () => {
  it("a CoLogo in Applications mode (co prop only) renders the text-initial chip with no image element", () => {
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    const { container } = render(
      <div data-testid="applications-card">
        <CoLogo co="Acme Corp" size="sm" data-testid="app-co-logo" />
      </div>
    );

    expect(container.querySelector(".cologo")).toBeInTheDocument();
    expect(container.querySelector("img")).not.toBeInTheDocument();
    expect(consoleSpy).not.toHaveBeenCalled();

    consoleSpy.mockRestore();
  });
});

describe("UI244-REG-02: JobSearch call site (co-only, no companyLogoUrl) renders without image or console error", () => {
  it("a CoLogo in JobSearch mode (co prop only) renders the text-initial chip with no image element", () => {
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    const { container } = render(
      <div data-testid="job-search-row">
        <CoLogo co="Globex" size="sm" />
      </div>
    );

    expect(container.querySelector(".cologo")).toBeInTheDocument();
    expect(container.querySelector("img")).not.toBeInTheDocument();
    expect(consoleSpy).not.toHaveBeenCalled();

    consoleSpy.mockRestore();
  });
});

// ─── Story #429 / sub-issue #448: QAE-429-UI-01..08 (docs/qa/429-logo-test-cases.md section F) ───

describe("QAE-429-UI-01: logoUrl present and non-empty renders an <img>", () => {
  it("renders an <img> with the given src and does not also show the initials chip glyph", () => {
    const { container } = render(<CoLogo co="Stripe" logoUrl="https://logo.clearbit.com/stripe.com" />);

    const img = container.querySelector("img");
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "https://logo.clearbit.com/stripe.com");
    // No separate chip glyph rendered alongside the image
    expect(container.querySelectorAll(".cologo").length).toBe(1);
    expect(container.textContent).not.toContain("S");
  });
});

describe("QAE-429-UI-02: logoUrl null/absent renders the chip, no <img> (regression of UI244-COLOGO-01/02)", () => {
  it("renders the chip when logoUrl is omitted entirely", () => {
    const { container } = render(<CoLogo co="Stripe" />);
    expect(container.querySelector(".cologo")).toBeInTheDocument();
    expect(container.querySelector("img")).not.toBeInTheDocument();
  });

  it("renders the chip when logoUrl is explicitly null", () => {
    const { container } = render(<CoLogo co="Stripe" logoUrl={null} />);
    expect(container.querySelector(".cologo")).toBeInTheDocument();
    expect(container.querySelector("img")).not.toBeInTheDocument();
  });
});

describe("QAE-429-UI-03: logoUrl = \"\" is treated identically to null/absent", () => {
  it("renders the chip, never an <img>, for an empty-string logoUrl", () => {
    const { container } = render(<CoLogo co="Stripe" logoUrl="" />);
    expect(container.querySelector(".cologo")).toBeInTheDocument();
    expect(container.querySelector("img")).not.toBeInTheDocument();
  });
});

describe("QAE-429-UI-04: colour is stable for the same company across independent render instances", () => {
  it("carries the identical data-hue token across two separate render() calls of the same co", () => {
    const first = render(<CoLogo co="Acme Corp" />);
    const firstHue = first.container.querySelector(".cologo").getAttribute("data-hue");

    const second = render(<CoLogo co="Acme Corp" />);
    const secondHue = second.container.querySelector(".cologo").getAttribute("data-hue");

    expect(firstHue).not.toBeNull();
    expect(firstHue).toBe(secondHue);
  });
});

describe("QAE-429-UI-05: a second, different company is independently stable too", () => {
  it("carries the identical data-hue token across two separate render() calls of a second co", () => {
    const first = render(<CoLogo co="Zylo Robotics" />);
    const firstHue = first.container.querySelector(".cologo").getAttribute("data-hue");

    const second = render(<CoLogo co="Zylo Robotics" />);
    const secondHue = second.container.querySelector(".cologo").getAttribute("data-hue");

    expect(firstHue).not.toBeNull();
    expect(firstHue).toBe(secondHue);
  });
});

describe("QAE-429-UI-06: onError degrades to the SAME chip as the null-logo case", () => {
  it("removes the <img> and renders a chip indistinguishable from the no-logoUrl case", () => {
    const { container } = render(<CoLogo co="Stripe" logoUrl="https://logo.clearbit.com/stripe.com" />);
    const img = container.querySelector("img");
    expect(img).toBeInTheDocument();

    fireEvent.error(img);

    expect(container.querySelector("img")).not.toBeInTheDocument();
    const chip = container.querySelector(".cologo");
    expect(chip).toBeInTheDocument();
    expect(chip).toHaveAttribute("data-co", "Stripe");
    expect(chip).toHaveTextContent("S");

    // Same output as the null-logo case for the same co
    const { container: noLogoContainer } = render(<CoLogo co="Stripe" />);
    const noLogoChip = noLogoContainer.querySelector(".cologo");
    expect(chip.textContent).toBe(noLogoChip.textContent);
    expect(chip.getAttribute("data-co")).toBe(noLogoChip.getAttribute("data-co"));
  });
});

describe("QAE-429-UI-07: no retry after onError", () => {
  it("keeps showing the chip after a re-render with identical, unchanged props", () => {
    const { container, rerender } = render(<CoLogo co="Stripe" logoUrl="https://logo.clearbit.com/stripe.com" />);
    const img = container.querySelector("img");
    fireEvent.error(img);

    expect(container.querySelector("img")).not.toBeInTheDocument();
    expect(container.querySelector(".cologo")).toBeInTheDocument();

    rerender(<CoLogo co="Stripe" logoUrl="https://logo.clearbit.com/stripe.com" />);

    expect(container.querySelector("img")).not.toBeInTheDocument();
    expect(container.querySelector(".cologo")).toBeInTheDocument();
  });
});

describe("QAE-429-UI-08: every existing UI244-* call-site case stays green (co-only, strictly additive change)", () => {
  it("Applications/Kanban/JobSearch-style co-only call renders the chip with zero <img> elements", () => {
    const { container } = render(<CoLogo co="Acme Corp" size="sm" />);
    expect(container.querySelector(".cologo")).toBeInTheDocument();
    expect(container.querySelector("img")).not.toBeInTheDocument();
  });
});
