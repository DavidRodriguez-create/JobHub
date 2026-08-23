/**
 * Unit tests for the shared apply-profile copy-value helpers.
 * Story #460 (sub-issue #480), TC-460-U1 / TC-460-U2.
 */
import { describe, it, expect } from "vitest";
import { boolCopyValue, languagesCopyValue } from "../../components/applyProfile/applyProfileFields.js";

describe("TC-460-U1: boolCopyValue maps booleans to Yes/No/null (AC-460-9)", () => {
  it("returns 'Yes' for true, 'No' for false, null for null", () => {
    expect(boolCopyValue(true)).toBe("Yes");
    expect(boolCopyValue(false)).toBe("No");
    expect(boolCopyValue(null)).toBeNull();
  });

  it("never returns the raw boolean or a 'true'/'false' string", () => {
    expect(boolCopyValue(true)).not.toBe(true);
    expect(boolCopyValue(false)).not.toBe(false);
    expect(boolCopyValue(true)).not.toBe("true");
    expect(boolCopyValue(false)).not.toBe("false");
  });
});

describe("TC-460-U2: languagesCopyValue joins/nullifies language lists (AC-460-10)", () => {
  it("joins non-empty entries with ', '", () => {
    expect(languagesCopyValue(["English (native)", "Spanish (C1)"])).toBe("English (native), Spanish (C1)");
  });

  it("returns null for an empty array", () => {
    expect(languagesCopyValue([])).toBeNull();
  });

  it("returns null for a missing (null) list", () => {
    expect(languagesCopyValue(null)).toBeNull();
  });

  it("returns null for a blank-only entries list", () => {
    expect(languagesCopyValue(["", "  "])).toBeNull();
  });
});
