/**
 * Unit tests for the saved-filter preset mappers, src/api/mappers.js (story #523).
 * Cases: TC-523-M01..M20 (docs/qa/523-comp-filter-removal-and-per-user-saved-filters-test-cases.md).
 * Plain JS, no React, no network mocking; mirrors jobs.api.test.js's non-network describe blocks.
 */
import { describe, it, expect } from "vitest";
import {
  POSTED_UI_TO_API,
  POSTED_API_TO_UI,
  filterValuesFromPreset,
  presetFromFilterValues,
  savedFilterFromApi,
} from "../../api/mappers.js";

function fullPresetState(overrides = {}) {
  return {
    query: "react developer",
    companies: ["Acme Corp"],
    locations: ["Spain"],
    employmentTypes: ["full-time"],
    careerLevels: ["senior"],
    language: "Spanish",
    posted: "week",
    ...overrides,
  };
}

describe("filterValuesFromPreset", () => {
  // TC-523-M01
  it("maps a fully-populated preset to exactly the seven FilterValues keys, no comp/sort", () => {
    const result = filterValuesFromPreset(fullPresetState());
    expect(result).toEqual({
      keyword: "react developer",
      company: ["Acme Corp"],
      location: ["Spain"],
      employmentType: ["full-time"],
      careerLevel: ["senior"],
      language: ["Spanish"],
      postedWithin: "week",
    });
    expect(result).not.toHaveProperty("compensationMin");
    expect(result).not.toHaveProperty("compensationMax");
    expect(result).not.toHaveProperty("sort");
  });

  // TC-523-M02
  it("omits every field entirely on an all-blank preset (absent keys, not undefined values)", () => {
    const result = filterValuesFromPreset({
      query: "",
      companies: [],
      locations: [],
      employmentTypes: [],
      careerLevels: [],
      language: "all",
      posted: "any",
    });
    expect(Object.keys(result)).toEqual([]);
  });

  // TC-523-M03
  it("omits keyword when query is whitespace-only", () => {
    const result = filterValuesFromPreset(fullPresetState({ query: "   " }));
    expect(result).not.toHaveProperty("keyword");
  });

  // TC-523-M04
  it('omits language entirely when language is "all"', () => {
    const result = filterValuesFromPreset(fullPresetState({ language: "all" }));
    expect(result).not.toHaveProperty("language");
  });

  // TC-523-M05
  it("sends language as a single-element array for a concrete language", () => {
    const result = filterValuesFromPreset(fullPresetState({ language: "Spanish" }));
    expect(result.language).toEqual(["Spanish"]);
  });

  // TC-523-M06
  it.each([
    ["any", undefined],
    ["today", "today"],
    ["3days", "3d"],
    ["week", "week"],
    ["month", "month"],
  ])("derives postedWithin for posted=%s via POSTED_UI_TO_API", (posted, expected) => {
    const result = filterValuesFromPreset(fullPresetState({ posted }));
    if (expected === undefined) expect(result).not.toHaveProperty("postedWithin");
    else expect(result.postedWithin).toBe(expected);
  });

  // TC-523-M07
  it("never includes compensationMin/compensationMax/sort even if the input state carries them", () => {
    const stateWithStrayFields = {
      ...fullPresetState(),
      compMin: 30,
      compMax: 150,
      sortBy: "salary",
      compensationMin: 30000,
      compensationMax: 150000,
      sort: "salary-desc",
    };
    const result = filterValuesFromPreset(stateWithStrayFields);
    expect(result).not.toHaveProperty("compensationMin");
    expect(result).not.toHaveProperty("compensationMax");
    expect(result).not.toHaveProperty("sort");
  });
});

const FULL_FILTER_VALUES = {
  keyword: "react developer",
  company: ["Acme Corp"],
  location: ["Spain"],
  employmentType: ["full-time"],
  careerLevel: ["senior"],
  language: ["Spanish"],
  postedWithin: "week",
};

