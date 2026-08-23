/**
 * Unit tests for src/components/notificationPresentation.js: card identity resolution.
 * Story #207 / Ticket #216: notification card identity (company + job title).
 * Story #244 / Ticket #260: gate fix (BR-244-1) + companyLogoUrl (BR-244-2).
 *
 * Cases (from docs/specs/207-test-cases.md + docs/test-cases/244-notification-card-title-and-logo-fix-test-cases.md):
 *   resolveCardIdentity(notification) -> { company, jobTitle, resolved, companyLogoUrl }
 *   - resolved company/jobTitle (AC-BELL-1/AC-LIST-1)
 *   - both null/missing (AC-BELL-2/AC-LIST-2, AC-DEGRADE-1)
 *   - company present, jobTitle null (AC-DEGRADE-2)
 *   - the fixed fallback label copy (BR-1)
 *
 * NOTE (story #244): UI244-PRES-01 is an INTENTIONAL breaking change to the old #207
 * assertion that was at lines 48-53 ("jobTitle present, company null: resolved is false").
 * BR-244-1 supersedes that rule: resolved is now TRUE when jobTitle is present, regardless
 * of company. That old test has been updated here per the QAE catalogue's explicit note.
 */
import { describe, it, expect } from "vitest";
import { resolveCardIdentity, FALLBACK_LABEL, categoryOf } from "../../components/notificationPresentation.js";

describe("resolveCardIdentity", () => {
  // ─── Story #207 regression cases (preserved, adjusted where BR-244-1 supersedes) ───

  it("returns resolved=true with company/jobTitle when both are present", () => {
    const result = resolveCardIdentity({ company: "Acme Corp", jobTitle: "Senior Backend Engineer" });
    expect(result).toEqual({
      company: "Acme Corp",
      jobTitle: "Senior Backend Engineer",
      resolved: true,
      companyLogoUrl: null,
    });
  });

  it("returns resolved=false when both company and jobTitle are null", () => {
    const result = resolveCardIdentity({ company: null, jobTitle: null });
    expect(result.resolved).toBe(false);
    expect(result.company).toBeNull();
    expect(result.jobTitle).toBeNull();
  });

  it("returns resolved=false when company/jobTitle keys are absent entirely (older API shape)", () => {
    const result = resolveCardIdentity({ id: "n-1", title: "x" });
    expect(result.resolved).toBe(false);
    expect(result.company).toBeNull();
    expect(result.jobTitle).toBeNull();
  });

  it("never throws when passed null/undefined", () => {
    expect(() => resolveCardIdentity(null)).not.toThrow();
    expect(() => resolveCardIdentity(undefined)).not.toThrow();
    expect(resolveCardIdentity(null).resolved).toBe(false);
  });

  it("company present, jobTitle null: resolved is false (no job title to show) but company is preserved (AC-DEGRADE-2, UI244-PRES-03)", () => {
    const result = resolveCardIdentity({ company: "Acme Corp", jobTitle: null });
    expect(result.resolved).toBe(false);
    expect(result.company).toBe("Acme Corp");
    expect(result.jobTitle).toBeNull();
  });

  it("exposes the fixed fallback label copy (BR-1)", () => {
    expect(FALLBACK_LABEL).toBe("Application no longer available");
  });

  // ─── Story #244 (UI244-PRES-*): gate fix + companyLogoUrl ───

  // UI244-PRES-01: INTENTIONAL breaking change of old #207 assertion.
  // Old: "jobTitle present, company null -> resolved: false"
  // New (BR-244-1): resolved is true when jobTitle is non-null/non-empty, regardless of company.
  it("UI244-PRES-01: jobTitle present, company null: resolved is NOW true (BR-244-1 gate fix; intentional #207 breaking change)", () => {
    const result = resolveCardIdentity({ company: null, jobTitle: "Senior Backend Engineer" });
    expect(result.resolved).toBe(true);
    expect(result.company).toBeNull();
    expect(result.jobTitle).toBe("Senior Backend Engineer");
  });

  // UI244-PRES-02: fully-resolved case is unaffected
  it("UI244-PRES-02: company + jobTitle both present: resolved stays true (regression AC-244-1)", () => {
    const result = resolveCardIdentity({ company: "Acme Corp", jobTitle: "Senior Backend Engineer" });
    expect(result.resolved).toBe(true);
    expect(result.company).toBe("Acme Corp");
    expect(result.jobTitle).toBe("Senior Backend Engineer");
  });

  // UI244-PRES-03: company-only (no title) still not resolved
  it("UI244-PRES-03: company present, jobTitle null: resolved is false (label gated on jobTitle alone, unchanged)", () => {
    const result = resolveCardIdentity({ company: "Acme Corp", jobTitle: null });
    expect(result.resolved).toBe(false);
    expect(result.company).toBe("Acme Corp");
    expect(result.jobTitle).toBeNull();
  });

  // UI244-PRES-04: fully unresolved, unchanged
  it("UI244-PRES-04: both null: resolved is false (AC-244-3)", () => {
    const result = resolveCardIdentity({ company: null, jobTitle: null });
    expect(result.resolved).toBe(false);
    expect(result.company).toBeNull();
    expect(result.jobTitle).toBeNull();
  });

  // UI244-PRES-05: empty-string jobTitle treated as absent
  it("UI244-PRES-05: empty-string jobTitle treated as absent -> resolved false (BR-244-1 non-empty requirement)", () => {
    const result = resolveCardIdentity({ company: null, jobTitle: "" });
    expect(result.resolved).toBe(false);
  });

  // UI244-PRES-06: companyLogoUrl flows through on the return value
  it("UI244-PRES-06: companyLogoUrl on input is exposed on the return value when present (BR-244-2 wiring)", () => {
    const url = "https://cdn.example/acme.png";
    const result = resolveCardIdentity({ company: "Acme Corp", jobTitle: "Engineer", companyLogoUrl: url });
    expect(result.companyLogoUrl).toBe(url);
  });

  it("UI244-PRES-06b: companyLogoUrl is null on return value when absent from input", () => {
    const result = resolveCardIdentity({ company: "Foo Inc", jobTitle: "Dev" });
    expect(result.companyLogoUrl).toBeNull();
  });

  // UI244-PRES-07: empty-string companyLogoUrl normalised to null (EC-244-2)
  it("UI244-PRES-07: empty-string companyLogoUrl normalised to null (EC-244-2)", () => {
    const result = resolveCardIdentity({ company: "Foo Inc", jobTitle: "Dev", companyLogoUrl: "" });
    expect(result.companyLogoUrl).toBeNull();
  });

  // UI244-PRES-08: null/undefined input still never throws, companyLogoUrl degrades to null
  it("UI244-PRES-08: null/undefined input: no throw, resolved false, companyLogoUrl null (null-safety regression)", () => {
    expect(() => resolveCardIdentity(null)).not.toThrow();
    expect(() => resolveCardIdentity(undefined)).not.toThrow();
    expect(resolveCardIdentity(null).resolved).toBe(false);
    expect(resolveCardIdentity(null).companyLogoUrl).toBeNull();
    expect(resolveCardIdentity(undefined).companyLogoUrl).toBeNull();
  });
});

