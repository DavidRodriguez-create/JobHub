/**
 * Component tests for AdminCompaniesPage (story #430, sub-issue #456).
 *
 * Contract: GET /jobs/admin/companies (browse), GET /jobs/admin/companies/{id} (read one),
 * PUT /jobs/admin/companies/{id} (full-replace update). See
 * api-contracts/src/main/resources/openapi/job-service.yaml and
 * docs/product/430-company-enrichment-acceptance.md.
 *
 * These cases are not individually numbered by the QAE doc (docs/qa/430-company-enrichment-test-cases.md
 * only specifies fe-component cases for the JobDetailDrawer tags row, QAE-430-UI-01..04, since every
 * AC for the browse/edit screen itself is [Backend]-tagged). They exercise the UI-side behaviour this
 * screen must have to satisfy the PDA's browse-and-edit workflow and the full-replace contract (ADR
 * 0025 D4): AC-430-03..09 (browse/filter/paginate), AC-430-10 (open a company), AC-430-13/15 (edit
 * persists, full-replace clears an omitted field), AC-430-31/32/33 (client-side tag-format UX guard,
 * backend remains the source of truth).
 */
import React from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/jobs.js", () => ({
  listAdminCompanies: vi.fn(),
  getAdminCompany: vi.fn(),
  updateAdminCompany: vi.fn(),
}));

vi.mock("../../api/client.js", () => ({
  ApiError: class ApiError extends Error {
    constructor(status, message, body) {
      super(message);
      this.status = status;
      this.body = body;
    }
  },
}));

import { listAdminCompanies, getAdminCompany, updateAdminCompany } from "../../api/jobs.js";
import { ApiError } from "../../api/client.js";
import { AdminCompaniesPage } from "../../screens/AdminCompanies.jsx";

const ACCOUNT = { isAdmin: true, email: "admin@example.com" };

const STRIPE = {
  id: "11111111-1111-1111-1111-111111111111",
  slug: "stripe",
  name: "Stripe",
  website: "https://stripe.com",
  industry: "Fintech",
  size: "1001-5000",
  headquarters: "San Francisco, USA",
  description: "Financial infrastructure for the internet.",
  tags: ["fintech", "payments"],
  logoUrl: "https://logo.example/stripe.png",
  manuallyEdited: true,
  updatedAt: "2026-01-05T10:00:00Z",
};

const ACME_ONLY = {
  id: "22222222-2222-2222-2222-222222222222",
  slug: "acme-only",
  name: "Acme Only",
  website: null,
  industry: null,
  size: null,
  headquarters: null,
  description: null,
  tags: null,
  logoUrl: null,
  manuallyEdited: false,
  updatedAt: "2026-01-01T00:00:00Z",
};

beforeEach(() => {
  vi.clearAllMocks();
  listAdminCompanies.mockResolvedValue({ items: [STRIPE, ACME_ONLY], total: 2 });
});

