package com.davidcreate.jobhub.crawler.domain.model;

public enum TriggerKind {
    CRAWL,
    ENRICHMENT;

    public String value() {
        return name().toLowerCase();
    }

    public static TriggerKind fromValue(String value) {
        return TriggerKind.valueOf(value.toUpperCase());
    }
}
