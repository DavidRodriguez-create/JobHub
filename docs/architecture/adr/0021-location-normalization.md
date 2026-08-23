# ADR 0021: Location normalization at crawl-write time

- **Status:** Accepted
- **Date:** 2026-07-21
- **Deciders:** jobhub-architect (David R H)
- **Affects:** crawler-service, db/init (none consumed), api-contracts (confirmed unchanged), JobHub-ui (no change), job-service (no change)

## Context

Story #408 (sub-issue #409). The location facet in the UI is polluted with inconsistent
buckets that mix real countries, cities, US states, ISO codes, regions and qualifier noise.
The real facet dump on #408 shows the same country appearing under many spellings
(`United States`:392, `Us`:51, `Usa`:71, `us`:569, `U.s.`:7), bare US-state codes
(`Ca`:136, `Ny`:46, `Tx`:24, `Wa`:18), lowercase ISO-2 codes (`es`:348, `fr`:282, `nl`:134,
`ch`:32), qualifier suffixes (`Germany (hybrid)`, `Berlin - Remote`, `United States (on-site)`),
and multi-location strings packed into one value (`Germany; France`, `Tx | Remote`,
`Netherlands; France; Italy`).

Facts that bound the solution (verified, not re-derived):

- **One table, read cross-schema.** job-service reads `crawler.job_post` (`city`/`country`) and
  `crawler.job_post_location` with a SELECT-only grant, and its `locations` facet groups by the
  lower-cased `country` value (plus a synthetic `Remote` bucket), excluding `remote`
  (ADR 0017). So normalizing the stored `country`/`city` values at the crawler write side, plus
  a one-shot backfill of existing rows, cleans the UI facets automatically: **job-service and
  the UI need no code change**.
- **The current helper only splits and title-cases.**
  `crawler-service/.../adapter/out/client/support/LocationParser.java` comma-splits a flat
  `"City, Country"` string and title-cases each segment. It treats `"Remote"` as a country-side
  sentinel (matching ADR 0017), but it does not canonicalize ISO codes, US-state abbreviations,
  aliases, qualifier suffixes, or `;`/`|` multi-location strings. It is called by
  `LeverJobSourceClient` and `GreenhouseJobSourceClient` (primary and additional openings).
- **Established maintenance idiom.** `DescriptionBackfillRunner` and `LanguagesBackfillRunner`
  (`adapter/in/maintenance/`) are startup-time, config-guarded, idempotent, batched one-shot
  passes that reuse a pure helper (`EnrichmentParser.normalizeLanguages`) over the same rows the
  write path already normalizes. This is the house pattern to follow.
- **Schema already exists.** `job_post.city`/`country`, the `job_post_location` child table
  (ADR 0017, `db/init/014`), and the `LOWER(country)`/`LOWER(city)` performance indexes
  (`db/init/017`) are all present. Highest existing migration is `db/init/049`.
- crawler-service is **Hexagonal** (CLAUDE.md). The normalizer must be a pure, framework-free
  helper (inputs as params, no CDI/JPA/JAX-RS) so it is unit-testable without any production
  test seam (CLAUDE.md "no production code for testing").

The heart of the human ask on #408: "if it doesn't fit the normal ones, still maintain, not a
null in case of error, no lose info, maintain the value". Preserve-raw is the core acceptance
rule, not an edge case.

## Decision

We will add a **pure, framework-free `LocationNormalizer`** in
`crawler-service/.../adapter/out/client/support/` (next to `LocationParser` and
`EnrichmentParser`), refactor `LocationParser` into a thin facade that delegates to it, and add
a config-guarded **`LocationNormalizationBackfillRunner`** in `adapter/in/maintenance/` that
reuses the SAME normalizer over existing rows. No domain, port shape, contract, or schema
change: crawler-service stays Hexagonal, the normalization core is an outbound-adapter support
helper exactly like `EnrichmentParser.normalizeLanguages`.

### 1. Normalizer placement and contract

- **Package:** `adapter/out/client/support/` (matches `EnrichmentParser`, which is a pure
  static helper reused by write-path and backfill). Do NOT promote it to `domain/`: the existing
  parse/normalize helpers live in the outbound-client support package, and keeping it there
  matches house style and avoids a needless domain dependency.
- **Class:** new `public final class LocationNormalizer` with a private constructor and static
  methods. Framework-free (no annotations), all inputs as parameters, deterministic, no I/O.
- **Return type:** a small pure record `NormalizedLocation(String city, String country)` in the
  same package. (Reusing domain `JobPostLocation` is acceptable but the record keeps the
  normalizer independently unit-testable without the domain builder; recommended.)
