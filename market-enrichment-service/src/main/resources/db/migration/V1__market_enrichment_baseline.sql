CREATE SCHEMA IF NOT EXISTS market_enrichment;

CREATE TABLE IF NOT EXISTS market_enrichment.market_enrichment_records (
    id UUID PRIMARY KEY,
    source_session_id UUID NOT NULL,
    normalized_event_id UUID NOT NULL UNIQUE,
    symbol VARCHAR(32) NOT NULL,
    expiry VARCHAR(64),
    strike VARCHAR(64),
    put_call VARCHAR(16),
    premium_numeric NUMERIC(20, 4),
    enrichment_status VARCHAR(64) NOT NULL,
    ohlc_status VARCHAR(64) NOT NULL,
    open_interest_status VARCHAR(64) NOT NULL,
    gamma_walls_status VARCHAR(64) NOT NULL,
    level2_status VARCHAR(64) NOT NULL,
    provider_notes TEXT,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at_utc TIMESTAMPTZ NOT NULL,
    updated_at_utc TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS market_enrichment.outbox_events (
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

CREATE UNIQUE INDEX IF NOT EXISTS ux_market_enrichment_outbox_idempotency
    ON market_enrichment.outbox_events (idempotency_key);

CREATE INDEX IF NOT EXISTS ix_market_enrichment_outbox_pending
    ON market_enrichment.outbox_events (status, available_at_utc)
    WHERE published_at_utc IS NULL;
