package com.davidcreate.jobhub.crawler.domain.port.out;

import java.util.UUID;

/**
 * Result of one {@code normalizeLocationsBatch} page (story #408, ADR 0021 section 6):
 * {@code lastId} is the ascending-id cursor to pass as {@code afterId} on the next call, and
 * {@code processed} is the number of rows examined in this page. An empty result ({@code
 * processed == 0}) tells the caller the whole table has been walked once and the loop should
 * stop; unlike a re-selectable "still dirty" filter, a normalized row keeps a non-null
 * city/country, so this cursor (not the row's own state) is what bounds the loop.
 */
public record LocationBatchResult(UUID lastId, int processed) {

    public static final LocationBatchResult EMPTY = new LocationBatchResult(null, 0);

    public boolean isEmpty() {
        return processed == 0;
    }
}