- **`LocationParser` becomes a facade.** Keep `parseCity(String)`/`parseCountry(String)`
  signatures so the existing single-opening call sites in the source clients keep compiling, but
  route each through the normalizer so they get canonicalization for free. Add the multi-opening
  entry point on the normalizer.

Public surface (signatures only, bodies are the developer's job):

```java
public final class LocationNormalizer {
    // Full pipeline: one raw source string to an ordered opening list, primary first.
    // Handles ;/| multi-location split. Empty list for null/blank/pure-noise input.
    public static List<NormalizedLocation> normalize(String raw);

    // Backfill entry point: re-normalize an already-split stored pair.
    // Used by the runner over existing job_post rows (city/country columns).
    public static List<NormalizedLocation> normalizePair(String city, String country);

    // Single-token canonicalization core (dictionary + state + preserve-raw fallback).
    // Exposed for reuse and for the facade's parseCity/parseCountry.
    public static NormalizedLocation canonicalizeToken(String token);
}
public record NormalizedLocation(String city, String country) {}
```

### 2. Transform pipeline (exact order)

`normalize(raw)` runs, in this order:

1. **Guard.** `raw` null or blank to empty list.
2. **Top-level multi-location split.** Split on `;` and `|` into raw openings; trim each; drop
   empties. Each opening is processed independently by steps 3 to 7. The first surviving opening
   becomes the **primary**; the rest become additional openings (deduped downstream by the
   existing `JobPostMapper.toLocationEntities`, so the normalizer does NOT dedupe).
3. **Qualifier / suffix stripping** (per opening, before comma-split):
   - Strip trailing/inline parenthetical qualifiers: `(hybrid)`, `(remote)`, `(on-site)`,
     `(on site)`, `(pt)`, `(multiple)`, `(heartland)`, and any `(...)` group whose inner text is
     a work-mode / noise word. A parenthetical that is itself a location alias (for example
     `(u.s.)`, `(usa)`) is NOT discarded: strip the parens and feed the inner token back through
     canonicalization.
   - Strip trailing work-mode suffixes introduced by ` - ` or ` or `: `- Remote`, `- Hybrid`,
     `- Remote Opportunity`, `Or Remote`, `Or Remote Within United States`, etc. A ` - `/`or`
     segment that is a real place (rare) is kept; a work-mode segment is dropped.
   - Strip stray edge punctuation left by messy sources: leading/trailing `)`, `-`, `:`, `.`,
     and collapse repeated whitespace. (`Usa-`, `Ca)`, `United States):`)
4. **Remote sentinel resolution.** If, after stripping, the opening reduces to nothing but a
   remote marker (`Remote`, `Remote - Us`, `Remote In Canada`, `Remote - Na`), emit a single
   `NormalizedLocation(city=null, country="Remote")` (ADR 0017 sentinel). A place that survives
   alongside a stripped remote qualifier keeps the place. Because remote packed as its own
   `;`/`|` segment (`Tx | Remote`) was already separated in step 2, it becomes its own `Remote`
   opening naturally.
5. **Comma split.** Split the cleaned opening on `,`. One segment to slot detection (step 6a);
   two segments to `(city=left, country=right)` (step 6b); three or more to
   `(city=first, country=last)`, middle segments folded into the city text.
6. **Slot canonicalization** (each token through `canonicalizeToken`, step 7):
   - **(6a) single flat token:** canonicalize it. If it resolves to a country to
     `(city=null, country=canonicalCountry)`. If it resolves to a US state to
     `(city=stateFullName, country="United States")`. If it is the remote sentinel to
     `(city=null, country="Remote")`. Otherwise (unmappable) to
     `(city=titleCased(token), country=null)` (preserve-raw, see rule 3 below).
   - **(6b) `City, Country` pair:** the left segment is the **city**, always kept title-cased
     (cities are never dictionary-canonicalized, only cleaned/title-cased). The right segment is
     canonicalized as a **country**: a country match wins the `country` slot; a US-state match
     sets `country="United States"` (the city stays the left segment, the state text is dropped);
     an unmappable right segment is preserved title-cased as the `country` value.
7. **`canonicalizeToken(token)` (the dictionary core):**
   - Trim, collapse internal whitespace, strip surrounding punctuation/parens.
   - Build a lookup key: lower-cased, dots removed (`u.s.` to `us`, `d.c.` to `dc`), whitespace
     collapsed.
   - **Country dictionary** (name, alias, ISO-3, and NON-colliding ISO-2). Examples the dev must
     cover from the #408 dump: `us`/`usa`/`u.s.`/`united states of america` to `United States`;
     `uk`/`u.k.` to `United Kingdom`; ISO-2 `es`/`fr`/`nl`/`ch` to Spain/France/Netherlands/
     Switzerland; ISO-3 `jpn` to Japan, `ire` to Ireland; `czechia` to `Czech Republic`;
     `united arab emirates`/`uae` to `United Arab Emirates`. On a hit to
     `(city=null, country=canonical)`.
   - **US-state dictionary** (all 50 + DC), keyed by 2-letter code AND full state name. On a hit
     to `(city=stateFullName, country="United States")`. `dc`/`d.c.`/`washington dc` to
     `(city="Washington", country="United States")`.
   - No dictionary hit to the preserve-raw fallback (rule 3).

### 3. The never-null / preserve-raw fallback rule (core acceptance)

**"Cannot confidently map"** means: after qualifier stripping and lookup-key normalization, a
token matches NEITHER the country dictionary (name / alias / ISO-2 / ISO-3) NOR the US-state
dictionary NOR the `Remote` sentinel.

When a token cannot be confidently mapped:

- It is **kept**, title-cased and whitespace-normalized, in the slot it occupied (city slot for a
  single flat token or the left of a comma pair; country slot for the right of a comma pair).
- It is **never set to null and never dropped.** `Amsterdam`, `Austin`, `Barcelona`, `Bengaluru`,
  `Emea`, `Amer`, `Apac`, `North America`, `Multiple Locations`, `Atlantic Time Zones`,
  `Pst Or Est` all survive as their title-cased value (a city-only opening,
  `country=null`, for a single flat token). Info is preserved; they simply drop out of the
  `country` facet (which excludes null), which is exactly the "distinguish countries from cities"
  cleanup the story asks for.
- The **only** tokens that legitimately become null / are dropped are: empty or blank after
  stripping, and pure qualifier noise (a lone `(hybrid)` with no place left). A whole opening
  that reduces to nothing is dropped from the list; if the ENTIRE `raw` reduces to nothing, the
  original title-cased `raw` is preserved as a single city-only opening rather than losing it.

### 4. City vs country distinction

- **Flat single token:** classified by dictionary lookup (country to country slot; US state to
  United States + state city; else preserved as city). This is what moves `us`, `Ca`, `Amsterdam`
  out of the `country` facet correctly.
- **`City, Country`:** left is city (kept title-cased), right is canonicalized as country. This is
  the shape the source clients already produce for Lever/Greenhouse; the normalizer only upgrades
  the right-hand canonicalization and left-hand cleaning.

### 5. Ambiguous two-letter code ruling

Some 2-letter codes are both a US-state code and an ISO-2 country code (CA = California /
Canada, CO = Colorado / Colombia, IN = Indiana / India, MA, MD, ...). **US-state interpretation
wins** for these ambiguous codes. Rationale: the #408 dataset is US-job-heavy, the collision
countries already appear spelled out (`Canada`:90, `Colombia`:2, `India`:110), and the explicit
ISO-2 set the story targets (`us`, `es`, `fr`, `nl`, `ch`) has zero collision with US-state
codes, so the collision-free codes stay countries and the colliding ones resolve to the far more
frequent US-state reading. This is a documented, QAE-enumerable ruling; casing cannot be used to
disambiguate (sources vary), so the rule is keyed on the code itself. The residual risk (a real
Canada posting tagged bare `CA`) is small and, under preserve-raw, still yields a non-null,
non-lossy value.

### 6. Backfill mechanism

`LocationNormalizationBackfillRunner` (`adapter/in/maintenance/`), following the two existing
runners:

- `@ApplicationScoped`, `void onStart(@Observes StartupEvent)`, gated by
  `crawler.maintenance.normalize-locations` (default `false`) with
  `crawler.maintenance.normalize-locations-batch-size` (default `200`). Enable, redeploy once,
  disable again.
- For each `job_post` row it calls `LocationNormalizer.normalizePair(row.city, row.country)`,
  writes the primary opening back to `job_post.city`/`country`, and rewrites the row's
  `job_post_location` child set (the existing `syncLocations` delete-then-reinsert path, or an
  equivalent repository call) so the child table and the facet stay consistent with ADR 0017's
  primary-mirror invariant. `content_hash` is NOT recomputed or touched.
- **Termination:** unlike the description pass (rows self-drop from an HTML filter), a normalized
  row still has a non-null `city`/`country`, so a page-0 loop would re-select forever. The runner
  MUST iterate the whole table once with an **ascending-id cursor** (`WHERE id > :lastId ORDER BY
  id LIMIT :batch`), advancing `lastId` per page, stopping when a page is empty. Idempotent on
  rerun (normalization is a fixed point). Keep the `MAX_BATCHES` safety cap like the precedents.
- New repository port method (crawler `JobPostRepository`) plus its Panache impl, mirroring
  `normalizeLanguagesBatch`, but cursor-paginated: for example
  `UUID normalizeLocationsBatch(UUID afterId, int limit)` returning the last id processed (or a
  small cursor/rows-touched pair), so the runner can advance and terminate.

**Chosen over pure-SQL.** The alias/ISO/US-state dictionary, the qualifier stripping, the `;`/`|`
split and the preserve-raw fallback are far beyond maintainable SQL, must stay byte-for-byte
identical to the write path (one source of truth), and the pass must also rewrite the
`job_post_location` child rows (which SQL would duplicate awkwardly). Reusing the Java normalizer
is the maintainable, testable, single-source choice, exactly as `LanguagesBackfillRunner` reuses
`EnrichmentParser.normalizeLanguages`.

### 7. Migrations

**No schema change. The backfill is code-only.** `job_post.city`/`country`, the
`job_post_location` table (ADR 0017 / `db/init/014`) and the `LOWER(country)`/`LOWER(city)`
indexes (`db/init/017`) already exist and are sufficient; normalization changes VALUES, not
shape. **No `db/init/0NN` number is consumed by this ticket; `050` remains the next free
number** for a future ticket. (If, and only if, the developer later prefers a trivial exact-match
SQL data patch for a subset, it would take `050`, but the recommended and decided approach is the
code-only runner.)

### 8. Contract check

**No api-contracts / OpenAPI change.** `JobLocation { country?, city?, primary }` and the
`locations` facet / `location` filter response shapes in `job-service.yaml` are unchanged;
normalization only changes the string VALUES those shapes carry. The country dictionary lives
**inside crawler-service** (`adapter/out/client/support/`), NOT in `api-contracts`: it is an
internal write-side normalization concern; job-service consumes the already-canonical strings and
needs no knowledge of the dictionary.

## Consequences

- Positive: the `country` facet collapses to canonical countries (all `us`/`Us`/`Usa`/`U.s.`/
  `United States Of America` fold into `United States`; `es`/`fr`/`nl`/`ch` into their countries;
  bare US states fold under `United States` with the state in `city`), directly delivering #408's
  "distinguish countries and cities" ask, with zero job-service/UI change.
- Positive: no information is ever lost (preserve-raw), satisfying the human's core rule.
- Positive: one normalization core serves both crawl-write and the one-shot backfill, matching the
  established `EnrichmentParser` + `LanguagesBackfillRunner` pattern; stays Hexagonal, pure, and
  unit-testable with no production test seam.
- Negative / cost: a hand-maintained country-alias + US-state dictionary must be kept current; new
  aliases surface over time. Mitigated because unmappable values degrade safely (preserved, not
  broken).
- Negative / cost: the ambiguous 2-letter ruling (Section 5) can mis-read a genuinely non-US bare
  code; accepted and documented, and still non-lossy under preserve-raw.
- Negative / cost: the backfill rewrites `job_post_location` child rows per row and must use a
  cursor to terminate; slightly more involved than the languages pass.
- Follow-ups: the developer implements the normalizer + facade + runner + port method under TDD;
  QAE enumerates the #408 tokens (countries, ISO-2/3, aliases, US states, ambiguous codes,
  qualifiers, `;`/`|` multi-location, preserve-raw survivors, Remote sentinel) as unit cases and a
  component test that the backfill is idempotent and rewrites child rows. A future gazetteer
  (city to country inference, for example Amsterdam to Netherlands) is explicitly out of scope.

