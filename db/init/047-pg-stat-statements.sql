-- 047-pg-stat-statements.sql
-- Story #328: enable the pg_stat_statements extension so slow-query analysis can
-- correlate the new RequestLoggingFilter slow-request WARN lines with per-query
-- execution stats (total/mean time, calls) across all service schemas.
--
-- Forward-only. Idempotent (CREATE EXTENSION IF NOT EXISTS).
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
