CREATE SCHEMA IF NOT EXISTS signal_evaluation;

CREATE TABLE IF NOT EXISTS signal_evaluation.signal_evaluations (
    id UUID PRIMARY KEY,
    source_session_id UUID NOT NULL,
    normalized_event_id UUID NOT NULL UNIQUE,
    enrichment_record_id UUID NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    strategy_name VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    score NUMERIC(20, 6) NOT NULL,
    confidence NUMERIC(20, 6) NOT NULL,
    status VARCHAR(64) NOT NULL,
    rationale TEXT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at_utc TIMESTAMPTZ NOT NULL,
    updated_at_utc TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS signal_evaluation.trade_candidates (
    id UUID PRIMARY KEY,
    signal_evaluation_id UUID NOT NULL UNIQUE,
    source_session_id UUID NOT NULL,
    normalized_event_id UUID NOT NULL UNIQUE,
    symbol VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    score NUMERIC(20, 6) NOT NULL,
    confidence NUMERIC(20, 6) NOT NULL,
    status VARCHAR(64) NOT NULL,
    created_at_utc TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS signal_evaluation.outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    payload_json JSONB NOT NULL,
    headers_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at_utc TIMESTAMPTZ NOT NULL,
    available_at_utc TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at_utc TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_signal_evaluation_outbox_idempotency
    ON signal_evaluation.outbox_events (idempotency_key);

CREATE INDEX IF NOT EXISTS ix_signal_evaluation_outbox_pending
    ON signal_evaluation.outbox_events (status, available_at_utc)
    WHERE published_at_utc IS NULL;
