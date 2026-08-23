/**
 * Pure relative-time and duration helpers for the admin trigger status panel.
 * Story #302 (sub-issue #312): admin trigger status panel freshness/readability.
 *
 * BR-7: a single relative-time rule is used everywhere a timestamp is shown:
 *   - under 1 minute: whole seconds ("Xs ago"), floor rounding
 *   - 1 to 59 minutes: whole minutes ("X min ago"), floor rounding
 *   - 1 hour or more: whole hours ("X h ago"), floor rounding
 * The freshness line uses its own "just now" phrasing for the sub-1-second case
 * (freshnessRelativeTime); requestedAt/finishedAt use "0s ago" uniformly (relativeTime).
 *
 * BR-8/BR-9/BR-10: duration(startIso, endIso) computes finishedAt - requestedAt,
 * formatted "Xs" / "Xm Ys" / "Xh Ym", never negative, never "NaN"/"Invalid Date",
 * returns null (no line rendered) on missing/unparseable input or clock skew.
 */

// Parses an ISO datetime string, returning null if missing/empty/unparseable.
// Mirrors AdminPage.jsx's existing fmtDateTime guard pattern (BR-10).
function parseDate(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return d;
}

// Shared grain math: given an elapsed non-negative number of whole seconds,
// returns { seconds, minutes, hours } floor-rounded.
function grainsFromSeconds(totalSeconds) {
  const seconds = Math.floor(totalSeconds);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  return { seconds, minutes, hours };
}

/**
 * Relative time for a per-run timestamp (requestedAt/finishedAt), per BR-7.
 * Sub-minute ages render in whole seconds ("0s ago".."59s ago"), including the
 * zero-second case (AC-15), distinct from the freshness line's "just now".
 *
 * @param {string|null|undefined} iso
 * @param {Date} [now] defaults to `new Date()`
 * @returns {string|null} null when iso is missing/unparseable (BR-10)
 */
export function relativeTime(iso, now = new Date()) {
  const d = parseDate(iso);
  if (!d) return null;

  const elapsedMs = now.getTime() - d.getTime();
  const elapsedSeconds = Math.max(0, elapsedMs / 1000);
  const { seconds, minutes, hours } = grainsFromSeconds(elapsedSeconds);

  if (minutes < 1) return `${seconds}s ago`;
  if (hours < 1) return `${minutes} min ago`;
  return `${hours} h ago`;
}

/**
 * Relative time for the freshness line (BR-3..BR-7, AC-9). Same grains as
 * relativeTime, except the sub-1-second case reads "just now" instead of "0s ago",
 * since it reads more naturally directly under a Refresh button (BR-7).
 *
 * @param {string|null|undefined} iso
 * @param {Date} [now] defaults to `new Date()`
 * @returns {string|null} null when iso is missing/unparseable
 */
export function freshnessRelativeTime(iso, now = new Date()) {
  const d = parseDate(iso);
  if (!d) return null;

  const elapsedMs = now.getTime() - d.getTime();
  if (elapsedMs < 1000) return "just now";
  return relativeTime(iso, now);
}

/**
 * Duration between a run's requestedAt and finishedAt (BR-8), for terminal runs.
 * - under 1 minute: "Xs" (e.g. "42s")
 * - 1 minute to under 1 hour: "Xm Ys", seconds always shown (e.g. "2m 14s", "3m 0s")
 * - 1 hour or more: "Xh Ym", seconds dropped (e.g. "1h 5m")
 *
 * Returns null (BR-9/BR-10) when either timestamp is missing/unparseable, or when
 * finishedAt is chronologically before requestedAt (clock skew): never a negative
 * or NaN-bearing string, the duration line is simply omitted.
 *
 * @param {string|null|undefined} requestedAt
 * @param {string|null|undefined} finishedAt
 * @returns {string|null}
 */
export function duration(requestedAt, finishedAt) {
  const start = parseDate(requestedAt);
  const end = parseDate(finishedAt);
  if (!start || !end) return null;

  const elapsedMs = end.getTime() - start.getTime();
  if (elapsedMs < 0) return null; // clock skew (BR-10): never negative, omit instead

  const { seconds, minutes, hours } = grainsFromSeconds(elapsedMs / 1000);

  if (minutes < 1) return `${seconds}s`;
  if (hours < 1) {
    const remainderSeconds = seconds - minutes * 60;
    return `${minutes}m ${remainderSeconds}s`;
  }
  const remainderMinutes = minutes - hours * 60;
  return `${hours}h ${remainderMinutes}m`;
}
