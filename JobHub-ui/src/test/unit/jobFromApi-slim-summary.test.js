/**
 * Unit tests for src/api/mappers.js: jobFromApi/savedJobFromApi against the slim
 * JobPostSummary list shape (story #330).
 * Cases: TC-15, TC-16, TC-17, TC-18
 */
import { describe, it, expect } from "vitest";
import { jobFromApi, savedJobFromApi } from "../../api/mappers.js";

// A slim JobPostSummary as returned by GET /jobs after story #330: every list-card
// field is present, description/requirements keys are entirely absent (not null).
function slimSummaryDto(overrides = {}) {
  return {
    id: "job-slim-1",
    title: "Backend Engineer",
    url: "https://boards.example.com/backend-engineer",
    location: "Berlin, Germany",
    locations: [{ country: "Germany", city: "Berlin", primary: true }],
    firstSeenAt: "2026-07-15T00:00:00Z",
    lastSeenAt: "2026-07-18T00:00:00Z",
    company: { name: "Acme", logoUrl: "https://example.com/logo.png" },
    compensationMin: 60000,
    compensationMax: 80000,
    language: ["English"],
    employmentType: "full-time",
    careerLevel: "senior",
    source: "Greenhouse",
    ...overrides,
  };
}

// A full JobPostResponse (or SavedJobResponse.job) as returned by GET /jobs/{id}:
// carries the same fields plus description/requirements.
function fullDetailDto(overrides = {}) {
  return {
    ...slimSummaryDto(),
    description: "We are looking for a backend engineer to join our platform team.",
    requirements: ["5+ years backend experience", "Fluent in Java or Kotlin"],
    ...overrides,
  };
}

describe("TC-15 jobFromApi maps every list-card field from a slim JobPostSummary", () => {
  it("builds a correct card view-model with no description/requirements keys present", () => {
    const dto = slimSummaryDto();
    const job = jobFromApi(dto, {});

    expect(job.id).toBe("job-slim-1");
    expect(job.title).toBe("Backend Engineer");
    expect(job.location).toBe("Berlin, Germany");
    expect(job.locations).toEqual([{ country: "Germany", city: "Berlin", primary: true }]);
    expect(job.comp).toBe("€60k–€80k");
    expect(job.type).toBe("Full-time");
    expect(job.postedDays).toBeGreaterThanOrEqual(0);
    expect(job.source).toBe("Greenhouse");
    expect(job.remote).toBe(false);
    expect(job.country).toBe("Germany");
    expect(job.language).toBe("English");
  });
});

describe("TC-16 jobFromApi distinguishes 'not yet loaded' from 'genuinely empty'", () => {
  it("does not throw and does not default desc/reqs to the same value a genuinely-empty full detail would produce", () => {
    const slim = slimSummaryDto();
    expect(() => jobFromApi(slim, {})).not.toThrow();

    const notLoaded = jobFromApi(slim, {});
    const genuinelyEmpty = jobFromApi({ ...slim, description: "", requirements: [] }, {});

    // The two must be distinguishable: "not yet fetched" is not the same value as
    // "fetched and confirmed empty".
    expect(notLoaded.hasFullDetail).toBe(false);
    expect(genuinelyEmpty.hasFullDetail).toBe(true);
    expect({ desc: notLoaded.desc, reqs: notLoaded.reqs }).not.toEqual({
      desc: genuinelyEmpty.desc,
      reqs: genuinelyEmpty.reqs,
    });
  });
});

describe("TC-17 jobFromApi marks a full-detail DTO as already loaded", () => {
  it("reads hasFullDetail=true and carries the real desc/reqs values, including genuine empties", () => {
    const dto = fullDetailDto();
    const job = jobFromApi(dto, {});

    expect(job.hasFullDetail).toBe(true);
    expect(job.desc).toBe(dto.description);
    expect(job.reqs).toEqual(dto.requirements);
  });

  it("carries true empty-state values when description/requirements are genuinely present-and-empty", () => {
    const dto = fullDetailDto({ description: "", requirements: [] });
    const job = jobFromApi(dto, {});

    expect(job.hasFullDetail).toBe(true);
    expect(job.desc).toBe("");
    expect(job.reqs).toEqual([]);
  });
});

describe("TC-18 savedJobFromApi delegates to jobFromApi and stays 'already loaded'", () => {
  it("maps a SavedJobResponse { savedAt, job } to an already-loaded job view-model", () => {
    const job = fullDetailDto();
    const dto = { savedAt: "2026-07-10T00:00:00Z", job };

    const result = savedJobFromApi(dto, {});

    expect(result.id).toBe(job.id);
    expect(result.hasFullDetail).toBe(true);
    expect(result.desc).toBe(job.description);
    expect(result.reqs).toEqual(job.requirements);
  });
});
