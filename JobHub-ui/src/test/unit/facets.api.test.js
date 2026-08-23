/**
 * Unit tests for getJobFacets(filters) — Story #4 reactive facets
 * Cases: FE-F01, FE-F02
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// Stub the low-level request() so no real network calls are made.
vi.mock("../../api/client.js", () => ({
  request: vi.fn(),
}));

const { getJobFacets } = await import("../../api/jobs.js");
const { request } = await import("../../api/client.js");
// getJobFacets is cache-first (story #329): clear the shared in-memory query cache before
// each test so identical-filter test cases in this file don't observe a cache hit from a
// prior test (peek/cachedFetch is an exact-match Map keyed on the request's own params).
const { clearQueryCache } = await import("../../api/query-cache.js");

beforeEach(() => {
  clearQueryCache();
  request.mockResolvedValue({
    data: {
      companies: [],
      locations: [],
      languages: [],
      employmentTypes: [],
      careerLevels: [],
      compensationMin: null,
      compensationMax: null,
    },
    status: 200,
  });
});

afterEach(() => {
  vi.clearAllMocks();
});

// Helper: parse the URL sent to request()
function capturedParams() {
  const url = request.mock.calls[0][0];
  const qs = url.split("?")[1] || "";
  return new URLSearchParams(qs);
}

function capturedUrl() {
  return request.mock.calls[0][0];
}

// ── FE-F01: getJobFacets(filters) sends active filters as repeated params,
//           plus scalar keyword/comp/postedWithin, and OMITS sort/page/size ──
describe("FE-F01 getJobFacets appends active filters and omits sort/page/size", () => {
  it("sends repeated location[], language[], company[], employmentType[], careerLevel[]", async () => {
    await getJobFacets({
      location: ["Spain", "Remote"],
      language: ["English"],
      company: ["Acme Corp", "Beta Ltd"],
      employmentType: ["full-time", "contract"],
      careerLevel: ["senior", "lead"],
    });

    const params = capturedParams();
    expect(params.getAll("location[]")).toEqual([]);
    // The contract uses explode:true, so params are named "location" not "location[]"
    expect(params.getAll("location")).toEqual(["Spain", "Remote"]);
    expect(params.getAll("language")).toEqual(["English"]);
    expect(params.getAll("company")).toEqual(["Acme Corp", "Beta Ltd"]);
    expect(params.getAll("employmentType")).toEqual(["full-time", "contract"]);
    expect(params.getAll("careerLevel")).toEqual(["senior", "lead"]);
  });

  it("sends scalar keyword, compensationMin, compensationMax, postedWithin", async () => {
    await getJobFacets({
      keyword: "react",
      compensationMin: 50000,
      compensationMax: 120000,
      postedWithin: "week",
    });

    const params = capturedParams();
    expect(params.get("keyword")).toBe("react");
    expect(params.get("compensationMin")).toBe("50000");
    expect(params.get("compensationMax")).toBe("120000");
    expect(params.get("postedWithin")).toBe("week");
  });

  it("does NOT send sort, page, or size", async () => {
    await getJobFacets({
      keyword: "engineer",
      location: ["France"],
      sort: "newest",
      page: 2,
      size: 50,
    });

    const params = capturedParams();
    expect(params.has("sort")).toBe(false);
    expect(params.has("page")).toBe(false);
    expect(params.has("size")).toBe(false);
  });

  it("calls /jobs/facets endpoint", async () => {
    await getJobFacets({ keyword: "test" });
    expect(capturedUrl()).toMatch(/^\/jobs\/facets/);
  });
});

// ── FE-F02: empty/undefined dimensions are omitted ──
describe("FE-F02 getJobFacets omits empty or undefined filter dimensions", () => {
  it("omits location when array is empty", async () => {
    await getJobFacets({ location: [] });
    const params = capturedParams();
    expect(params.has("location")).toBe(false);
  });

  it("omits language when undefined", async () => {
    await getJobFacets({ language: undefined });
    const params = capturedParams();
    expect(params.has("language")).toBe(false);
  });

  it("omits company when not provided", async () => {
    await getJobFacets({});
    const params = capturedParams();
    expect(params.has("company")).toBe(false);
  });

  it("omits keyword when not provided", async () => {
    await getJobFacets({});
    const params = capturedParams();
    expect(params.has("keyword")).toBe(false);
  });

  it("omits compensationMin when not provided", async () => {
    await getJobFacets({});
    const params = capturedParams();
    expect(params.has("compensationMin")).toBe(false);
  });

  it("omits postedWithin when not provided", async () => {
    await getJobFacets({});
    const params = capturedParams();
    expect(params.has("postedWithin")).toBe(false);
  });

  it("produces empty query string when called with no args", async () => {
    await getJobFacets();
    const url = capturedUrl();
    // Should be /jobs/facets with no ? or an empty ?
    const qs = url.split("?")[1] || "";
    expect(new URLSearchParams(qs).toString()).toBe("");
  });
});
