/**
 * #329 / sub-issue #368
 * Cases: TC-QC-01..TC-QC-21 (docs/design/329-test-cases.md, Group A)
 *
 * Mirrors swr-cache.unit.test.js style: vi.resetModules() + dynamic re-import in
 * beforeEach so every test starts from a genuinely empty store/inflight (module-scoped
 * in-memory state, never localStorage/sessionStorage).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

let queryCache;

beforeEach(async () => {
  vi.resetModules();
  queryCache = await import("../../api/query-cache.js");
});

// ── stableKey ──────────────────────────────────────────────────────────────

describe("TC-QC-01: stableKey is order-independent for a multi-value URLSearchParams array", () => {
  it("returns the same string for reversed multi-value insertion order", () => {
    const a = new URLSearchParams();
    a.append("location", "Spain");
    a.append("location", "Remote");
    const b = new URLSearchParams();
    b.append("location", "Remote");
    b.append("location", "Spain");

    expect(queryCache.stableKey("jobs:search", a)).toBe(queryCache.stableKey("jobs:search", b));
  });
});

describe("TC-QC-02: stableKey is order-independent for the plain-object overload", () => {
  it("returns the same string for reordered object keys and reversed array element order", () => {
    const objA = { location: ["Spain", "Remote"], keyword: "react" };
    const objB = { keyword: "react", location: ["Remote", "Spain"] };

    expect(queryCache.stableKey("jobs:search", objA)).toBe(queryCache.stableKey("jobs:search", objB));
  });
});

describe("TC-QC-03: stableKey never collides across namespaces", () => {
  it("returns different strings for jobs:search vs jobs:facets given the same params", () => {
    const params = new URLSearchParams({ keyword: "react" });
    const searchKey = queryCache.stableKey("jobs:search", params);
    const facetsKey = queryCache.stableKey("jobs:facets", params);

    expect(searchKey).not.toBe(facetsKey);
  });
});

describe("TC-QC-04: stableKey treats every param as key-relevant, including page", () => {
  it("returns different strings for page=1 vs page=2", () => {
    const p1 = new URLSearchParams({ keyword: "react", page: "1" });
    const p2 = new URLSearchParams({ keyword: "react", page: "2" });

    expect(queryCache.stableKey("jobs:search", p1)).not.toBe(queryCache.stableKey("jobs:search", p2));
  });
});

// ── peek ───────────────────────────────────────────────────────────────────

describe("TC-QC-05: peek returns undefined on a genuine miss", () => {
  it("does not throw and returns undefined", () => {
    expect(() => queryCache.peek("jobs:search|anything")).not.toThrow();
    expect(queryCache.peek("jobs:search|anything")).toBeUndefined();
  });
});

describe("TC-QC-06: peek returns the exact stored value on a hit, without fetching", () => {
  it("returns the same value on repeated calls without re-invoking the fetcher", async () => {
    const fetcher = vi.fn().mockResolvedValue({ items: [1, 2, 3] });
    const key = "jobs:search|k1";
    await queryCache.cachedFetch(key, fetcher);

    expect(queryCache.peek(key)).toEqual({ items: [1, 2, 3] });
    expect(queryCache.peek(key)).toEqual({ items: [1, 2, 3] });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
});

// ── cachedFetch ────────────────────────────────────────────────────────────

describe("TC-QC-07: cachedFetch on a miss calls the fetcher exactly once and stores the result", () => {
  it("resolves the fetcher value and a subsequent peek returns it", async () => {
    const fetcher = vi.fn().mockResolvedValue({ ok: true });
    const key = "jobs:search|k2";

    const result = await queryCache.cachedFetch(key, fetcher);

    expect(result).toEqual({ ok: true });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(queryCache.peek(key)).toEqual({ ok: true });
  });
});

describe("TC-QC-08: cachedFetch on a hit resolves from the cache without calling the fetcher", () => {
  it("does not call a brand-new fetcher when the key is already populated", async () => {
    const key = "jobs:search|k3";
    queryCache.setCached(key, { cached: true });

    const fetcher2 = vi.fn().mockResolvedValue({ cached: false });
    const result = await queryCache.cachedFetch(key, fetcher2);

    expect(result).toEqual({ cached: true });
    expect(fetcher2).not.toHaveBeenCalled();
  });
});

describe("TC-QC-09: cachedFetch de-dupes concurrent same-key callers onto one fetch", () => {
  it("invokes the fetcher exactly once and both callers resolve to the same value", async () => {
    let resolveFetch;
    const fetcher = vi.fn(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        })
    );
    const key = "jobs:search|k4";

    const p1 = queryCache.cachedFetch(key, fetcher);
    const p2 = queryCache.cachedFetch(key, fetcher);

    expect(fetcher).toHaveBeenCalledTimes(1);
    resolveFetch({ value: 42 });

    const [r1, r2] = await Promise.all([p1, p2]);
    expect(r1).toEqual({ value: 42 });
    expect(r2).toEqual({ value: 42 });
  });
});

describe("TC-QC-10: cachedFetch rejection writes nothing to the cache", () => {
  it("propagates the rejection and leaves peek() returning undefined", async () => {
    const key = "jobs:search|k5";
    const fetcher = vi.fn().mockRejectedValue(new Error("boom"));

    await expect(queryCache.cachedFetch(key, fetcher)).rejects.toThrow("boom");
    expect(queryCache.peek(key)).toBeUndefined();
  });
});

describe("TC-QC-11: a retry after a rejected cachedFetch genuinely re-fetches", () => {
  it("invokes the fetcher a second time and resolves to the second call's value", async () => {
    const key = "jobs:search|k6";
    const fetcher = vi
      .fn()
      .mockRejectedValueOnce(new Error("first fails"))
      .mockResolvedValueOnce({ ok: true });

    await expect(queryCache.cachedFetch(key, fetcher)).rejects.toThrow("first fails");
    const result = await queryCache.cachedFetch(key, fetcher);

    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(result).toEqual({ ok: true });
  });
});

// ── Eviction (LRU, cap 50) ───────────────────────────────────────────────────

describe("TC-QC-12: LRU eviction drops the oldest entry once the cap of 50 is exceeded", () => {
  it("keeps _cacheSize() at 50, evicts K1, keeps K51", async () => {
    for (let i = 1; i <= 50; i++) {
      queryCache.setCached(`k${i}`, i);
    }
    expect(queryCache._cacheSize()).toBe(50);

    queryCache.setCached("k51", 51);

    expect(queryCache._cacheSize()).toBe(50);
    expect(queryCache.peek("k1")).toBeUndefined();
    expect(queryCache.peek("k51")).toBe(51);
  });
});

describe("TC-QC-13: a recently-touched key survives an eviction that would otherwise drop it", () => {
  it("K1 stays a hit after being peeked; K2 (now-oldest untouched) is evicted", async () => {
    for (let i = 1; i <= 50; i++) {
      queryCache.setCached(`k${i}`, i);
    }

    queryCache.peek("k1"); // touch K1 to most-recently-used
    queryCache.setCached("k51", 51);

    expect(queryCache.peek("k1")).toBe(1);
    expect(queryCache.peek("k2")).toBeUndefined();
  });
});

// ── prefetch ────────────────────────────────────────────────────────────────

describe("TC-QC-14: prefetch on a miss warms the cache for a later peek/cachedFetch", () => {
  it("a subsequent peek returns the fetcher's resolved value; fetcher never called twice", async () => {
    const fetcher = vi.fn().mockResolvedValue({ warm: true });
    const key = "jobs:search|k7";

    queryCache.prefetch(key, fetcher);
    await new Promise((r) => setTimeout(r, 0)); // allow the fire-and-forget promise to settle

    expect(queryCache.peek(key)).toEqual({ warm: true });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(queryCache._cacheSize()).toBe(1);
  });
});

describe("TC-QC-15: prefetch on a key already cached does not re-invoke the fetcher", () => {
  it("does not call fetcher2 for an already-populated key", async () => {
    const key = "jobs:search|k8";
    queryCache.setCached(key, { existing: true });

    const fetcher2 = vi.fn().mockResolvedValue({ existing: false });
    queryCache.prefetch(key, fetcher2);
    await new Promise((r) => setTimeout(r, 0));

    expect(fetcher2).not.toHaveBeenCalled();
  });
});

describe("TC-QC-16: prefetch swallows a rejection: no throw, nothing cached", () => {
  it("does not throw synchronously, produces no unhandled rejection, and leaves the key a miss", async () => {
    const key = "jobs:search|k9";
    const fetcher = vi.fn().mockRejectedValue(new Error("warm-up failed"));

    expect(() => queryCache.prefetch(key, fetcher)).not.toThrow();
    await new Promise((r) => setTimeout(r, 0));

    expect(queryCache.peek(key)).toBeUndefined();
  });
});

// ── clearQueryCache ──────────────────────────────────────────────────────────

describe("TC-QC-17: clearQueryCache empties the store; a cleared key is a genuine miss again", () => {
  it("resets _cacheSize to 0 and a subsequent cachedFetch re-invokes the fetcher", async () => {
    const key = "jobs:search|k10";
    const fetcher = vi.fn().mockResolvedValue({ v: 1 });
    await queryCache.cachedFetch(key, fetcher);
    queryCache.setCached("k11", { v: 2 });

    queryCache.clearQueryCache();

    expect(queryCache._cacheSize()).toBe(0);
    expect(queryCache.peek(key)).toBeUndefined();
    expect(queryCache.peek("k11")).toBeUndefined();

    await queryCache.cachedFetch(key, fetcher);
    expect(fetcher).toHaveBeenCalledTimes(2);
  });
});

describe("TC-QC-18: a fresh module import starts with an empty cache (module-boundary parity)", () => {
  it("has _cacheSize() 0 immediately after a fresh dynamic import", async () => {
    queryCache.setCached("leftover", { stale: true });
    expect(queryCache._cacheSize()).toBe(1);

    vi.resetModules();
    const fresh = await import("../../api/query-cache.js");

    expect(fresh._cacheSize()).toBe(0);
  });
});

// ── setCached ────────────────────────────────────────────────────────────────

describe("TC-QC-19: setCached writes synchronously and is readable via peek with no fetch", () => {
  it("returns the value and peek reflects it immediately, with no fetcher involved", () => {
    const key = "jobs:search|k12";
    const result = queryCache.setCached(key, { direct: true });

    expect(result).toEqual({ direct: true });
    expect(queryCache.peek(key)).toEqual({ direct: true });
  });
});

// ── _cacheSize in-flight semantics ────────────────────────────────────────────

describe("TC-QC-20: _cacheSize counts stored entries only, not in-flight-only keys", () => {
  it("stays 0 while a fetch is pending, becomes 1 once settled, and a hit does not increment it again", async () => {
    let resolveFetch;
    const fetcher = vi.fn(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        })
    );
    const key = "jobs:search|k13";

    const p = queryCache.cachedFetch(key, fetcher);
    expect(queryCache._cacheSize()).toBe(0);

    resolveFetch({ done: true });
    await p;
    expect(queryCache._cacheSize()).toBe(1);

    await queryCache.cachedFetch(key, vi.fn());
    expect(queryCache._cacheSize()).toBe(1);
  });
});

// ── Errors never counted ──────────────────────────────────────────────────────

describe("TC-QC-21: errors never reach _cacheSize as a phantom entry (analog of TC-C3c)", () => {
  it("leaves _cacheSize at 0 after a rejected cachedFetch", async () => {
    const key = "jobs:search|k14";
    const fetcher = vi.fn().mockRejectedValue(new Error("boom"));

    await expect(queryCache.cachedFetch(key, fetcher)).rejects.toThrow();
    expect(queryCache._cacheSize()).toBe(0);
  });
});