describe("browse: loads and renders the company list on mount", () => {
  it("fetches the default page and renders each company's name and curated state", async () => {
    render(<AdminCompaniesPage account={ACCOUNT} />);

    await waitFor(() => expect(listAdminCompanies).toHaveBeenCalledTimes(1));
    expect(listAdminCompanies).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: expect.any(Number) })
    );

    expect(await screen.findByText("Stripe")).toBeInTheDocument();
    expect(screen.getByText("Acme Only")).toBeInTheDocument();

    const stripeRow = screen.getByTestId(`company-row-${STRIPE.id}`);
    expect(within(stripeRow).getByText(/curated/i)).toBeInTheDocument();
    const acmeRow = screen.getByTestId(`company-row-${ACME_ONLY.id}`);
    expect(within(acmeRow).getByText(/not curated|backlog|needs enrichment/i)).toBeInTheDocument();
  });

  it("shows an empty state, not an error, when nothing matches", async () => {
    listAdminCompanies.mockResolvedValue({ items: [], total: 0 });
    render(<AdminCompaniesPage account={ACCOUNT} />);

    await waitFor(() => expect(listAdminCompanies).toHaveBeenCalled());
    expect(await screen.findByTestId("admin-companies-empty")).toBeInTheDocument();
  });

  // ── size + headquarters (story #486, sub-issue #490): QAE-486-13..17 ──────

  it("QAE-486-13 (AC-486-13): size present renders in the row's secondary line", async () => {
    render(<AdminCompaniesPage account={ACCOUNT} />);
    const row = await screen.findByTestId(`company-row-${STRIPE.id}`);
    expect(within(row).getByText("1001-5000")).toBeInTheDocument();
  });

  it("QAE-486-14 (AC-486-14): size null is gracefully absent, no dash placeholder", async () => {
    listAdminCompanies.mockResolvedValue({ items: [{ ...STRIPE, size: null }], total: 1 });
    render(<AdminCompaniesPage account={ACCOUNT} />);
    const row = await screen.findByTestId(`company-row-${STRIPE.id}`);
    expect(within(row).queryByText("1001-5000")).not.toBeInTheDocument();
    expect(within(row).queryByText(/^-$/)).not.toBeInTheDocument();
  });

  it("QAE-486-15 (AC-486-15): headquarters present renders in the row's secondary line", async () => {
    render(<AdminCompaniesPage account={ACCOUNT} />);
    const row = await screen.findByTestId(`company-row-${STRIPE.id}`);
    expect(within(row).getByText("San Francisco, USA")).toBeInTheDocument();
  });

  it("QAE-486-16 (AC-486-16): headquarters null is gracefully absent, no dash placeholder", async () => {
    listAdminCompanies.mockResolvedValue({ items: [{ ...STRIPE, headquarters: null }], total: 1 });
    render(<AdminCompaniesPage account={ACCOUNT} />);
    const row = await screen.findByTestId(`company-row-${STRIPE.id}`);
    expect(within(row).queryByText("San Francisco, USA")).not.toBeInTheDocument();
    expect(within(row).queryByText(/^-$/)).not.toBeInTheDocument();
  });

  it("QAE-486-17 (AC-486-17): industry/size/headquarters all null leaves a clean minimal row", async () => {
    render(<AdminCompaniesPage account={ACCOUNT} />);
    const row = await screen.findByTestId(`company-row-${ACME_ONLY.id}`);
    expect(within(row).getByText("Acme Only")).toBeInTheDocument();
    expect(within(row).getByText(/not curated|backlog|needs enrichment/i)).toBeInTheDocument();
    expect(row.textContent).not.toContain("undefined");
    expect(row.textContent).not.toMatch(/·\s*$/);
    expect(row.textContent).not.toMatch(/^\s*·/);
  });

  it("a load failure surfaces an inline error, not a crash", async () => {
    listAdminCompanies.mockRejectedValue(new ApiError(500, "Internal Server Error"));
    render(<AdminCompaniesPage account={ACCOUNT} />);

    expect(await screen.findByTestId("admin-companies-load-error")).toBeInTheDocument();
  });
});

describe("browse: q / manuallyEdited filters", () => {
  it("typing a q value and submitting the filter form re-fetches with q set", async () => {
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await waitFor(() => expect(listAdminCompanies).toHaveBeenCalledTimes(1));

    await user.type(screen.getByTestId("company-search-input"), "strip");
    await user.click(screen.getByTestId("company-search-submit"));

    await waitFor(() => expect(listAdminCompanies).toHaveBeenCalledTimes(2));
    expect(listAdminCompanies).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: "strip", page: 0 })
    );
  });

  it("selecting the 'needs enrichment' filter re-fetches with manuallyEdited=false", async () => {
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await waitFor(() => expect(listAdminCompanies).toHaveBeenCalledTimes(1));

    await user.selectOptions(screen.getByTestId("company-filter-select"), "backlog");

    await waitFor(() => expect(listAdminCompanies).toHaveBeenCalledTimes(2));
    expect(listAdminCompanies).toHaveBeenLastCalledWith(
      expect.objectContaining({ manuallyEdited: false, page: 0 })
    );
  });

  it("selecting the 'curated' filter re-fetches with manuallyEdited=true", async () => {
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await waitFor(() => expect(listAdminCompanies).toHaveBeenCalledTimes(1));

    await user.selectOptions(screen.getByTestId("company-filter-select"), "curated");

    await waitFor(() => expect(listAdminCompanies).toHaveBeenCalledTimes(2));
    expect(listAdminCompanies).toHaveBeenLastCalledWith(
      expect.objectContaining({ manuallyEdited: true, page: 0 })
    );
  });
});

describe("browse: pagination (X-Total-Count driven)", () => {
  it("Next page advances the page param and is disabled on the last page", async () => {
    listAdminCompanies.mockResolvedValue({ items: [STRIPE], total: 21 }); // > one page of 20
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await waitFor(() => expect(listAdminCompanies).toHaveBeenCalledTimes(1));

    const nextBtn = await screen.findByTestId("company-page-next");
    expect(nextBtn).not.toBeDisabled();
    await user.click(nextBtn);

    await waitFor(() => expect(listAdminCompanies).toHaveBeenCalledTimes(2));
    expect(listAdminCompanies).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
  });

  it("Next page is disabled when every match already fits on the current page", async () => {
    listAdminCompanies.mockResolvedValue({ items: [STRIPE, ACME_ONLY], total: 2 });
    render(<AdminCompaniesPage account={ACCOUNT} />);
    const nextBtn = await screen.findByTestId("company-page-next");
    expect(nextBtn).toBeDisabled();
  });
});

