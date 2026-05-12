# Java Event Pipeline Plan

## Goal

Plan the next architecture step after browser collection: take captured options-flow events, enrich them with outside market data, and evaluate whether an actionable trade exists.

## Constraint

The browser-side collector remains a Chrome extension, so that one boundary is not Java.

Everything after the browser should move to Java and Spring Boot.

## Recommendation

Use:

- Java 21
- Spring Boot
- PostgreSQL as the durable system of record for production
- ActiveMQ Artemis as the queue broker

Why ActiveMQ Artemis:

- good fit for a Java-first stack
- straightforward Spring Boot integration
- durable queues, dead-letter handling, and retry patterns
- JMS-friendly model if we want a conventional enterprise message contract

## High-Level Architecture

### 1. Browser Collector

Current responsibility:

- capture live CheddarFlow rows in the authenticated browser

Near-term role:

- post raw row events to a local or network-reachable ingestion endpoint

Technology:

- Chrome extension

### 2. Collector Ingestion Service

Spring Boot service.

Responsibilities:

- receive raw browser events
- normalize the row content
- write raw and normalized records to PostgreSQL
- create an outbox event in the same transaction

Suggested name:

- `collector-ingestion-service`

### 3. Outbox Publisher

Spring Boot background component or separate service.

Responsibilities:

- read committed outbox rows
- publish messages to ActiveMQ Artemis
- mark outbox rows as published

Suggested name:

- `event-publisher-service`

### 4. Enrichment Service

Spring Boot worker service that consumes newly captured options-flow events and enriches them with external market data.

Responsibilities:

- pull OHLC
- pull open interest
- pull other provider-specific context
- store enrichment records
- publish an enrichment-complete event

Suggested name:

- `market-enrichment-service`

### 5. Signal Evaluation Service

Spring Boot worker service that consumes enriched events and decides whether they meet trade criteria.

Responsibilities:

- compute derived features
- apply signal rules or model scoring
- store the evaluation result
- emit trade candidates for later review or execution

Suggested name:

- `signal-evaluation-service`

### 6. Execution Service

Later phase, not immediate.

Responsibilities:

- consume approved trade candidates
- apply risk controls
- send orders to a broker
- persist order lifecycle state

Suggested name:

- `execution-service`

## Event Flow

### Stage 1: Ingestion

1. Browser collector posts a raw options-flow row to `collector-ingestion-service`.
2. The service stores:
   - raw event
   - normalized event
   - outbox record `OptionsFlowCaptured`
3. The outbox publisher sends that event to the broker.

### Stage 2: Enrichment

1. `market-enrichment-service` consumes `OptionsFlowCaptured`.
2. It fetches external data for the symbol and contract.
3. It stores the enrichment payload.
4. It creates an outbox record `OptionsFlowEnriched`.
5. The publisher sends that event to the broker.

### Stage 3: Signal Evaluation

1. `signal-evaluation-service` consumes `OptionsFlowEnriched`.
2. It computes trade features and scoring.
3. It stores a signal evaluation result.
4. If actionable, it emits `TradeCandidateCreated`.

### Stage 4: Execution

Later:

1. `execution-service` consumes `TradeCandidateCreated`.
2. It applies risk and account checks.
3. It submits or rejects the order.

## Queue Plan

Use explicit queues for each stage.

Suggested logical names:

- `options-flow.captured`
- `options-flow.enrichment.request`
- `options-flow.enrichment.completed`
- `trade-signal.evaluate`
- `trade-candidate.created`
- `trade-execution.request`
- `dead-letter.options-flow`
- `dead-letter.enrichment`
- `dead-letter.signal`

In practice, we may use Artemis addresses with bound queues, but the above names are the logical contract.

## Message Contracts

Every message should include:

- `eventId`
- `eventType`
- `eventVersion`
- `occurredAtUtc`
- `sourceSessionId`
- `normalizedEventId`
- `symbol`
- `idempotencyKey`

### OptionsFlowCaptured

Fields:

- normalized event ID
- raw event ID
- symbol
- expiry
- strike
- put/call
- premium
- event timestamp

### OptionsFlowEnriched

Fields:

- normalized event ID
- enrichment record ID
- symbol
- OHLC snapshot ID
- open interest snapshot ID
- other feature flags

### TradeCandidateCreated

Fields:

- signal evaluation ID
- symbol
- strategy name
- score
- action
- confidence
- risk snapshot ID

## Data Storage Plan

### Keep

- raw browser events
- normalized options-flow events
- enrichment results
- signal evaluations
- trade candidates
- order lifecycle records later
- outbox events
- processing error records

### Move Toward

Use PostgreSQL as the production database.

The current SQLite database is acceptable for local validation and early development only.

## Reliability Requirements

### Use Transactional Outbox

Do not:

- write to the database
- then separately publish to the broker in a second non-transactional step

Do:

- write domain rows and outbox rows in one database transaction
- publish asynchronously from the outbox

### Use Idempotent Consumers

Each consumer must tolerate duplicate deliveries.

Mechanisms:

- idempotency key on each message
- processed-message table or unique constraint
- upsert rather than blind insert where appropriate

### Use Dead-Letter Queues

If enrichment or signal evaluation fails repeatedly:

- route the message to a dead-letter queue
- store failure details in the database
- do not block the whole pipeline

### Preserve Stage Boundaries

Do not let the ingestion service fetch all external market data inline.

Reason:

- it increases latency
- it couples browser ingestion to vendor uptime
- it makes retries and isolation worse

## Immediate Planning Sequence

### Step 1

Replace the current Python ingestion service with a Spring Boot ingestion service while keeping the existing browser extension.

### Step 2

Move the durable database target to PostgreSQL.

### Step 3

Add an outbox table and a Spring Boot publisher to ActiveMQ Artemis.

### Step 4

Implement the first enrichment worker for one outside data source.

Recommended first enrichment scope:

- OHLC
- open interest

### Step 5

Implement the first signal evaluation worker and persist candidate trade decisions.

## Near-Term Module Layout

Suggested repo direction once we start the Java migration:

- `collector-ingestion-service`
- `event-publisher-service`
- `market-enrichment-service`
- `signal-evaluation-service`
- `shared-domain`
- `shared-messaging`

## Questions To Resolve Next

- Which external providers will supply OHLC, open interest, and later Level 2 or gamma data?
- How fast does enrichment need to complete for a signal to remain actionable?
- Do we evaluate one event at a time or aggregate short rolling windows per symbol?
- What rules define an actionable trade candidate in the first version?
- When do we move from local PostgreSQL to a hosted deployment?

## Recommended Next Deliverable

The next concrete build step should be a Spring Boot `collector-ingestion-service` design doc and scaffold, with:

- PostgreSQL schema
- outbox schema
- Artemis message contracts
- REST endpoint for browser collector ingest
