CREATE TABLE IF NOT EXISTS collector_runs (
    session_id UUID PRIMARY KEY,
    collector_type VARCHAR(64) NOT NULL,
    page_url TEXT,
    page_title TEXT,
    started_at_utc TIMESTAMPTZ NOT NULL,
    last_heartbeat_at_utc TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS collector_heartbeats (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES collector_runs(session_id),
    collector_type VARCHAR(64) NOT NULL,
    page_url TEXT,
    page_title TEXT,
    attached BOOLEAN NOT NULL,
    attach_attempts INTEGER NOT NULL,
    queued_event_count INTEGER NOT NULL,
    captured_event_count INTEGER NOT NULL,
    duplicate_count INTEGER NOT NULL,
    parse_failure_count INTEGER NOT NULL,
    send_failure_count INTEGER NOT NULL,
    source_selector TEXT,
    reason VARCHAR(64),
    last_capture_at_utc TIMESTAMPTZ,
    capture_age_seconds DOUBLE PRECISION,
    is_stale BOOLEAN NOT NULL DEFAULT FALSE,
    stale_reason VARCHAR(64),
    heartbeat_at_utc TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS raw_browser_events (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES collector_runs(session_id),
    collector_type VARCHAR(64) NOT NULL,
    page_url TEXT,
    page_title TEXT,
    source_selector TEXT,
    dom_key VARCHAR(255) NOT NULL DEFAULT '',
    source_html TEXT NOT NULL,
    source_text TEXT,
    row_signature TEXT,
    source_hash CHAR(64) NOT NULL,
    client_hash CHAR(64),
    observed_via VARCHAR(64),
    captured_at_utc TIMESTAMPTZ NOT NULL,
    ingested_at_utc TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_raw_browser_events_session_hash_dom
    ON raw_browser_events (session_id, source_hash, dom_key);

CREATE INDEX IF NOT EXISTS ix_raw_browser_events_captured_at
    ON raw_browser_events (captured_at_utc DESC);

CREATE TABLE IF NOT EXISTS normalized_options_flow_events (
    id UUID PRIMARY KEY,
    raw_event_id UUID NOT NULL UNIQUE REFERENCES raw_browser_events(id),
    session_id UUID NOT NULL REFERENCES collector_runs(session_id),
    event_time_text VARCHAR(64),
    event_date_text VARCHAR(64),
    symbol VARCHAR(32),
    expiry VARCHAR(64),
    strike VARCHAR(64),
    put_call VARCHAR(16),
    side VARCHAR(32),
    buy_sell VARCHAR(32),
    spot VARCHAR(64),
    size VARCHAR(64),
    price VARCHAR(64),
    premium_text VARCHAR(64),
    premium_numeric NUMERIC(20, 4),
    sweep_block_split VARCHAR(64),
    volume VARCHAR(64),
    open_interest VARCHAR(64),
    conditions TEXT,
    captured_at_utc TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_normalized_options_flow_events_symbol_captured_at
    ON normalized_options_flow_events (symbol, captured_at_utc DESC);

CREATE TABLE IF NOT EXISTS ingestion_errors (
    id UUID PRIMARY KEY,
    created_at_utc TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    session_id UUID,
    error_type VARCHAR(128) NOT NULL,
    detail TEXT NOT NULL,
    payload_json JSONB
);

CREATE TABLE IF NOT EXISTS outbox_events (
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

CREATE UNIQUE INDEX IF NOT EXISTS ux_outbox_events_idempotency_key
    ON outbox_events (idempotency_key);

CREATE INDEX IF NOT EXISTS ix_outbox_events_pending
    ON outbox_events (status, available_at_utc)
    WHERE published_at_utc IS NULL;