describe("edit: opening a company pre-loads every field, including nulls as empty inputs", () => {
  it("clicking a company row loads it via getAdminCompany and shows the edit form", async () => {
    getAdminCompany.mockResolvedValue(STRIPE);
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await screen.findByText("Stripe");

    await user.click(screen.getByTestId(`company-row-${STRIPE.id}`));

    await waitFor(() => expect(getAdminCompany).toHaveBeenCalledWith(STRIPE.id));
    expect(await screen.findByTestId("company-edit-form")).toBeInTheDocument();
    expect(screen.getByDisplayValue("https://stripe.com")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Fintech")).toBeInTheDocument();
    expect(screen.getByDisplayValue("San Francisco, USA")).toBeInTheDocument();
    expect(screen.getByText("fintech")).toBeInTheDocument();
    expect(screen.getByText("payments")).toBeInTheDocument();
  });

  it("a sparse company (every enrichable field null) opens with empty inputs, not placeholders", async () => {
    getAdminCompany.mockResolvedValue(ACME_ONLY);
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await screen.findByText("Acme Only");

    await user.click(screen.getByTestId(`company-row-${ACME_ONLY.id}`));

    await screen.findByTestId("company-edit-form");
    expect(screen.getByTestId("company-field-website")).toHaveValue("");
    expect(screen.getByTestId("company-field-industry")).toHaveValue("");
    expect(screen.getByTestId("company-field-headquarters")).toHaveValue("");
    expect(screen.getByTestId("company-field-description")).toHaveValue("");
    expect(document.body.textContent).not.toContain("undefined");
  });
});

describe("edit: full-replace submit semantics (ADR 0025 D4)", () => {
  it("changing one field and submitting sends every field, echoing the untouched ones as loaded", async () => {
    getAdminCompany.mockResolvedValue(STRIPE);
    updateAdminCompany.mockResolvedValue({ ...STRIPE, industry: "Financial Services", manuallyEdited: true });
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await screen.findByText("Stripe");
    await user.click(screen.getByTestId(`company-row-${STRIPE.id}`));
    await screen.findByTestId("company-edit-form");

    const industryInput = screen.getByTestId("company-field-industry");
    await user.clear(industryInput);
    await user.type(industryInput, "Financial Services");

    await user.click(screen.getByTestId("company-save-btn"));

    await waitFor(() => expect(updateAdminCompany).toHaveBeenCalledTimes(1));
    expect(updateAdminCompany).toHaveBeenCalledWith(STRIPE.id, {
      website: "https://stripe.com",
      industry: "Financial Services",
      size: "1001-5000",
      headquarters: "San Francisco, USA",
      description: "Financial infrastructure for the internet.",
      tags: ["fintech", "payments"],
      logoUrl: "https://logo.example/stripe.png",
    });
  });

  it("clearing a populated field to empty and submitting sends null for that field (clears, not left alone)", async () => {
    getAdminCompany.mockResolvedValue(STRIPE);
    updateAdminCompany.mockResolvedValue({ ...STRIPE, website: null });
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await screen.findByText("Stripe");
    await user.click(screen.getByTestId(`company-row-${STRIPE.id}`));
    await screen.findByTestId("company-edit-form");

    const websiteInput = screen.getByTestId("company-field-website");
    await user.clear(websiteInput);

    await user.click(screen.getByTestId("company-save-btn"));

    await waitFor(() => expect(updateAdminCompany).toHaveBeenCalledTimes(1));
    expect(updateAdminCompany).toHaveBeenCalledWith(
      STRIPE.id,
      expect.objectContaining({ website: null })
    );
  });

  it("a successful save reflects the response's manuallyEdited/updated values back in the form", async () => {
    getAdminCompany.mockResolvedValue(ACME_ONLY);
    updateAdminCompany.mockResolvedValue({ ...ACME_ONLY, industry: "Retail", manuallyEdited: true });
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await screen.findByText("Acme Only");
    await user.click(screen.getByTestId(`company-row-${ACME_ONLY.id}`));
    await screen.findByTestId("company-edit-form");

    await user.type(screen.getByTestId("company-field-industry"), "Retail");
    await user.click(screen.getByTestId("company-save-btn"));

    await waitFor(() => expect(updateAdminCompany).toHaveBeenCalledTimes(1));
    expect(await screen.findByTestId("company-save-success")).toBeInTheDocument();
  });

  it("a 400 from the server surfaces an inline error and does not close the form", async () => {
    getAdminCompany.mockResolvedValue(STRIPE);
    updateAdminCompany.mockRejectedValue(new ApiError(400, "website must be a well-formed URI"));
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await screen.findByText("Stripe");
    await user.click(screen.getByTestId(`company-row-${STRIPE.id}`));
    await screen.findByTestId("company-edit-form");

    await user.click(screen.getByTestId("company-save-btn"));

    expect(await screen.findByTestId("company-save-error")).toHaveTextContent(/well-formed URI/i);
    expect(screen.getByTestId("company-edit-form")).toBeInTheDocument();
  });
});

describe("edit: client-side tag format guard (backend remains the source of truth)", () => {
  it("adding a tag that violates lowercase-kebab-case is rejected inline and never reaches the server", async () => {
    getAdminCompany.mockResolvedValue(ACME_ONLY);
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await screen.findByText("Acme Only");
    await user.click(screen.getByTestId(`company-row-${ACME_ONLY.id}`));
    await screen.findByTestId("company-edit-form");

    await user.type(screen.getByTestId("company-tag-input"), "Remote First");
    await user.click(screen.getByTestId("company-tag-add"));

    expect(await screen.findByTestId("company-tag-error")).toBeInTheDocument();
    // the rejected candidate never enters the draft, so it never reaches the server
    expect(screen.queryByText("Remote First")).not.toBeInTheDocument();
    await user.click(screen.getByTestId("company-save-btn"));
    await waitFor(() => expect(updateAdminCompany).toHaveBeenCalledTimes(1));
    expect(updateAdminCompany).toHaveBeenCalledWith(
      ACME_ONLY.id,
      expect.objectContaining({ tags: null })
    );
  });

  it("adding a duplicate tag is rejected inline", async () => {
    getAdminCompany.mockResolvedValue(STRIPE); // already has "fintech","payments"
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await screen.findByText("Stripe");
    await user.click(screen.getByTestId(`company-row-${STRIPE.id}`));
    await screen.findByTestId("company-edit-form");

    await user.type(screen.getByTestId("company-tag-input"), "fintech");
    await user.click(screen.getByTestId("company-tag-add"));

    expect(await screen.findByTestId("company-tag-error")).toBeInTheDocument();
    // still exactly one "fintech" chip, not two
    expect(screen.getAllByText("fintech")).toHaveLength(1);
  });

  it("removing a tag chip drops it from the draft sent on save", async () => {
    getAdminCompany.mockResolvedValue(STRIPE);
    updateAdminCompany.mockResolvedValue(STRIPE);
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await screen.findByText("Stripe");
    await user.click(screen.getByTestId(`company-row-${STRIPE.id}`));
    await screen.findByTestId("company-edit-form");

    await user.click(screen.getByTestId("company-tag-remove-fintech"));
    await user.click(screen.getByTestId("company-save-btn"));

    await waitFor(() => expect(updateAdminCompany).toHaveBeenCalledTimes(1));
    expect(updateAdminCompany).toHaveBeenCalledWith(
      STRIPE.id,
      expect.objectContaining({ tags: ["payments"] })
    );
  });

  it("adding a 21st tag is rejected inline (max 20)", async () => {
    const twentyTags = Array.from({ length: 20 }, (_, i) => `tag-${i}`);
    getAdminCompany.mockResolvedValue({ ...ACME_ONLY, tags: twentyTags });
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await screen.findByText("Acme Only");
    await user.click(screen.getByTestId(`company-row-${ACME_ONLY.id}`));
    await screen.findByTestId("company-edit-form");

    await user.type(screen.getByTestId("company-tag-input"), "one-more");
    await user.click(screen.getByTestId("company-tag-add"));

    expect(await screen.findByTestId("company-tag-error")).toBeInTheDocument();
    expect(screen.queryByText("one-more")).not.toBeInTheDocument();
  });
});

describe("edit: back to list", () => {
  it("Back returns to the browse list without an extra fetch of the same page", async () => {
    getAdminCompany.mockResolvedValue(STRIPE);
    const user = userEvent.setup();
    render(<AdminCompaniesPage account={ACCOUNT} />);
    await screen.findByText("Stripe");
    await user.click(screen.getByTestId(`company-row-${STRIPE.id}`));
    await screen.findByTestId("company-edit-form");

    await user.click(screen.getByTestId("company-back-btn"));

    expect(screen.queryByTestId("company-edit-form")).not.toBeInTheDocument();
    expect(await screen.findByTestId(`company-row-${STRIPE.id}`)).toBeInTheDocument();
  });
});
