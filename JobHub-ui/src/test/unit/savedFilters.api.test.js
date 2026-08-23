/**
 * Unit tests for the saved-filter preset functions in src/api/jobs.js (story #523).
 * Cases: TC-523-B01..B06 (docs/qa/523-comp-filter-removal-and-per-user-saved-filters-test-cases.md).
 * Mirrors jobs.api.test.js's `vi.mock("../../api/client.js", () => ({ request: vi.fn() }))` pattern.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("../../api/client.js", () => ({
  request: vi.fn(),
  ApiError: class ApiError extends Error {
    constructor(status, message, body) {
      super(message || `HTTP ${status}`);
      this.name = "ApiError";
      this.status = status;
      this.body = body;
    }
  },
}));

const { listSavedFilters, createSavedFilter, deleteSavedFilter } = await import("../../api/jobs.js");
const { request, ApiError } = await import("../../api/client.js");

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("listSavedFilters", () => {
  // TC-523-B01
  it("calls GET /jobs/filters/saved with auth:true and resolves data unwrapped", async () => {
    const rows = [{ id: "sf-1", name: "Remote EU", filters: {}, createdAt: "x", updatedAt: "x" }];
    request.mockResolvedValue({ data: rows, total: null, status: 200 });

    const result = await listSavedFilters();

    expect(request).toHaveBeenCalledWith("/jobs/filters/saved", { auth: true });
    expect(result).toBe(rows);
  });

  // TC-523-B02
  it("resolves [] when request resolves { data: null }", async () => {
    request.mockResolvedValue({ data: null, total: null, status: 200 });
    const result = await listSavedFilters();
    expect(result).toEqual([]);
  });
});

describe("createSavedFilter", () => {
  // TC-523-B03
  it("calls POST /jobs/filters/saved with exactly { name, filters } as the body", async () => {
    const created = { id: "new-1", name: "React roles", filters: { keyword: "react" } };
    request.mockResolvedValue({ data: created, total: null, status: 201 });

    await createSavedFilter({ name: "React roles", filters: { keyword: "react" } });

    expect(request).toHaveBeenCalledWith("/jobs/filters/saved", {
      method: "POST",
      auth: true,
      body: { name: "React roles", filters: { keyword: "react" } },
    });
    const bodyKeys = Object.keys(request.mock.calls[0][1].body);
    expect(bodyKeys.sort()).toEqual(["filters", "name"]);
  });

  // TC-523-B04
  it("resolves with the created SavedFilterResponse unmodified", async () => {
    const created = { id: "new-1", name: "React roles", filters: { keyword: "react" } };
    request.mockResolvedValue({ data: created, total: null, status: 201 });

    const result = await createSavedFilter({ name: "React roles", filters: { keyword: "react" } });

    expect(result).toBe(created);
  });
});

describe("deleteSavedFilter", () => {
  // TC-523-B05
  it("calls DELETE /jobs/filters/saved/{id} with auth:true", async () => {
    request.mockResolvedValue({ data: null, total: null, status: 204 });

    await deleteSavedFilter("sf-1");

    expect(request).toHaveBeenCalledWith("/jobs/filters/saved/sf-1", { method: "DELETE", auth: true });
  });
});

// TC-523-B06, parametrized over 400/401/404/500/0(network), across all four saved-filter functions.
describe("saved-filter functions propagate ApiError.status unchanged on rejection", () => {
  it.each([400, 401, 404, 500, 0])("listSavedFilters propagates status %i", async (status) => {
    request.mockRejectedValue(new ApiError(status, "boom"));
    await expect(listSavedFilters()).rejects.toMatchObject({ status });
  });

  it.each([400, 401, 404, 500, 0])("createSavedFilter propagates status %i", async (status) => {
    request.mockRejectedValue(new ApiError(status, "boom"));
    await expect(createSavedFilter({ name: "x", filters: {} })).rejects.toMatchObject({ status });
  });

  it.each([400, 401, 404, 500, 0])("deleteSavedFilter propagates status %i", async (status) => {
    request.mockRejectedValue(new ApiError(status, "boom"));
    await expect(deleteSavedFilter("sf-1")).rejects.toMatchObject({ status });
  });

  it.each([400, 401, 404, 500, 0])("updateSavedFilter propagates status %i", async (status) => {
    request.mockRejectedValue(new ApiError(status, "boom"));
    const { updateSavedFilter } = await import("../../api/jobs.js");
    await expect(updateSavedFilter("sf-1", { name: "x" })).rejects.toMatchObject({ status });
  });
});
