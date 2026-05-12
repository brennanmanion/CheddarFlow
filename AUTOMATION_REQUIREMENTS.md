# CheddarFlow Automation Requirements

## Purpose

Define the requirements for turning the current CheddarFlow snapshot scripts into a repeatable browser automation that:

- launches before the U.S. market opens
- reuses a saved authenticated browser session
- collects intraday data until market close
- writes collected data to durable storage
- enriches the dataset with end-of-day and auxiliary market data

## Current Repo Assessment

The current repository contains prototype extraction scripts, not a working browser automation system.

- `optionsMutator.js` and `darkPoolsScript.js` are console snippets meant to run in an already-open browser tab.
- `cheddarflow_script.py`, `cheddarflow_script2.py`, and `dark_pool_script.py` parse previously saved HTML snapshots from hard-coded files in `~/Downloads`.
- There is no scheduler, browser launcher, login workflow, session validation, retry logic, health monitoring, or database integration.
- There are no tests, fixtures, or stable DOM contracts.

## Immediate Risks In The Current Approach

- DOM selectors are brittle and depend on generated CSS classes or dynamic element IDs.
- The browser scripts export immediately instead of running as long-lived collectors.
- Several scripts silently swallow parsing errors, making data loss invisible.
- Data is written to ad hoc CSV outputs instead of an append-only store with deduplication.
- The current Python scripts are tied to local files and cannot validate a live browser session.

## Recommended Architecture

Build the system as two cooperating jobs.

### 1. Intraday Collector

Responsible for browser startup, session reuse, live capture, and persistence during market hours.

Recommended implementation:

- Use Playwright with a persistent browser profile.
- Run the browser in headed mode for the first setup and optional later diagnostics.
- Reuse a dedicated Chrome or Chromium user data directory instead of trying to read credentials directly from the OS password store.
- Prefer intercepting network responses or WebSocket payloads over scraping rendered DOM rows.
- Persist both raw payloads and normalized records.

### 2. End-Of-Day Enrichment Job

Responsible for data that is not reliably available from the live CheddarFlow UI or that is better sourced elsewhere.

- OHLC bars
- open interest snapshots
- gamma wall inputs or derived outputs
- any additional reference data such as symbol metadata or trading calendars

## Scheduling Requirements

The automation must use a market calendar, not only a wall-clock schedule.

- Start the browser 15 to 30 minutes before regular market open.
- On May 6, 2026, while Denver is on MDT, regular U.S. equity market open is 7:30 AM MDT and close is 2:00 PM MDT.
- The scheduler must handle market holidays and half-days.
- The collector must stop gracefully at market close and trigger end-of-day jobs after intraday capture finishes.

## Authentication Requirements

- Use a dedicated persistent browser profile for this automation.
- Complete first-time login manually in that profile.
- On later runs, the automation should reuse existing cookies and local storage.
- If the session expires or a 2FA challenge appears, the run should pause and surface an alert instead of repeatedly failing login attempts.
- Do not hard-code credentials in the repo.

## Collection Requirements

### Options Flow

- Capture each event with a stable event timestamp, symbol, expiry, strike, option side, premium, size, spot price, volume, open interest, trade side, and any condition flags.
- Store raw payloads when available so parsers can be improved later without replaying the browser session.
- Deduplicate events using a source event ID when available, otherwise use a deterministic hash.

### Dark Pool Data

- Capture time, symbol, size, price or spot, notional amount, and any source-side metadata available in the payload.
- Record the ingestion timestamp separately from the source timestamp.

### OHLC Data

- Pull OHLC bars from a defined source.
- At minimum, support daily bars.
- If intraday analysis is required, define the bar interval explicitly, such as 1 minute or 5 minute.

### Open Interest

- Treat open interest as end-of-day data unless a reliable vendor source provides fresher values.
- Record the effective market date of the snapshot.

### Level 2 Data

- Do not rely on browser DOM scraping for Level 2.
- Use a dedicated market data source or direct feed if Level 2 is in scope.
- Define symbol coverage, depth, and retention limits before implementation.

### Gamma Walls

- Define whether gamma walls come from a vendor endpoint or are computed internally.
- If computed internally, define the formula, input data, aggregation windows, and symbol universe.

## Storage Requirements

Recommended default: PostgreSQL.

Reasoning:

- better fit than ad hoc CSV files for long-running collection
- supports deduplication, indexing, and later analytics
- handles multiple datasets and job metadata cleanly

Suggested tables:

- `collector_runs`
- `raw_events`
- `options_flow_events`
- `dark_pool_events`
- `ohlc_bars`
- `open_interest_snapshots`
- `level2_snapshots`
- `gamma_wall_snapshots`
- `job_heartbeats`
- `ingestion_errors`

For a short MVP, SQLite is acceptable only if:

- the collector runs on a single machine
- Level 2 volume is out of scope
- write concurrency remains low

## Reliability Requirements

- Detect whether the source page is still receiving updates.
- Restart the page or browser if no data arrives for a configured threshold during market hours.
- Log navigation errors, auth failures, parse failures, and write failures.
- Maintain a heartbeat row for each active run.
- Support resume after crash without duplicating already saved records.

## Browser Validation Requirements

Before calling the system production-ready, validate the following in a real browser session:

1. The automation launches the browser using the dedicated persistent profile.
2. The automation lands on the correct CheddarFlow page and confirms authenticated state.
3. At least one live event is captured and written to storage.
4. The collector continues running for a sustained interval without manual interaction.
5. A forced restart resumes collection without corrupting or duplicating data.
6. End-of-day enrichment completes and links data back to the same market date.

## Non-Functional Requirements

- Timestamps must be stored in UTC, with source market date preserved separately.
- The system must separate raw capture from downstream aggregation.
- Parsers must fail loudly enough to make data quality issues visible.
- Configuration must be externalized for URLs, schedules, symbols, and storage settings.
- The implementation must include fixtures or recorded payload samples for parser tests.

## Proposed Implementation Phases

### Phase 1: Proof Of Capture

- Build a Playwright runner with persistent profile support.
- Navigate to the target CheddarFlow page.
- Capture and save raw network payloads or DOM-derived records locally.
- Prove end-to-end storage for one data stream.

### Phase 2: Durable Intraday Collector

- Add scheduler, heartbeat, restart handling, deduplication, and logging.
- Move storage into PostgreSQL.
- Add parser tests using recorded samples.

### Phase 3: Multi-Dataset Enrichment

- Add OHLC ingestion.
- Add open interest snapshots.
- Define and implement gamma wall sourcing or computation.
- Add Level 2 only after confirming source access and storage volume expectations.

## Open Questions

- Which CheddarFlow pages are in scope first: options flow, dark pool, both, or more?
- Is network interception allowed and preferred over DOM scraping for this use case?
- What database do you want for the first production version: local PostgreSQL, hosted PostgreSQL, or SQLite MVP?
- What exact OHLC intervals are needed?
- What provider will supply Level 2 and open interest if CheddarFlow does not expose them reliably?
- Should gamma walls be sourced from a vendor or computed from chain data?

## Recommendation

Treat the existing files as parsing prototypes only.

The next implementation step should be a new Playwright-based collector that validates a real logged-in browser session and saves raw events into a database-backed pipeline. Once that capture path is stable, layer on OHLC, open interest, gamma walls, and any Level 2 feed integration.
