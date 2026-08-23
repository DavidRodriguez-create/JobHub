/**
 * Component tests for NotificationIdentity.jsx
 * Story #244 / Ticket #260: job-title gate fix + company logo rendering.
 *
 * Cases: UI244-NID-01..14 from docs/test-cases/244-notification-card-title-and-logo-fix-test-cases.md
 *
 * Strategy: render <NotificationIdentity notification={...} /> directly,
 * simulate image load/error events with fireEvent, assert DOM state.
 * data-testid for the logo image: "notification-row-co-logo-image" (per PDA OQ-244-3).
 */
import React from "react";
import { render, screen, within, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

// Mock Icon to avoid SVG resolution issues in jsdom
vi.mock("../../components/Icon.jsx", () => ({
  default: ({ name, size }) => <span data-icon={name} data-size={size} />,
}));

// Mock mockData used by CoLogo
vi.mock("../../data/mockData.js", () => ({
  default: {
    coOf: (co) => ({ name: co || "?", industry: "", size: "", hq: "", url: "" }),
  },
}));

import { NotificationIdentity } from "../../components/NotificationIdentity.jsx";

// ─── helpers ───────────────────────────────────────────────────────────────────

function renderIdentity(notification) {
  return render(<NotificationIdentity notification={notification} />);
}

function queryLogoImage(container) {
  return container.querySelector('[data-testid="notification-row-co-logo-image"]');
}

// ─── UI244-NID-01..06: job title gate fix ─────────────────────────────────────

describe("UI244-NID-01..06: job title gate fix", () => {
  it("UI244-NID-01: full data (company + jobTitle + companyLogoUrl): label is jobTitle, image present with correct src", () => {
    const { container } = renderIdentity({
      company: "Acme Corp",
      jobTitle: "Senior Backend Engineer",
      companyLogoUrl: "https://cdn.example/acme.png",
    });

    expect(screen.getByTestId("notification-row-job-title")).toHaveTextContent("Senior Backend Engineer");
    const img = queryLogoImage(container);
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "https://cdn.example/acme.png");
    // initial-chip (CoLogo text) should not be the primary slot — image should be present
    expect(screen.queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
  });

  it("UI244-NID-02: company present, jobTitle present, companyLogoUrl null: label is jobTitle, text-initial chip shown, no image", () => {
    const { container } = renderIdentity({
      company: "Foo Inc",
      jobTitle: "Backend Dev",
      companyLogoUrl: null,
    });

    expect(screen.getByTestId("notification-row-job-title")).toHaveTextContent("Backend Dev");
    expect(screen.getByTestId("notification-row-co-logo")).toHaveAttribute("data-co", "Foo Inc");
    expect(queryLogoImage(container)).not.toBeInTheDocument();
    expect(screen.queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
  });

  it("UI244-NID-03: fully unresolved (all null): fallback icon + fallback label (AC-244-3 regression)", () => {
    const { container } = renderIdentity({
      company: null,
      jobTitle: null,
      companyLogoUrl: null,
    });

    expect(screen.getByTestId("notification-row-job-title")).toHaveTextContent("Application no longer available");
    expect(screen.getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(screen.queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
    expect(queryLogoImage(container)).not.toBeInTheDocument();
  });

  // UI244-NID-04: the HEADLINE regression check for this story (AC-244-4)
  it("UI244-NID-04: S4 gate-fix safety-net: company null, jobTitle present, logo null -> real title shown, generic fallback icon (NOT fallback label)", () => {
    const { container } = renderIdentity({
      company: null,
      jobTitle: "Senior Backend Engineer",
      companyLogoUrl: null,
    });

    // Title must be the real value, NOT the fallback string
    expect(screen.getByTestId("notification-row-job-title")).toHaveTextContent("Senior Backend Engineer");
    expect(screen.getByTestId("notification-row-job-title")).not.toHaveTextContent("Application no longer available");
    // Generic fallback icon (no company name available)
    expect(screen.getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(screen.queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
    expect(queryLogoImage(container)).not.toBeInTheDocument();
  });

  it("UI244-NID-05: two rows - S4 (company null) and S1/S2 (company present) both show their own titles, neither gates on the other (AC-244-5)", () => {
    const { container } = render(
      <>
        <NotificationIdentity notification={{ company: null, jobTitle: "Title A", companyLogoUrl: null }} />
        <NotificationIdentity notification={{ company: "Acme Corp", jobTitle: "Title B", companyLogoUrl: null }} />
      </>
    );

    const titles = container.querySelectorAll('[data-testid="notification-row-job-title"]');
    expect(titles[0]).toHaveTextContent("Title A");
    expect(titles[1]).toHaveTextContent("Title B");
  });

  it("UI244-NID-06: company present + logo present, jobTitle null: label is still fallback; logo still attempted independently (BR-244-2 independence)", () => {
    const { container } = renderIdentity({
      company: "Acme Corp",
      jobTitle: null,
      companyLogoUrl: "https://cdn.example/acme.png",
    });

    // Title gate is on jobTitle alone - no title means fallback label
    expect(screen.getByTestId("notification-row-job-title")).toHaveTextContent("Application no longer available");
    // Logo slot still attempts the image (BR-244-2 says logo is independent of title)
    const img = queryLogoImage(container);
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "https://cdn.example/acme.png");
  });
});

// ─── UI244-NID-07..14: company logo image + fallback ──────────────────────────

describe("UI244-NID-07..14: company logo image rendering and fallback", () => {
  it("UI244-NID-07: companyLogoUrl present, image load succeeds: image remains, no fallback chip", () => {
    const { container } = renderIdentity({
      company: "Acme Corp",
      jobTitle: "Engineer",
      companyLogoUrl: "https://cdn.example/acme.png",
    });

    const img = queryLogoImage(container);
    expect(img).toBeInTheDocument();

    // Simulate successful image load
    fireEvent.load(img);

    expect(queryLogoImage(container)).toBeInTheDocument();
    expect(screen.queryByTestId("notification-row-fallback-icon")).not.toBeInTheDocument();
  });

  it("UI244-NID-08: companyLogoUrl present, image load fails: falls back to text-initial chip (company present), title unaffected (AC-244-8)", () => {
    const { container } = renderIdentity({
      company: "Acme Corp",
      jobTitle: "Engineer",
      companyLogoUrl: "https://cdn.example/broken.png",
    });

    const img = queryLogoImage(container);
    expect(img).toBeInTheDocument();

    // Simulate image load error
    fireEvent.error(img);

    // Image should be gone or hidden; fallback chip should appear
    expect(screen.getByTestId("notification-row-co-logo")).toBeInTheDocument();
    expect(screen.getByTestId("notification-row-co-logo")).toHaveAttribute("data-co", "Acme Corp");
    // Title should be unaffected
    expect(screen.getByTestId("notification-row-job-title")).toHaveTextContent("Engineer");
  });

  it("UI244-NID-09: companyLogoUrl is empty string: no image element created, fallback shown immediately (AC-244-9, EC-244-2)", () => {
    const { container } = renderIdentity({
      company: "Foo Inc",
      jobTitle: "Dev",
      companyLogoUrl: "",
    });

    // No image should ever be created for empty string src
    expect(queryLogoImage(container)).not.toBeInTheDocument();
    // Text-initial chip shown instead
    expect(screen.getByTestId("notification-row-co-logo")).toHaveAttribute("data-co", "Foo Inc");
  });

  it("UI244-NID-10: logo present, company null (EC-244-1): image load succeeds -> loaded image renders, no error thrown (AC-244-10 success branch)", () => {
    const { container } = renderIdentity({
      company: null,
      jobTitle: "Engineer",
      companyLogoUrl: "https://cdn.example/acme.png",
    });

    const img = queryLogoImage(container);
    expect(img).toBeInTheDocument();

    expect(() => fireEvent.load(img)).not.toThrow();
    expect(queryLogoImage(container)).toBeInTheDocument();
  });

  it("UI244-NID-11: logo present, company null, image fails: generic fallback icon (not named-initial chip, no company name), no JS error (AC-244-10 failure branch, EC-244-1)", () => {
    const { container } = renderIdentity({
      company: null,
      jobTitle: "Engineer",
      companyLogoUrl: "https://cdn.example/broken.png",
    });

    const img = queryLogoImage(container);
    expect(img).toBeInTheDocument();

    expect(() => fireEvent.error(img)).not.toThrow();

    // Should fall back to the GENERIC fallback icon (no company = no initial chip)
    expect(screen.getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(screen.queryByTestId("notification-row-co-logo")).not.toBeInTheDocument();
  });

  it("UI244-NID-12: two rows with identical companyLogoUrl - error on row A does not affect row B (EC-244-3)", () => {
    const sharedUrl = "https://cdn.example/acme.png";
    const { container } = render(
      <>
        <div data-testid="row-a">
          <NotificationIdentity notification={{ company: "Acme Corp", jobTitle: "Role A", companyLogoUrl: sharedUrl }} />
        </div>
        <div data-testid="row-b">
          <NotificationIdentity notification={{ company: "Acme Corp", jobTitle: "Role B", companyLogoUrl: sharedUrl }} />
        </div>
      </>
    );

    const rowA = screen.getByTestId("row-a");
    const rowB = screen.getByTestId("row-b");
    const imgA = within(rowA).getByTestId("notification-row-co-logo-image");
    const imgB = within(rowB).getByTestId("notification-row-co-logo-image");

    // Fire error only on row A
    fireEvent.error(imgA);

    // Row A falls back
    expect(within(rowA).queryByTestId("notification-row-co-logo-image")).not.toBeInTheDocument();
    expect(within(rowA).getByTestId("notification-row-co-logo")).toBeInTheDocument();

    // Row B is unaffected - still has its image
    expect(within(rowB).getByTestId("notification-row-co-logo-image")).toBeInTheDocument();
  });

  it("UI244-NID-13: row mid-load (image present, no event fired): sibling rows in their own states are unaffected (EC-244-4, EC-244-5)", () => {
    const { container } = render(
      <>
        <div data-testid="row-pending">
          <NotificationIdentity notification={{ company: "Acme Corp", jobTitle: "Pending", companyLogoUrl: "https://cdn.example/acme.png" }} />
        </div>
        <div data-testid="row-resolved">
          <NotificationIdentity notification={{ company: "Foo Inc", jobTitle: "Resolved", companyLogoUrl: null }} />
        </div>
        <div data-testid="row-unresolved">
          <NotificationIdentity notification={{ company: null, jobTitle: null, companyLogoUrl: null }} />
        </div>
      </>
    );

    // Mid-load row: image present but no event fired yet
    expect(within(screen.getByTestId("row-pending")).getByTestId("notification-row-co-logo-image")).toBeInTheDocument();
    // Resolved row (no logo): chip present, no image
    expect(within(screen.getByTestId("row-resolved")).getByTestId("notification-row-co-logo")).toBeInTheDocument();
    expect(within(screen.getByTestId("row-resolved")).queryByTestId("notification-row-co-logo-image")).not.toBeInTheDocument();
    // Unresolved row: fallback icon, fallback label
    expect(within(screen.getByTestId("row-unresolved")).getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(within(screen.getByTestId("row-unresolved")).getByTestId("notification-row-job-title")).toHaveTextContent("Application no longer available");

    // Now fire events on the pending row - sibling rows unaffected
    const pendingImg = within(screen.getByTestId("row-pending")).getByTestId("notification-row-co-logo-image");
    fireEvent.load(pendingImg);

    // Other rows still in their same state
    expect(within(screen.getByTestId("row-resolved")).getByTestId("notification-row-co-logo")).toBeInTheDocument();
    expect(within(screen.getByTestId("row-unresolved")).getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
  });

  it("UI244-NID-14: all four states (S1/S2/S3/S4) coexist on one page without cross-row interference (AC-244-11)", () => {
    const { container } = render(
      <>
        {/* S1: real logo + title */}
        <div data-testid="s1">
          <NotificationIdentity notification={{ company: "Acme Corp", jobTitle: "Engineer", companyLogoUrl: "https://cdn.example/acme.png" }} />
        </div>
        {/* S2: initial chip + title, no logo */}
        <div data-testid="s2">
          <NotificationIdentity notification={{ company: "Foo Inc", jobTitle: "Dev", companyLogoUrl: null }} />
        </div>
        {/* S3: fully unresolved */}
        <div data-testid="s3">
          <NotificationIdentity notification={{ company: null, jobTitle: null, companyLogoUrl: null }} />
        </div>
        {/* S4: gate-fix safety net */}
        <div data-testid="s4">
          <NotificationIdentity notification={{ company: null, jobTitle: "Senior Backend Engineer", companyLogoUrl: null }} />
        </div>
      </>
    );

    // S1: image present, title correct
    const s1 = screen.getByTestId("s1");
    expect(within(s1).getByTestId("notification-row-co-logo-image")).toBeInTheDocument();
    expect(within(s1).getByTestId("notification-row-job-title")).toHaveTextContent("Engineer");

    // S2: initial chip present (no image), title correct
    const s2 = screen.getByTestId("s2");
    expect(within(s2).getByTestId("notification-row-co-logo")).toHaveAttribute("data-co", "Foo Inc");
    expect(within(s2).queryByTestId("notification-row-co-logo-image")).not.toBeInTheDocument();
    expect(within(s2).getByTestId("notification-row-job-title")).toHaveTextContent("Dev");

    // S3: fallback icon, fallback label
    const s3 = screen.getByTestId("s3");
    expect(within(s3).getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(within(s3).getByTestId("notification-row-job-title")).toHaveTextContent("Application no longer available");

    // S4: fallback icon (no company), real title (not fallback label)
    const s4 = screen.getByTestId("s4");
    expect(within(s4).getByTestId("notification-row-fallback-icon")).toBeInTheDocument();
    expect(within(s4).getByTestId("notification-row-job-title")).toHaveTextContent("Senior Backend Engineer");
    expect(within(s4).getByTestId("notification-row-job-title")).not.toHaveTextContent("Application no longer available");
  });
});
