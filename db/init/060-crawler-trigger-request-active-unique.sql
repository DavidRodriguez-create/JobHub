
-- 060-crawler-trigger-request-active-unique.sql
-- Story #398 (ADR 0032, N2): "only one active run per kind" as a database fact, not just
-- application logic. A partial unique index on (kind, status) restricted to the active
-- statuses (queued, running) means at most one queued row and at most one running row can
-- exist per kind at any time -- but a queued row and a running row of the SAME kind may
-- coexist (a manual trigger fired while a crawl is already active is accepted and waits
-- queued, story #398 AC8), since (kind, 'queued') and (kind, 'running') are distinct keys.

CREATE UNIQUE INDEX uq_trigger_request_active_kind_status
    ON crawler.trigger_request (kind, status)
    WHERE status IN ('queued', 'running');
