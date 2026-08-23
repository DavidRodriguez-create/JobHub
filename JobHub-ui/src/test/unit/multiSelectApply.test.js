/**
 * Unit test for the symmetric-difference-size helper used by MultiSelect's
 * Apply button labelling — FE-FA-12 (BR-6).
 */
import { describe, it, expect } from "vitest";
import { symmetricDifferenceSize } from "../../components/FilterComponents.jsx";

describe("FE-FA-12 symmetricDifferenceSize computes added+removed correctly (BR-6)", () => {
  it.each([
    [new Set(), new Set(), 0],
    [new Set(["Spain"]), new Set(["Spain"]), 0],
    [new Set(["Spain"]), new Set(["Spain", "France"]), 1],
    [new Set(["Spain"]), new Set(["France"]), 2],
    [new Set(["Spain", "Germany"]), new Set(), 2],
    [new Set(["Spain"]), new Set(["Spain", "France", "Germany"]), 2],
  ])("applied=%o pending=%o => %i", (applied, pending, expected) => {
    expect(symmetricDifferenceSize(applied, pending)).toBe(expected);
  });
});
