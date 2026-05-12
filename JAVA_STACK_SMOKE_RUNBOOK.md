# Java Stack Smoke Runbook

This runbook validates the current Java event-driven path:

1. `collector-ingestion-service`
2. `market-enrichment-service`
3. `signal-evaluation-service`
4. `trade-execution-service`

## Infrastructure

Start local dependencies:

```bash
docker compose up -d postgres artemis
```

## Service Startup

Build everything:

```bash
mvn -q -DskipTests package
```

Start services in separate terminals:

```bash
java -jar collector-ingestion-service/target/collector-ingestion-service-0.1.0-SNAPSHOT.jar
java -jar market-enrichment-service/target/market-enrichment-service-0.1.0-SNAPSHOT.jar
java -jar signal-evaluation-service/target/signal-evaluation-service-0.1.0-SNAPSHOT.jar
java -jar trade-execution-service/target/trade-execution-service-0.1.0-SNAPSHOT.jar
```

## Collector Health

Verify the HTTP ingress side:

```bash
curl -s http://127.0.0.1:8787/actuator/health
```

Expected:

- `status = UP`
- `db = UP`
- `jms = UP`

## Synthetic End-to-End Event

Post a synthetic options-flow row:

```bash
curl -s -X POST http://127.0.0.1:8787/api/options-flow/raw \
  -H 'Content-Type: application/json' \
  --data @synthetic-options-flow.json
```

The payload should include:

- `collector_type = options_flow`
- a unique `session_id`
- a unique `dom_key`
- row HTML containing `symbol`, `expiry`, `strike`, `putCall`, `premium`, and `conds`

## Database Verification

Confirm the event reached every stage:

```sql
select count(*) from normalized_options_flow_events where session_id = '<session-id>';
select count(*) from market_enrichment.market_enrichment_records where source_session_id = '<session-id>';
select count(*) from signal_evaluation.signal_evaluations where source_session_id = '<session-id>';
select count(*) from signal_evaluation.trade_candidates where source_session_id = '<session-id>';
select count(*) from trade_execution.execution_work_items where source_session_id = '<session-id>';
```

Inspect the final row:

```sql
select
  n.symbol,
  n.expiry,
  n.strike,
  n.put_call,
  n.premium_numeric,
  se.status as signal_status,
  tc.status as candidate_status,
  ewi.status as execution_status,
  ewi.routing_mode
from normalized_options_flow_events n
left join signal_evaluation.signal_evaluations se on se.normalized_event_id = n.id
left join signal_evaluation.trade_candidates tc on tc.normalized_event_id = n.id
left join trade_execution.execution_work_items ewi on ewi.normalized_event_id = n.id
where n.session_id = '<session-id>';
```

Expected for an actionable synthetic row:

- `signal_status = CANDIDATE_CREATED`
- `candidate_status = CREATED`
- `execution_status = QUEUED`
- `routing_mode = PAPER_REVIEW`

## Browser Path

For the browser-based path, keep using the Chrome extension to post to:

- `http://127.0.0.1:8787/api/options-flow/raw`
- `http://127.0.0.1:8787/api/heartbeats`

The extension is the authenticated capture edge. Everything after that edge is now Java.
