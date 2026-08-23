/**
 * Unit tests for src/api/mappers.js: the frozen CompanyInfo mapping (story #428,
 * ADR 0023). Cases: QAE-428-FE-01..06.
 *
 * Stops synthesising "-" placeholders, stops backfilling headquarters from the
 * job's own location, prefers the backend slug/id over the local truncating
 * derivation, and merges the companies map upgrade-never-downgrade.
 */
import { describe, it, expect } from "vitest";
import { jobFromApi } from "../../api/mappers.js";

function baseDto(overrides = {}) {
  return {
    id: "job-co-1",
    title: "Platform Engineer",
    url: "https://boards.example.com/platform-engineer",
    location: "Berlin, Germany",
    firstSeenAt: "2026-07-15T00:00:00Z",
    ...overrides,
  };
}

describe("QAE-428-FE-01: jobFromApi stops synthesising a placeholder for industry/size", () => {
  it("does not set industry/size to the long-dash placeholder when they are null", () => {
    const companies = {};
    const dto = baseDto({ company: { name: "Acme", logoUrl: null, industry: null, size: null } });

    const job = jobFromApi(dto, companies);
    const entry = companies[job.co];

    // U+2014 (the long-dash placeholder used before this story), via escape so
    // this file never contains the literal character.
    const longDash = String.fromCharCode(0x2014);
    expect(entry.industry).not.toBe(longDash);
    expect(entry.size).not.toBe(longDash);
    expect(entry.industry == null).toBe(true);
    expect(entry.size == null).toBe(true);
  });
});

describe("QAE-428-FE-02: jobFromApi stops backfilling headquarters from the job's own location", () => {
  it("does not fall back to the posting's location when headquarters is null", () => {
    const companies = {};
    const dto = baseDto({
      location: "Berlin, Germany",
      company: { name: "Acme", headquarters: null },
    });

    const job = jobFromApi(dto, companies);
    const entry = companies[job.co];

    expect(entry.hq).not.toBe("Berlin, Germany");
    expect(entry.hq == null).toBe(true);
  });

  it("shows the populated headquarters value, never the job's own location", () => {
    const companies = {};
    const dto = baseDto({
      location: "Berlin, Germany",
      company: { name: "Acme", headquarters: "Zurich, Switzerland" },
    });

    const job = jobFromApi(dto, companies);
    const entry = companies[job.co];

    expect(entry.hq).toBe("Zurich, Switzerland");
    expect(entry.hq).not.toBe(dto.location);
  });
});

describe("QAE-428-FE-03: present fields still render/populate correctly (non-omission control)", () => {
  it("carries every populated CompanyInfo field into the companies-map entry", () => {
    const companies = {};
    const dto = baseDto({
      company: {
        id: "c-1",
        slug: "acme",
        name: "Acme",
        logoUrl: "https://example.com/logo.png",
        website: "https://acme.example.com",
        industry: "Fintech",
        size: "51-200",
        headquarters: "Zurich, Switzerland",
        description: "We build financial infrastructure.",
        tags: ["b2b", "remote-first"],
        manuallyEdited: false,
        updatedAt: "2026-06-01T00:00:00Z",
      },
    });

    const job = jobFromApi(dto, companies);
    const entry = companies[job.co];

    expect(entry.name).toBe("Acme");
    expect(entry.logoUrl).toBe("https://example.com/logo.png");
    expect(entry.website).toBe("https://acme.example.com");
    expect(entry.industry).toBe("Fintech");
    expect(entry.size).toBe("51-200");
    expect(entry.hq).toBe("Zurich, Switzerland");
    expect(entry.description).toBe("We build financial infrastructure.");
    expect(entry.tags).toEqual(["b2b", "remote-first"]);
    expect(entry.id).toBe("c-1");
    expect(entry.slug).toBe("acme");
    expect(entry.manuallyEdited).toBe(false);
    expect(entry.updatedAt).toBe("2026-06-01T00:00:00Z");
  });
});

describe("QAE-428-FE-04: prefers dto.company.slug over the local truncating slug()", () => {
  it("uses company.slug exactly, untruncated, when present", () => {
    const companies = {};
    const longSlug = "the-official-backend-company-slug-value";
    const dto = baseDto({ company: { name: "Acme", slug: longSlug } });

    const job = jobFromApi(dto, companies);

    expect(job.co).toBe(longSlug);
    expect(companies[longSlug]).toBeDefined();
  });

  it("falls back to the local slug() derivation when company.slug is absent (AC-428-13)", () => {
    const companies = {};
    const dto = baseDto({ company: { name: "A Very Long Company Name Indeed" } });

    const job = jobFromApi(dto, companies);

    // Local slug(): lowercase, strip non-alphanumerics, truncate to 16 chars.
    expect(job.co).toBe("averylongcompany");
    expect(job.co.length).toBeLessThanOrEqual(16);
  });
});

describe("QAE-428-FE-05: upgrade, a later rich fetch replaces an earlier sparse one (AC-428-25)", () => {
  it("never drops the richer fetch in favour of the already-registered sparse entry", () => {
    const companies = {};
    const sparseDto = baseDto({ company: { name: "Acme", slug: "acme", logoUrl: "https://example.com/logo.png" } });
    jobFromApi(sparseDto, companies);

    expect(companies.acme.industry == null).toBe(true);

    const richDto = baseDto({
      id: "job-co-2",
      company: {
        name: "Acme",
        slug: "acme",
        industry: "Fintech",
        size: "51-200",
        headquarters: "Zurich, Switzerland",
        description: "Rich detail fetch.",
      },
    });
    jobFromApi(richDto, companies);

    expect(companies.acme.industry).toBe("Fintech");
    expect(companies.acme.size).toBe("51-200");
    expect(companies.acme.hq).toBe("Zurich, Switzerland");
    expect(companies.acme.description).toBe("Rich detail fetch.");
    // the sparse call's logoUrl must still be present, never wiped by the richer merge
    expect(companies.acme.logoUrl).toBe("https://example.com/logo.png");
  });
});

describe("QAE-428-FE-06: never downgrade, a later sparse fetch does not erase known-rich fields (AC-428-26)", () => {
  it("keeps the previously-known rich description when a later list projection sends null", () => {
    const companies = {};
    const richDto = baseDto({
      company: {
        name: "Acme",
        slug: "acme",
        description: "Rich detail already known.",
        industry: "Fintech",
      },
    });
    jobFromApi(richDto, companies);
    expect(companies.acme.description).toBe("Rich detail already known.");

    // A subsequent GET /jobs list entry for the same company: description is null
    // per the projection rule (AC-428-16), industry still carried.
    const sparseListDto = baseDto({
      id: "job-co-3",
      company: { name: "Acme", slug: "acme", description: null, industry: "Fintech" },
    });
    jobFromApi(sparseListDto, companies);

    expect(companies.acme.description).toBe("Rich detail already known.");
  });
});
