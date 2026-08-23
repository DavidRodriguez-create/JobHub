/**
 * Unit tests for src/api/jobs.js and src/api/mappers.js
 * Cases: FE-EMP-01 through FE-EMP-07
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { EMPLOYMENT_TYPE_LABEL, CAREER_LEVEL_LABEL } from "../../api/mappers.js";

// ── Helpers to capture URLSearchParams from searchJobs ──

// We stub the low-level request() to avoid real network calls.
// The module-mock approach: vi.mock hoists to the top so jobs.js
// gets the stub at import time.
vi.mock("../../api/client.js", () => ({
  request: vi.fn(),
}));

// Re-import after mock is registered (vitest handles this via module cache).
const { searchJobs } = await import("../../api/jobs.js");
const { request } = await import("../../api/client.js");
// searchJobs is cache-first (story #329): clear the shared in-memory query cache before
// each test so identical-filter test cases in this file don't observe a cache hit from a
// prior test (peek/cachedFetch is an exact-match Map keyed on the request's own params).
const { clearQueryCache } = await import("../../api/query-cache.js");

beforeEach(() => {
  clearQueryCache();
  request.mockResolvedValue({
    data: { content: [], totalElements: 0, page: 0, totalPages: 0 },
    total: 0,
    status: 200,
  });
});

afterEach(() => {
  vi.clearAllMocks();
});

// Helper: extract the URL string from the first request() call
function capturedUrl() {
  return request.mock.calls[0][0];
}
function capturedParams() {
  const url = capturedUrl();
  const qs = url.split("?")[1] || "";
  return new URLSearchParams(qs);
}

// ── FE-EMP-01: careerLevel appended as repeated params, alongside employmentType ──
describe("FE-EMP-01 searchJobs appends repeated careerLevel params", () => {
  it("sends careerLevel=senior&careerLevel=lead alongside employmentType", async () => {
    await searchJobs({
      employmentType: ["contract"],
      careerLevel: ["senior", "lead"],
    });

    const params = capturedParams();
    expect(params.getAll("careerLevel")).toEqual(["senior", "lead"]);
    expect(params.getAll("employmentType")).toEqual(["contract"]);
  });
});

// ── FE-EMP-02: careerLevel omitted when undefined ──
describe("FE-EMP-02 searchJobs omits careerLevel when undefined", () => {
  it("does not send careerLevel key when not provided", async () => {
    await searchJobs({ keyword: "react" });
    const params = capturedParams();
    expect(params.has("careerLevel")).toBe(false);
  });

  it("does not send careerLevel key when explicitly undefined", async () => {
    await searchJobs({ careerLevel: undefined });
    const params = capturedParams();
    expect(params.has("careerLevel")).toBe(false);
  });
});

// ── FE-EMP-03: employmentType omitted for [] and undefined ──
describe("FE-EMP-03 searchJobs omits employmentType for [] and undefined", () => {
  it("does not send employmentType when given empty array", async () => {
    await searchJobs({ employmentType: [] });
    const params = capturedParams();
    expect(params.has("employmentType")).toBe(false);
  });

  it("does not send employmentType when undefined", async () => {
    await searchJobs({ employmentType: undefined });
    const params = capturedParams();
    expect(params.has("employmentType")).toBe(false);
  });
});

// ── FE-EMP-04: CAREER_LEVEL_LABEL maps all 8 values ──
describe("FE-EMP-04 CAREER_LEVEL_LABEL maps all 8 career level slugs", () => {
  it("exports CAREER_LEVEL_LABEL with all required entries", () => {
    expect(CAREER_LEVEL_LABEL).toBeDefined();
    expect(CAREER_LEVEL_LABEL["internship"]).toBe("Internship");
    expect(CAREER_LEVEL_LABEL["junior"]).toBe("Junior");
    expect(CAREER_LEVEL_LABEL["mid"]).toBe("Mid");
    expect(CAREER_LEVEL_LABEL["senior"]).toBe("Senior");
    expect(CAREER_LEVEL_LABEL["lead"]).toBe("Lead");
    expect(CAREER_LEVEL_LABEL["principal"]).toBe("Principal");
    expect(CAREER_LEVEL_LABEL["manager"]).toBe("Manager");
    expect(CAREER_LEVEL_LABEL["director"]).toBe("Director");
  });

  it("has exactly 8 entries", () => {
    expect(Object.keys(CAREER_LEVEL_LABEL)).toHaveLength(8);
  });
});

// ── FE-EMP-05: EMPLOYMENT_TYPE_LABEL still covers all 5 ──
describe("FE-EMP-05 EMPLOYMENT_TYPE_LABEL regression — still covers all 5 types", () => {
  it("has full-time, part-time, contract, freelance, internship", () => {
    expect(EMPLOYMENT_TYPE_LABEL).toBeDefined();
    expect(EMPLOYMENT_TYPE_LABEL["full-time"]).toBe("Full-time");
    expect(EMPLOYMENT_TYPE_LABEL["part-time"]).toBe("Part-time");
    expect(EMPLOYMENT_TYPE_LABEL["contract"]).toBe("Contract");
    expect(EMPLOYMENT_TYPE_LABEL["freelance"]).toBe("Freelance");
    expect(EMPLOYMENT_TYPE_LABEL["internship"]).toBe("Internship");
  });

  it("has at least 5 entries", () => {
    expect(Object.keys(EMPLOYMENT_TYPE_LABEL).length).toBeGreaterThanOrEqual(5);
  });
});

// ── FE-EMP-06: emp-type option list maps facets.employmentTypes → {value,label,count} ──
describe("FE-EMP-06 emp-type option list maps facets.employmentTypes to {value,label,count}", () => {
  it("maps a facet bucket to the expected shape with humanised label", () => {
    const facetBuckets = [
      { value: "full-time", count: 12 },
      { value: "contract", count: 4 },
    ];
    // The mapping logic (mirrors what JobSearch.jsx does in useMemo):
    const options = facetBuckets.map((f) => ({
      value: f.value,
      label: EMPLOYMENT_TYPE_LABEL[f.value] || f.value,
      count: f.count,
    }));

    expect(options[0]).toEqual({ value: "full-time", label: "Full-time", count: 12 });
    expect(options[1]).toEqual({ value: "contract", label: "Contract", count: 4 });
  });
});

// ── FE-EMP-07: career-level option list maps facets.careerLevels → {value,label,count} ──
describe("FE-EMP-07 career-level option list maps facets.careerLevels to {value,label,count}", () => {
  it("maps a career level facet bucket with humanised label", () => {
    const facetBuckets = [
      { value: "senior", count: 7 },
      { value: "mid", count: 5 },
    ];
    const options = facetBuckets.map((f) => ({
      value: f.value,
      label: CAREER_LEVEL_LABEL[f.value] || f.value,
      count: f.count,
    }));

    expect(options[0]).toEqual({ value: "senior", label: "Senior", count: 7 });
    expect(options[1]).toEqual({ value: "mid", label: "Mid", count: 5 });
  });
});

// ── TC-14: searchJobs/unwrapPage stays a pure pass-through with a slim JobPostSummary content[] ──
describe("TC-14 searchJobs/unwrapPage unwraps a slim JobPostSummary page unchanged", () => {
  it("unwraps content/totalElements/page/totalPages from a slim-summary payload with no field remap", async () => {
    const slimItem = {
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
    };
    expect(slimItem).not.toHaveProperty("description");
    expect(slimItem).not.toHaveProperty("requirements");

    request.mockResolvedValue({
      data: { content: [slimItem], totalElements: 1, page: 0, totalPages: 1 },
      status: 200,
    });

    const result = await searchJobs({});

    expect(result.items).toEqual([slimItem]);
    expect(result.total).toBe(1);
    expect(result.page).toBe(0);
    expect(result.totalPages).toBe(1);
  });
});

// ── TC-331-42/43 (AC-331-19): unwrapPage tolerates the additive countIsEstimate field ──
describe("TC-331-42/43 searchJobs/unwrapPage is inert to the new countIsEstimate field", () => {
  it("TC-331-42 ignores countIsEstimate=true, resolving the same { items, total, page, totalPages } shape", async () => {
    request.mockResolvedValue({
      data: { content: [], totalElements: 5, page: 0, totalPages: 1, countIsEstimate: true },
      status: 200,
    });

    const result = await searchJobs({});

    expect(result).toEqual({ items: [], total: 5, page: 0, totalPages: 1 });
    expect(result).not.toHaveProperty("countIsEstimate");
  });

  it("TC-331-43 resolves the same shape when countIsEstimate is absent entirely (today's payload)", async () => {
    request.mockResolvedValue({
      data: { content: [], totalElements: 5, page: 0, totalPages: 1 },
      status: 200,
    });

    const result = await searchJobs({});

    expect(result).toEqual({ items: [], total: 5, page: 0, totalPages: 1 });
  });
});
