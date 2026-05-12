CREATE SCHEMA IF NOT EXISTS trade_execution;

CREATE TABLE IF NOT EXISTS trade_execution.execution_work_items (
    id UUID PRIMARY KEY,
    trade_candidate_id UUID NOT NULL UNIQUE,
    source_session_id UUID NOT NULL,
    normalized_event_id UUID NOT NULL,
    symbol TEXT NOT NULL,
    action TEXT NOT NULL,
    expiry TEXT,
    strike TEXT,
    put_call TEXT,
    premium_numeric NUMERIC(18, 4),
    score NUMERIC(18, 6) NOT NULL,
    confidence NUMERIC(18, 6) NOT NULL,
    routing_mode TEXT NOT NULL,
    status TEXT NOT NULL,
    notes TEXT,
    idempotency_key TEXT NOT NULL UNIQUE,
    created_at_utc TIMESTAMPTZ NOT NULL,
    updated_at_utc TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_execution_work_items_status
    ON trade_execution.execution_work_items (status, created_at_utc DESC);

CREATE INDEX IF NOT EXISTS idx_execution_work_items_normalized_event
    ON trade_execution.execution_work_items (normalized_event_id);

CREATE TABLE IF NOT EXISTS trade_execution.outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    event_version INTEGER NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    payload_json JSONB NOT NULL,
    headers_json JSONB NOT NULL,
    occurred_at_utc TIMESTAMPTZ NOT NULL,
    available_at_utc TIMESTAMPTZ NOT NULL,
    published_at_utc TIMESTAMPTZ,
    status TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_trade_execution_outbox_pending
    ON trade_execution.outbox_events (status, available_at_utc, occurred_at_utc)
    WHERE published_at_utc IS NULL;