// ─── Story #439 / Ticket #535 (ADR 0031, BR-439-8): categoryOf(notification) ───
// Resolves the response's `category` to its effective value, falling back to
// "ACCOUNT" for anything that isn't exactly one of the three recognised strings
// (unrecognised value, null, absent field, or a null/undefined notification).
// Falling back to "APPLICATION" is explicitly forbidden. Never throws.
describe("categoryOf", () => {
  it("TC-439-22: returns 'APPLICATION' when category is exactly 'APPLICATION'", () => {
    expect(categoryOf({ category: "APPLICATION" })).toBe("APPLICATION");
  });

  it("TC-439-23: returns 'JOB_POST' when category is exactly 'JOB_POST' (recognised passthrough)", () => {
    expect(categoryOf({ category: "JOB_POST" })).toBe("JOB_POST");
  });

  it("TC-439-24: returns 'ACCOUNT' when category is exactly 'ACCOUNT'", () => {
    expect(categoryOf({ category: "ACCOUNT" })).toBe("ACCOUNT");
  });

  it("TC-439-25: an unrecognised category string resolves to 'ACCOUNT', never 'APPLICATION' (BR-439-8)", () => {
    expect(categoryOf({ category: "SOME_FUTURE_VALUE" })).toBe("ACCOUNT");
  });

  it("TC-439-26: a notification with no 'category' key at all resolves to 'ACCOUNT' (EC-439-1)", () => {
    expect(categoryOf({ id: "n-1", type: "INTERVIEW_REMINDER" })).toBe("ACCOUNT");
  });

  it("TC-439-27: category: null resolves to 'ACCOUNT'", () => {
    expect(categoryOf({ category: null })).toBe("ACCOUNT");
  });

  it("TC-439-28: categoryOf(null) and categoryOf(undefined) never throw and both return 'ACCOUNT'", () => {
    expect(() => categoryOf(null)).not.toThrow();
    expect(() => categoryOf(undefined)).not.toThrow();
    expect(categoryOf(null)).toBe("ACCOUNT");
    expect(categoryOf(undefined)).toBe("ACCOUNT");
  });
});