## Alternatives considered

- **Pure-SQL backfill / normalization** to rejected: the dictionary, qualifier stripping,
  multi-location split and preserve-raw fallback are unmaintainable in SQL and would drift from the
  write-path logic; it also could not cleanly rewrite the `job_post_location` child rows.
- **Fix it in job-service at read time** to rejected: job-service has SELECT-only access and reads
  the same table; normalizing at read time would repeat work on every query and every facet, and
  leave the stored data dirty. Normalizing once at the crawler write boundary is the single fix.
- **Promote the normalizer into `domain/`** to rejected: the existing parse/normalize helpers are
  outbound-client support (`LocationParser`, `EnrichmentParser`); keeping `LocationNormalizer`
  beside them matches house style and avoids a needless domain dependency for what is a
  source-string cleaning concern.
- **A city to country gazetteer** (infer Netherlands from Amsterdam) to deferred: large data
  dependency, not needed to satisfy #408; unmappable cities are preserved and simply leave the
  country facet.
- **Blend normalization into the existing `LocationParser` methods only** to rejected as the whole
  design: it cannot express `;`/`|` multi-location openings (single-String return) and mixes
  splitting with dictionary concerns; the facade-plus-`LocationNormalizer` split keeps each pure
  and testable while preserving the existing call sites.
