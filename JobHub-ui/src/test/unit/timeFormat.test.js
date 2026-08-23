/**
 * Unit tests for src/lib/timeFormat.js: pure relative-time + duration helpers.
 * Story #302 (sub-issue #312): admin trigger status panel freshness/readability.
 *
 * Cases (from docs/testing/302-admin-status-panel-freshness-testcases.md):
 *   TC-14, TC-16, TC-18, TC-20, TC-22 : relativeTime(iso, now) grains + rounding
 *   TC-24, TC-26, TC-28, TC-30, TC-32 : duration(startIso, endIso) grains + boundaries
 *   TC-37, TC-39 : defensive parsing (unparseable dates, clock skew), never throws
 */
import { describe, it, expect } from "vitest";
import { relativeTime, freshnessRelativeTime, duration } from "../../lib/timeFormat.js";

const NOW = new Date("2026-06-06T10:00:00Z");

function secondsBefore(now, seconds) {
  return new Date(now.getTime() - seconds * 1000).toISOString();
}

describe("relativeTime (per-timestamp variant, BR-7)", () => {
  it("TC-14: exactly 3 minutes before now reads '3 min ago'", () => {
    const iso = secondsBefore(NOW, 3 * 60);
    expect(relativeTime(iso, NOW)).toBe("3 min ago");
  });

  it("TC-16: exactly 2 hours before now reads '2 h ago'", () => {
    const iso = secondsBefore(NOW, 2 * 60 * 60);
    expect(relativeTime(iso, NOW)).toBe("2 h ago");
  });

  it("TC-18: 45 seconds before now reads '45s ago' (not '0 min ago'/'1 min ago')", () => {
    const iso = secondsBefore(NOW, 45);
    expect(relativeTime(iso, NOW)).toBe("45s ago");
  });

  it("TC-20: the current instant (0s elapsed) reads '0s ago'", () => {
    const iso = NOW.toISOString();
    expect(relativeTime(iso, NOW)).toBe("0s ago");
  });

  it("TC-22: 89 seconds before now reads '1 min ago' (floor rounding, not '2 min ago'/'89s ago')", () => {
    const iso = secondsBefore(NOW, 89);
    expect(relativeTime(iso, NOW)).toBe("1 min ago");
  });

  it("returns null for a missing/empty timestamp (BR-10)", () => {
    expect(relativeTime(null, NOW)).toBeNull();
    expect(relativeTime("", NOW)).toBeNull();
    expect(relativeTime(undefined, NOW)).toBeNull();
  });

  it("does not throw and does not produce NaN/Invalid Date for an unparseable string (BR-10)", () => {
    expect(() => relativeTime("not-a-date", NOW)).not.toThrow();
    const result = relativeTime("not-a-date", NOW);
    if (result !== null) {
      expect(result).not.toMatch(/NaN/);
      expect(result).not.toMatch(/Invalid Date/);
    }
  });
});

describe("freshnessRelativeTime (freshness-line variant, BR-7/AC-9)", () => {
  it("reads 'just now' for sub-1-second age", () => {
    const iso = NOW.toISOString();
    expect(freshnessRelativeTime(iso, NOW)).toBe("just now");
  });

  it("reads '1s ago' at exactly 1 second elapsed", () => {
    const iso = secondsBefore(NOW, 1);
    expect(freshnessRelativeTime(iso, NOW)).toBe("1s ago");
  });

  it("reads '45s ago' at 45 seconds elapsed (still sub-minute)", () => {
    const iso = secondsBefore(NOW, 45);
    expect(freshnessRelativeTime(iso, NOW)).toBe("45s ago");
  });

  it("reads '3 min ago' at 3 minutes elapsed, matching the shared minutes/hours grain", () => {
    const iso = secondsBefore(NOW, 3 * 60);
    expect(freshnessRelativeTime(iso, NOW)).toBe("3 min ago");
  });
});

describe("duration (BR-8/BR-9/BR-10)", () => {
  it("TC-24: 42 seconds elapsed reads '42s'", () => {
    const start = "2026-06-06T10:00:00Z";
    const end = "2026-06-06T10:00:42Z";
    expect(duration(start, end)).toBe("42s");
  });

  it("TC-26: 2 minutes 14 seconds elapsed reads '2m 14s'", () => {
    const start = "2026-06-06T10:00:00Z";
    const end = "2026-06-06T10:02:14Z";
    expect(duration(start, end)).toBe("2m 14s");
  });

  it("TC-28: 1h 5m 30s elapsed reads '1h 5m' (seconds dropped at the hour grain)", () => {
    const start = "2026-06-06T09:00:00Z";
    const end = "2026-06-06T10:05:30Z";
    expect(duration(start, end)).toBe("1h 5m");
  });

  it("TC-30: exactly 60 seconds elapsed reads '1m 0s' (crosses into minutes+seconds branch)", () => {
    const start = "2026-06-06T10:00:00Z";
    const end = "2026-06-06T10:01:00Z";
    expect(duration(start, end)).toBe("1m 0s");
  });

  it("TC-32: exactly 3600 seconds elapsed reads '1h 0m' (crosses into hours+minutes branch)", () => {
    const start = "2026-06-06T09:00:00Z";
    const end = "2026-06-06T10:00:00Z";
    expect(duration(start, end)).toBe("1h 0m");
  });

  it("E7: under 1 second elapsed reads '0s' (whole seconds, floor rounding, no special-casing)", () => {
    const start = "2026-06-06T10:00:00.000Z";
    const end = "2026-06-06T10:00:00.900Z";
    expect(duration(start, end)).toBe("0s");
  });

  it("returns null when requestedAt/finishedAt is missing/null (BR-9)", () => {
    expect(duration(null, "2026-06-06T10:00:42Z")).toBeNull();
    expect(duration("2026-06-06T10:00:00Z", null)).toBeNull();
    expect(duration(null, null)).toBeNull();
  });

  it("TC-37: returns null (or a NaN/Invalid-Date-free fallback) for an unparseable date string, never throws (BR-10)", () => {
    expect(() => duration("not-a-date", "2026-06-06T10:00:42Z")).not.toThrow();
    const result1 = duration("not-a-date", "2026-06-06T10:00:42Z");
    if (result1 !== null) {
      expect(result1).not.toMatch(/NaN/);
      expect(result1).not.toMatch(/Invalid Date/);
    }

    expect(() => duration("2026-06-06T10:00:00Z", "not-a-date")).not.toThrow();
    const result2 = duration("2026-06-06T10:00:00Z", "not-a-date");
    if (result2 !== null) {
      expect(result2).not.toMatch(/NaN/);
      expect(result2).not.toMatch(/Invalid Date/);
    }
  });

  it("TC-39: finishedAt before requestedAt (clock skew) never yields a negative duration, never throws (BR-10)", () => {
    const requestedAt = "2026-06-06T10:05:00Z";
    const finishedAt = "2026-06-06T10:00:00Z";
    expect(() => duration(requestedAt, finishedAt)).not.toThrow();
    const result = duration(requestedAt, finishedAt);
    if (result !== null) {
      expect(result.startsWith("-")).toBe(false);
      expect(result).not.toMatch(/-\d/);
    }
  });
});
