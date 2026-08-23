package com.davidcreate.jobhub.crawler.adapter.out.client.support;

/**
 * One canonicalized opening produced by {@link LocationNormalizer}: either part may be null
 * (the special value {@code "Remote"} lives in {@code country}, matching how the parent
 * {@code job_post.city}/{@code country} columns already represent the sentinel, ADR 0017).
 * A pure record so the normalizer is independently unit-testable without the domain builder.
 */
public record NormalizedLocation(String city, String country) {
}
