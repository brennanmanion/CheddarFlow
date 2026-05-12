# Phase 1 Runbook

## Scope

Phase 1 includes only:

- the Chrome extension collector
- the local ingestion service

It does not include:

- automated login
- dark pool collection
- OHLC, open interest, Level 2, or gamma walls
- trade execution

## Start The Service

```bash
python3 service/ingestion_server.py
```

Then verify:

```bash
curl -s http://127.0.0.1:8787/health
```

If you changed extension or service code, reload the unpacked extension in `chrome://extensions` before the next live test.

## Load The Extension

1. Open `chrome://extensions`
2. Turn on `Developer mode`
3. Click `Load unpacked`
4. Select [extension](/Users/brennan/Documents/CheddarFlow/extension)

## Start Collection

1. Open normal Chrome.
2. Log in to CheddarFlow manually.
3. Navigate to the options flow page.
4. Confirm the in-page collector panel switches from `collector waiting` to `collector attached`.
5. Leave the tab open while the feed is active.

## Validate Capture

Check service health:

```bash
curl -s http://127.0.0.1:8787/health
```

The following should increase over time:

- `raw_event_count`
- `normalized_event_count`
- `heartbeat_count`

Use the inspector for a richer view:

```bash
python3 service/feed_inspector.py --db data/cheddarflow_phase1.sqlite3 summary
```

```bash
python3 service/feed_inspector.py --db data/cheddarflow_phase1.sqlite3 latest-events --limit 10
```

## What To Look For

- If the collector stays in waiting state, the page selector no longer matches the live DOM.
- If the collector switches to stale state, the page is attached but no new rows have been captured within the stale threshold.
- If `raw_event_count` grows but `normalized_event_count` does not, the row parser needs adjustment.
- If `error_count` grows, inspect `ingestion_errors` in SQLite before trusting the feed.

## Replay Test

To replay stored rows through the parser and detect drift:

```bash
python3 service/feed_inspector.py --db data/cheddarflow_phase1.sqlite3 replay --limit 10
python3 -m unittest tests/test_options_flow_parser.py
```
