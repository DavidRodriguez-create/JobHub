/**
 * Unit tests for src/api/jobs.js cache integration (searchJobs / getJobFacets adopting
 * query-cache.js). Story #329 / sub-issue #368.
 * Cases: TC-JOBS-01..TC-JOBS-11 (docs/design/329-test-cases.md, Group B)
 *
 * Strategy: mock only ../../api/client.js (request), use the REAL query-cache.js module
 * (not mocked). vi.resetModules() + re-import both jobs.js and query-cache.js in
 * beforeEach so each test starts from an empty cache (same discipline as Group A).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("../../api/client.js", () => ({
  request: vi.fn(),
}));

let jobsApi;
let request;

beforeEach(async () => {
  vi.resetModules();
  vi.clearAllMocks();
  jobsApi = await import("../../api/jobs.js");
  ({ request } = await import("../../api/client.js"));
});

function searchPayload(overrides = {}) {
  return {
    data: { content: [], totalElements: 0, page: 0, totalPages: 1, ...overrides },
    status: 200,
  };
}

function facetsPayload(overrides = {}) {
  return {
    data: {
      companies: [], locations: [], languages: [], employmentTypes: [],
      careerLevels: [], compensationMin: null, compensationMax: null,
      ...overrides,
    },
    status: 200,
  };
}

// ── TC-JOBS-01: searchJobs is cache-first ───────────────────────────────────

describe("TC-JOBS-01: searchJobs is cache-first: identical filters produce one underlying request", () => {
  it("calls request once for two byte-identical searchJobs calls and resolves the same shape", async () => {
    request.mockResolvedValue(searchPayload({ content: [{ id: "j1" }], totalElements: 1 }));
    const filters = { keyword: "react", page: 0, size: 20 };

    const r1 = await jobsApi.searchJobs(filters);
    const r2 = await jobsApi.searchJobs({ ...filters });

    expect(request).toHaveBeenCalledTimes(1);
    expect(r1).toEqual(r2);
    expect(r1).toEqual({ items: [{ id: "j1" }], total: 1, page: 0, totalPages: 1 });
  });
});

// ── TC-JOBS-02: unwrapped page shape unchanged on a miss ───────────────────

describe("TC-JOBS-02: searchJobs still returns the unwrapped page shape unchanged on a miss", () => {
  it("resolves { items, total, page, totalPages } for a fresh combination", async () => {
    request.mockResolvedValue(
      searchPayload({ content: [{ id: "j1" }, { id: "j2" }], totalElements: 2, page: 1, totalPages: 3 })
    );

    const result = await jobsApi.searchJobs({ keyword: "unique-combo", page: 1 });

    expect(result).toEqual({ items: [{ id: "j1" }, { id: "j2" }], total: 2, page: 1, totalPages: 3 });
  });
});

// ── TC-JOBS-03: search vs facets never share a cache entry ─────────────────

describe("TC-JOBS-03: searchJobs and getJobFacets never share a cache entry for equal filters", () => {
  it("calls request at least twice and each resolves its own endpoint's shape", async () => {
    request.mockImplementation((url) => {
      if (url.startsWith("/jobs/facets")) {
        return Promise.resolve(facetsPayload({ companies: [{ value: "Acme", count: 1 }] }));
      }
      return Promise.resolve(searchPayload({ content: [{ id: "j1" }], totalElements: 1 }));
    });

    const sameFilters = { keyword: "engineer" };
    const searchResult = await jobsApi.searchJobs(sameFilters);
    const facetsResult = await jobsApi.getJobFacets(sameFilters);

    expect(request).toHaveBeenCalledTimes(2);
    expect(searchResult).toEqual({ items: [{ id: "j1" }], total: 1, page: 0, totalPages: 1 });
    expect(facetsResult.companies).toEqual([{ value: "Acme", count: 1 }]);
  });
});

// ── TC-JOBS-04: getJobFacets is cache-first ─────────────────────────────────

describe("TC-JOBS-04: getJobFacets is cache-first: identical active filters produce one request", () => {
  it("calls request once for two identical getJobFacets calls", async () => {
    request.mockResolvedValue(facetsPayload({ companies: [{ value: "Acme", count: 3 }] }));
    const filters = { location: ["Spain"] };

    const r1 = await jobsApi.getJobFacets(filters);
    const r2 = await jobsApi.getJobFacets({ ...filters });

    expect(request).toHaveBeenCalledTimes(1);
    expect(r1).toEqual(r2);
  });
});

// ── TC-JOBS-05: reordered multi-select values are the same key ─────────────

describe("TC-JOBS-05: searchJobs treats reordered multi-select values as the same key", () => {
  it("calls request once total for two opposite location orderings", async () => {
    request.mockResolvedValue(searchPayload({ content: [{ id: "j8" }], totalElements: 1 }));
    const base = { keyword: "backend" };

    await jobsApi.searchJobs({ ...base, location: ["Spain", "Remote"] });
    await jobsApi.searchJobs({ ...base, location: ["Remote", "Spain"] });

    expect(request).toHaveBeenCalledTimes(1);
  });
});

// ── TC-JOBS-06: peekSearch mirrors searchJobs' cache-hit/miss state ────────

describe("TC-JOBS-06: peekSearch mirrors the exact cache-hit/miss state of searchJobs", () => {
  it("returns undefined before searchJobs resolves, then the same shape after, with no extra request", async () => {
    request.mockResolvedValue(searchPayload({ content: [{ id: "j1" }], totalElements: 1 }));
    const filters = { keyword: "peek-me" };

    expect(jobsApi.peekSearch(filters)).toBeUndefined();

    const resolved = await jobsApi.searchJobs(filters);
    expect(jobsApi.peekSearch(filters)).toEqual(resolved);
    expect(request).toHaveBeenCalledTimes(1);
  });
});

// ── TC-JOBS-07: prefetchSearch warms the exact key searchJobs would use ────

describe("TC-JOBS-07: prefetchSearch warms the exact key searchJobs would use for that page", () => {
  it("a subsequent searchJobs call for the same page does not call request again", async () => {
    request.mockResolvedValue(searchPayload({ content: [{ id: "j2" }], totalElements: 1, page: 2 }));
    const filters = { keyword: "warm", page: 2 };

    jobsApi.prefetchSearch(filters);
    await new Promise((r) => setTimeout(r, 0));

    const result = await jobsApi.searchJobs(filters);

    expect(request).toHaveBeenCalledTimes(1);
    expect(result.page).toBe(2);
  });
});

// ── TC-JOBS-08: prefetchSearch swallows a request rejection ────────────────

describe("TC-JOBS-08: prefetchSearch swallows a request rejection", () => {
  it("does not throw and a subsequent searchJobs performs a genuine new request", async () => {
    request.mockRejectedValueOnce(new Error("network down"));
    const filters = { keyword: "flaky" };

    expect(() => jobsApi.prefetchSearch(filters)).not.toThrow();
    await new Promise((r) => setTimeout(r, 0));

    request.mockResolvedValueOnce(searchPayload({ content: [{ id: "j3" }], totalElements: 1 }));
    const result = await jobsApi.searchJobs(filters);

    expect(request).toHaveBeenCalledTimes(2);
    expect(result.items).toEqual([{ id: "j3" }]);
  });
});

// ── TC-JOBS-09: a failed searchJobs is not cached ───────────────────────────

describe("TC-JOBS-09: a failed searchJobs is not cached; the identical retry re-fetches for real", () => {
  it("peekSearch is undefined after the failure, and the retry calls request again", async () => {
    request.mockRejectedValueOnce(new Error("HTTP 500"));
    const filters = { keyword: "will-fail" };

    await expect(jobsApi.searchJobs(filters)).rejects.toThrow("HTTP 500");
    expect(jobsApi.peekSearch(filters)).toBeUndefined();

    request.mockResolvedValueOnce(searchPayload({ content: [{ id: "j4" }], totalElements: 1 }));
    const result = await jobsApi.searchJobs(filters);

    expect(request).toHaveBeenCalledTimes(2);
    expect(result.items).toEqual([{ id: "j4" }]);
  });
});

// ── TC-JOBS-10: peekFacets mirrors getJobFacets' cache-hit/miss state ──────

describe("TC-JOBS-10: peekFacets mirrors the exact cache-hit/miss state of getJobFacets", () => {
  it("returns undefined before getJobFacets resolves, then the resolved object after", async () => {
    request.mockResolvedValue(facetsPayload({ companies: [{ value: "Beta", count: 2 }] }));
    const filters = { keyword: "peek-facets" };

    expect(jobsApi.peekFacets(filters)).toBeUndefined();

    const resolved = await jobsApi.getJobFacets(filters);
    expect(jobsApi.peekFacets(filters)).toEqual(resolved);
    expect(request).toHaveBeenCalledTimes(1);
  });
});

// ── TC-JOBS-11: a failed getJobFacets is not cached ─────────────────────────

describe("TC-JOBS-11: a failed getJobFacets is not cached; the identical retry re-fetches", () => {
  it("the second call triggers a second request to the facets endpoint", async () => {
    request.mockRejectedValueOnce(new Error("HTTP 500"));
    const filters = { keyword: "facets-fail" };

    await expect(jobsApi.getJobFacets(filters)).rejects.toThrow("HTTP 500");

    request.mockResolvedValueOnce(facetsPayload({ companies: [{ value: "Gamma", count: 5 }] }));
    const result = await jobsApi.getJobFacets(filters);

    expect(request).toHaveBeenCalledTimes(2);
    expect(result.companies).toEqual([{ value: "Gamma", count: 5 }]);
  });
});
