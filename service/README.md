# Phase 1 Ingestion Service

This service receives events from the Chrome extension, stores raw rows, normalizes them into a second table, and exposes a health endpoint.

It also supports feed inspection and replay testing through [feed_inspector.py](/Users/brennan/Documents/CheddarFlow/service/feed_inspector.py).

## Run

From the repo root:

```bash
python3 service/ingestion_server.py
```

Optional flags:

```bash
python3 service/ingestion_server.py --host 127.0.0.1 --port 8787 --db data/cheddarflow_phase1.sqlite3
```

## Endpoints

- `GET /health`
- `POST /api/options-flow/raw`
- `POST /api/heartbeats`

The health endpoint includes the latest collector heartbeat and stale-feed status when available.

## Inspector

Summary for a database:

```bash
python3 service/feed_inspector.py --db data/cheddarflow_phase1.sqlite3 summary
```

Latest normalized events:

```bash
python3 service/feed_inspector.py --db data/cheddarflow_phase1.sqlite3 latest-events --limit 10
```

Replay stored raw rows through the parser:

```bash
python3 service/feed_inspector.py --db data/cheddarflow_phase1.sqlite3 replay --limit 10
```

## Storage

Default database path:

- [data/cheddarflow_phase1.sqlite3](/Users/brennan/Documents/CheddarFlow/data/cheddarflow_phase1.sqlite3)

Tables:

- `collector_runs`
- `collector_heartbeats`
- `raw_events`
- `normalized_options_flow_events`
- `ingestion_errors`
