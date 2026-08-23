import { getApplyProfile } from "../../api/auth.js";

/* ── Apply profile: in-memory stale-while-revalidate cache ──
   Story #483 (#3, faster load). The quick-access drawer used to re-fetch on
   every open and show a spinner each time. This module-level cache lets the
   drawer render the last-known profile instantly while it revalidates in the
   background, and lets the app prefetch after sign-in so the first open is
   instant too.

   Not user-keyed on purpose: the cache is cleared on sign-out
   (clearApplyProfileCache, wired from App.clearUserData), so a value can never
   leak across accounts. Settings writes push their fresh result in via
   setCachedApplyProfile so an edit is never served stale. */

let cache = null; // last successfully fetched ApplyProfileResponse, or null

export function getCachedApplyProfile() {
  return cache;
}

export function setCachedApplyProfile(data) {
  cache = data;
}

export function clearApplyProfileCache() {
  cache = null;
}

// Fetch fresh, update the cache, resolve with the data. Errors propagate so the
// caller can decide (e.g. the drawer surfaces an error only when it has no cache
// to fall back on).
export function fetchApplyProfile() {
  return getApplyProfile().then((data) => {
    cache = data;
    return data;
  });
}

// Best-effort background warm-up (used right after sign-in). Never rejects.
export function prefetchApplyProfile() {
  return fetchApplyProfile().catch(() => {});
}