describe("presetFromFilterValues", () => {
  // TC-523-M08
  it("restores every non-comp/non-sort dimension from a fully-populated FilterValues", () => {
    const filters = { ...FULL_FILTER_VALUES, compensationMin: 30000, compensationMax: 150000, sort: "salary-desc" };
    const result = presetFromFilterValues(filters);
    expect(result.query).toBe("react developer");
    expect(result.companies).toEqual(["Acme Corp"]);
    expect(result.locations).toEqual(["Spain"]);
    expect(result.employmentTypes).toEqual(["full-time"]);
    expect(result.careerLevels).toEqual(["senior"]);
    expect(result.language).toBe("Spanish");
    expect(result.posted).toBe("week");
  });

  // TC-523-M09
  it("never carries compensationMin/compensationMax/sort on the returned object", () => {
    const filters = { ...FULL_FILTER_VALUES, compensationMin: 30000, compensationMax: 150000, sort: "salary-desc" };
    const result = presetFromFilterValues(filters);
    expect(result).not.toHaveProperty("compensationMin");
    expect(result).not.toHaveProperty("compensationMax");
    expect(result).not.toHaveProperty("sort");
  });

  // TC-523-M10
  it("returns query: '' when filters.keyword is absent", () => {
    const result = presetFromFilterValues({});
    expect(result.query).toBe("");
  });

  // TC-523-M11
  it.each(["company", "location", "employmentType", "careerLevel"])(
    "returns an empty array for %s when absent or malformed (non-array)",
    (key) => {
      const absent = presetFromFilterValues({});
      const malformed = presetFromFilterValues({ [key]: "not-an-array" });
      const outKey = { company: "companies", location: "locations", employmentType: "employmentTypes", careerLevel: "careerLevels" }[key];
      expect(absent[outKey]).toEqual([]);
      expect(() => presetFromFilterValues({ [key]: "not-an-array" })).not.toThrow();
      expect(malformed[outKey]).toEqual([]);
    }
  );

  // TC-523-M12
  it("returns the first element of a multi-value language array, never a joined string", () => {
    const result = presetFromFilterValues({ language: ["Spanish", "German"] });
    expect(result.language).toBe("Spanish");
  });

  // TC-523-M13
  it.each([undefined, []])("returns language: 'all' when filters.language is %s", (language) => {
    const result = presetFromFilterValues(language === undefined ? {} : { language });
    expect(result.language).toBe("all");
  });

  // TC-523-M14
  it.each([
    ["today", "today"],
    ["3d", "3days"],
    ["week", "week"],
    ["month", "month"],
    [undefined, "any"],
  ])("derives posted for postedWithin=%s via POSTED_API_TO_UI", (postedWithin, expected) => {
    const result = presetFromFilterValues(postedWithin === undefined ? {} : { postedWithin });
    expect(result.posted).toBe(expected);
  });

  // TC-523-M15
  it("falls back to posted: 'any' for an unrecognised postedWithin value, no throw", () => {
    expect(() => presetFromFilterValues({ postedWithin: "some-future-value" })).not.toThrow();
    const result = presetFromFilterValues({ postedWithin: "some-future-value" });
    expect(result.posted).toBe("any");
  });

  // TC-523-M19
  it("round-trips a comp/sort-free FilterValues object key-for-key", () => {
    const roundTripped = filterValuesFromPreset(presetFromFilterValues(FULL_FILTER_VALUES));
    expect(roundTripped).toEqual(FULL_FILTER_VALUES);
  });
});

describe("savedFilterFromApi", () => {
  // TC-523-M16
  it("maps a well-formed SavedFilterResponse to { id, name, state }", () => {
    const dto = {
      id: "sf-1",
      name: "Remote EU",
      filters: FULL_FILTER_VALUES,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-02T00:00:00Z",
    };
    const result = savedFilterFromApi(dto);
    expect(result).toEqual({
      id: "sf-1",
      name: "Remote EU",
      state: presetFromFilterValues(dto.filters),
    });
  });

  // TC-523-M17
  it("does not throw and returns the all-absent default state when dto.filters is null", () => {
    const dto = { id: "sf-2", name: "Null filters", filters: null };
    expect(() => savedFilterFromApi(dto)).not.toThrow();
    const result = savedFilterFromApi(dto);
    expect(result.state).toEqual({
      query: "",
      companies: [],
      locations: [],
      employmentTypes: [],
      careerLevels: [],
      language: "all",
      posted: "any",
    });
  });

  // TC-523-M18
  it("does not throw and returns the all-absent default state when dto.filters is entirely absent", () => {
    const dto = { id: "sf-3", name: "No filters key" };
    expect(() => savedFilterFromApi(dto)).not.toThrow();
    const result = savedFilterFromApi(dto);
    expect(result.state.query).toBe("");
    expect(result.state.companies).toEqual([]);
    expect(result.state.language).toBe("all");
    expect(result.state.posted).toBe("any");
  });
});

// TC-523-M20 is a reviewer/grep confirmation (design note section 4.3's "cannot drift
// apart" property): JobSearch.jsx's local POSTED_MAP is deleted and the screen imports
// POSTED_UI_TO_API from mappers.js. Confirmed by reading the screen's import list, not by
// a runtime assertion (a Vitest case cannot observe "the same object reference was
// imported twice" any better than a source read can).
