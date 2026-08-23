package com.davidcreate.jobhub.job.domain.model;

public enum TriggerKind {
    CRAWL("crawl"),
    ENRICHMENT("enrichment");

    private final String value;

    TriggerKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TriggerKind fromValue(String value) {
        for (TriggerKind k : values()) {
            if (k.value.equals(value)) {
                return k;
            }
        }
        throw new IllegalArgumentException("Unknown trigger kind '" + value + "'");
    }
}
