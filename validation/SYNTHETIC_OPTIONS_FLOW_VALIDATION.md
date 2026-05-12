# Synthetic Options-Flow Validation

## Goal

Run a repeatable after-hours regression test for the real browser-to-Java path without waiting for live market flow.

This validation proves:

- the Chrome extension is attached
- the mutation observer still captures new rows
- the Java ingestion service accepts the browser payloads
- normalization still works
- outbox rows are still written

## Preconditions

1. Docker services are up:

```bash
docker compose up -d postgres artemis
```

2. The Java services you want to validate are running:

- `collector-ingestion-service`
- later `market-enrichment-service`
- later `signal-evaluation-service`

3. The Chrome extension is loaded and the CheddarFlow options-flow page is open.

## Browser Step

Open DevTools on the CheddarFlow tab and run the script from:

- [inject_synthetic_options_flow_row.js](/Users/brennan/Documents/CheddarFlow/validation/inject_synthetic_options_flow_row.js)

Run it once for a single mutation, or run it multiple times to force multiple mutation events.

The injected row:

- uses `symbol=SPY`
- uses `premium=$1.5M`
- adds the condition tag `synthetic`

That lets us query for the exact validation rows later.

## What To Expect On The Page

Immediately after injection:

- `events` should increment
- `queue` should rise briefly, then drain
- `send_failures` should not increase
- `last_error` should stay empty

After market close, `collector stale` is still expected later if no further rows arrive.

## PostgreSQL Verification

Run:

```bash
docker compose exec postgres psql -U cheddarflow -d cheddarflow -f /workspace/validation/verify_synthetic_options_flow.sql
```

If you are not mounting the repo into the container, run this from the host instead:

```bash
docker compose exec postgres psql -U cheddarflow -d cheddarflow -c "select r.session_id, n.symbol, n.premium_text, n.premium_numeric, n.conditions, r.observed_via, n.captured_at_utc from raw_browser_events r join normalized_options_flow_events n on n.raw_event_id = r.id where n.conditions ilike '%synthetic%' order by n.captured_at_utc desc limit 20;"
```

Success criteria:

- rows appear with `conditions = AUTO synthetic`
- `observed_via = mutation`
- `premium_numeric = 1500000.0000`

## End-To-End Pipeline Verification

Once Artemis and the downstream Java services are running, verify:

1. synthetic rows appear in `raw_browser_events`
2. matching `OptionsFlowCaptured` rows are published from `outbox_events`
3. `market_enrichment` records are created
4. matching `OptionsFlowEnriched` rows are published
5. `signal_evaluation` rows are created
6. if the rule threshold is met, a trade candidate row is created

## Regression Use

Use this after:

- DOM selector changes
- extension queueing changes
- Java ingestion changes
- broker or outbox changes
- consumer changes

This is the fastest closed-market validation path for the current stack.
