/**
 * Unit tests for src/api/auth.js — apply-profile answer bank (Story #336, ticket #422).
 * Contract: GET/PUT /auth/account/apply-profile (auth-service.yaml, ADR 0022).
 *
 * FE-API-1: getApplyProfile() sends GET /auth/account/apply-profile with auth:true
 *           and returns the response data as-is (ApplyProfileResponse).
 * FE-API-2: saveApplyProfile(body) sends PUT /auth/account/apply-profile with
 *           auth:true and the given body, returning the response data.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("../../api/client.js", () => ({
  request: vi.fn(),
  setToken: vi.fn(),
  clearToken: vi.fn(),
  getToken: vi.fn(() => null),
}));

const { request } = await import("../../api/client.js");
const { getApplyProfile, saveApplyProfile } = await import("../../api/auth.js");

beforeEach(() => {
  request.mockResolvedValue({ data: null, total: null, status: 200 });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("FE-API-1: getApplyProfile sends GET /auth/account/apply-profile", () => {
  it("requests with auth:true and returns the response data", async () => {
    const response = {
      workAuthorization: "US Citizen",
      requiresSponsorship: false,
      noticePeriod: "2 weeks",
      salaryExpectation: "$120k-$140k",
      currentLocation: "Madrid, Spain",
      willingToRelocate: true,
      linkedinUrl: "https://linkedin.com/in/alice",
      githubUrl: "https://github.com/alice",
      portfolioUrl: "https://alice.dev",
      languages: ["English (native)", "Spanish (C1)"],
      roomToGrow: "Grow into a staff engineer role",
      updatedAt: "2026-07-20T10:00:00Z",
    };
    request.mockResolvedValueOnce({ data: response, total: null, status: 200 });

    const result = await getApplyProfile();

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe("/auth/account/apply-profile");
    expect(opts.auth).toBe(true);
    expect(opts.method).toBeUndefined(); // default GET
    expect(result).toEqual(response);
  });

  it("returns an all-null shape on a never-saved profile without throwing", async () => {
    const allNull = {
      workAuthorization: null,
      requiresSponsorship: null,
      noticePeriod: null,
      salaryExpectation: null,
      currentLocation: null,
      willingToRelocate: null,
      linkedinUrl: null,
      githubUrl: null,
      portfolioUrl: null,
      languages: null,
      roomToGrow: null,
      updatedAt: null,
    };
    request.mockResolvedValueOnce({ data: allNull, total: null, status: 200 });

    const result = await getApplyProfile();
    expect(result).toEqual(allNull);
  });
});

describe("FE-API-2: saveApplyProfile sends PUT /auth/account/apply-profile", () => {
  it("sends the given body with auth:true and returns the response data", async () => {
    const body = {
      workAuthorization: "US Citizen",
      requiresSponsorship: null,
      noticePeriod: null,
      salaryExpectation: null,
      currentLocation: "Madrid, Spain",
      willingToRelocate: null,
      linkedinUrl: null,
      githubUrl: null,
      portfolioUrl: null,
      languages: null,
      roomToGrow: null,
    };
    const response = { ...body, updatedAt: "2026-07-22T09:00:00Z" };
    request.mockResolvedValueOnce({ data: response, total: null, status: 200 });

    const result = await saveApplyProfile(body);

    expect(request).toHaveBeenCalledTimes(1);
    const [path, opts] = request.mock.calls[0];
    expect(path).toBe("/auth/account/apply-profile");
    expect(opts.method).toBe("PUT");
    expect(opts.auth).toBe(true);
    expect(opts.body).toEqual(body);
    expect(result).toEqual(response);
  });
});
