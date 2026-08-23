// Generic, framework-free, module-scoped keyed request cache.
// Story #329 / sub-issue #368 (client-side query cache: debounce, keep-previous-data, prefetch).
// Mirrors the module-scoped SWR pattern already proven in notifications.js (_prefsCache),
// generalized to support many keys (one per filter combination) instead of a single fixed key.
//
// State lives ONLY in module scope, in plain in-memory Maps. It is never written to
// localStorage / sessionStorage: a full page reload (module re-execution) starts empty,
// exactly like swr-cache.unit.test.js exercises for notifications.js.
//
// Errors are never cached: cachedFetch only writes to `store` in the fulfilled branch of
// the supplied fetcher. LRU eviction keeps the cache bounded at MAX_ENTRIES.

const MAX_ENTRIES = 50;

// value store, Map insertion order == LRU order (oldest first, newest/most-recently-used last)
const store = new Map();
// in-flight de-dupe: key -> Promise currently resolving that key's fetch
const inflight = new Map();

function toPairs(params) {
  if (params instanceof URLSearchParams) {
    return [...params.entries()];
  }
  const pairs = [];
  if (params && typeof params === "object") {
    for (const key of Object.keys(params)) {
      const value = params[key];
      if (value == null || value === "") continue;
      if (Array.isArray(value)) {
        for (const v of value) {
          if (v != null && v !== "") pairs.push([key, String(v)]);
        }
      } else {
        pairs.push([key, String(value)]);
      }
    }
  }
  return pairs;
}

/**
 * Derive a stable, order-independent cache key.
 * @param {string} namespace short label so different callers never collide, e.g.
 *   "jobs:search" / "jobs:facets".
 * @param {URLSearchParams|object} [params] the request's own query params (preferred: a
 *   URLSearchParams, since callers already build one), or a plain object for generality.
 * @returns {string} deterministic string; equal filters (any array/key order) map to one key.
 */
export function stableKey(namespace, params) {
  const pairs = toPairs(params);
  pairs.sort((a, b) => {
    if (a[0] !== b[0]) return a[0] < b[0] ? -1 : 1;
    if (a[1] === b[1]) return 0;
    return a[1] < b[1] ? -1 : 1;
  });
  return `${namespace}|${JSON.stringify(pairs)}`;
}

/**
 * Synchronous read of the last cached value for a key, or undefined on miss.
 * A hit touches LRU recency. Never fetches. Never falls back to a different key.
 */
export function peek(key) {
  if (!store.has(key)) return undefined;
  const value = store.get(key);
  // touch recency: delete then re-set moves this key to the newest (most-recently-used) position
  store.delete(key);
  store.set(key, value);
  return value;
}

function evictIfNeeded() {
  while (store.size > MAX_ENTRIES) {
    const oldestKey = store.keys().next().value;
    store.delete(oldestKey);
  }
}

/**
 * Write-through: store a value under a key without a fetch (e.g. a PUT/POST response body).
 * @returns {*} value (for chaining)
 */
export function setCached(key, value) {
  if (store.has(key)) store.delete(key); // re-set below moves it to the newest position
  store.set(key, value);
  evictIfNeeded();
  return value;
}

/**
 * Cache-first async read. On hit: resolves the cached value (no network).
 * On miss: calls fetcher(), stores the resolved value, resolves it.
 * De-dupes concurrent callers of the same key onto one in-flight promise.
 * On fetcher rejection: NOTHING is written; the rejection propagates.
 * @param {string} key
 * @param {() => Promise<*>} fetcher
 * @returns {Promise<*>}
 */
export function cachedFetch(key, fetcher) {
  if (store.has(key)) {
    return Promise.resolve(peek(key));
  }
  if (inflight.has(key)) {
    return inflight.get(key);
  }
  // Call the fetcher synchronously (not deferred to a microtask) so concurrent same-key
  // callers observe `inflight` already populated before either one settles.
  let raw;
  try {
    raw = Promise.resolve(fetcher());
  } catch (err) {
    raw = Promise.reject(err);
  }
  const tracked = raw.then(
    (value) => {
      inflight.delete(key);
      setCached(key, value);
      return value;
    },
    (err) => {
      inflight.delete(key);
      throw err;
    }
  );
  inflight.set(key, tracked);
  return tracked;
}

/**
 * Fire-and-forget warm-up. Same semantics as cachedFetch but returns nothing and
 * swallows errors (a failed prefetch must never surface to the user or reject).
 */
export function prefetch(key, fetcher) {
  cachedFetch(key, fetcher).catch(() => {});
}

/** Drop every entry and every in-flight promise. Called on logout / session boundary. */
export function clearQueryCache() {
  store.clear();
  inflight.clear();
}

/** Test-only introspection. Number of cached entries (excludes in-flight-only keys). */
export function _cacheSize() {
  return store.size;
}
