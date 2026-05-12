# Browser Automation MVP

## Goal

Define the minimum viable browser automation for CheddarFlow data capture that is realistic given the current human-verification/login constraint and that can later support real-time trading decisions.

## Constraint

The site currently presents a human-verification challenge when the browser is launched under Playwright. That means the MVP should not depend on automated login or bot-evasion.

The correct MVP assumption is:

- login is manual
- data capture after login is automated
- the session remains active during market hours

## MVP Outcome

By the end of the MVP, the system should be able to:

1. detect that a logged-in CheddarFlow browser tab is open
2. attach a collector to the correct page
3. capture new flow events continuously during the session
4. persist raw events immediately to local storage
5. normalize events into a queryable store
6. expose collector health and basic data-quality metrics

This MVP does not place trades.

## Why This Scope

If the end goal is real-time trading, the first non-negotiable requirement is trustworthy ingestion. Right now the critical gap is not signal generation or order routing; it is proving that the feed can be captured continuously, loss-detected, and stored without silent corruption.

## Recommended MVP Architecture

### 1. Manual Session Bootstrap

You open normal Chrome manually before market open and complete login yourself.

Expected operator workflow:

- open Chrome
- log in to CheddarFlow
- open the target options flow page
- click a local start control or run a local command

### 2. Browser-Side Collector

The collector runs inside the already-authenticated browser context.

Best MVP implementation:

- Chrome extension with a content script on the CheddarFlow page

Why this is the best MVP:

- avoids automated browser startup during login
- runs in the real user session
- can observe DOM mutations continuously
- can post captured records to a local service
- is easier to keep resident than a DevTools console snippet

Fallback MVP implementation:

- a bookmarklet or manually injected content script

This is acceptable only for short validation, not for a production collector.

### 3. Local Ingestion Service

Run a local service on your machine that receives events from the browser collector and writes them to storage.

Responsibilities:

- receive events from the extension
- timestamp ingestion
- deduplicate repeated records
- write raw payloads and normalized events
- record heartbeats and parse errors

### 4. Storage

MVP storage recommendation:

- SQLite for first implementation

Reason:

- simpler than PostgreSQL for a single-machine MVP
- enough for browser validation and local analytics
- easy to inspect manually while debugging

Move to PostgreSQL when:

- the collector is stable
- multiple jobs are writing concurrently
- trading logic needs stronger operational guarantees

## Browser MVP Requirements

### Required

- detect whether the target CheddarFlow page is open
- confirm the collector is attached
- capture existing visible rows on startup
- capture new rows as they appear
- serialize each captured event immediately
- never store live DOM nodes as the source of truth
- write to storage incrementally, not only at end of session
- expose a heartbeat at least every 15 seconds
- expose a count of captured events
- expose a count of parse failures

### Not Required For MVP

- automated login
- full pre-market auto-start
- dark pool, open interest, OHLC, Level 2, gamma walls
- trading integration
- cloud deployment
- multi-user support

## Data Model For MVP

Store two forms of data.

### Raw Event

Fields:

- `id`
- `captured_at_utc`
- `source_page`
- `source_html`
- `source_hash`
- `session_id`

### Normalized Options Flow Event

Fields:

- `id`
- `raw_event_id`
- `event_time_text`
- `event_date_text`
- `symbol`
- `expiry`
- `strike`
- `put_call`
- `side`
- `buy_sell`
- `spot`
- `size`
- `price`
- `premium`
- `sweep_block_split`
- `volume`
- `open_interest`
- `conditions`
- `captured_at_utc`

## Recommended Collector Behavior

### Startup

1. Wait for the target page container to appear.
2. Capture all currently visible rows.
3. Start a `MutationObserver`.
4. Serialize newly added rows immediately.
5. Send each record to the local ingestion service.

### Health

Track:

- last event capture time
- total events captured
- total parse failures
- whether the target container still exists
- whether the page appears stalled

### Failure Handling

If the page stops updating:

- emit a visible warning
- continue heartbeat logging
- allow manual operator restart

If the DOM selector changes:

- log the missing selector clearly
- do not fail silently

## Anti-Goals

Do not do these in the MVP:

- hide Playwright or browser automation
- place trades from DOM-scraped data
- rely on one end-of-day CSV dump
- swallow parse errors
- treat browser DOM nodes as durable records

## Acceptance Criteria

The MVP is successful when all of these are true:

1. A manually logged-in Chrome session can start the collector.
2. The collector captures live options flow events for at least 30 minutes.
3. Raw events and normalized events are both written locally.
4. Event counts continue increasing while the feed is active.
5. Parse failures are visible in logs or metrics.
6. Restarting the collector does not corrupt the database.
7. At least one recorded capture can be replayed through the parser offline.

## Path To Trading

Before this feed is used for real-time trading decisions, add these gates:

### Gate 1: Capture Reliability

- no silent parse failures
- stable event counts
- clear recovery procedure

### Gate 2: Data Correctness

- validate symbol, expiry, strike, premium, and option side against sampled live records
- fix the known premium parsing bug first
- confirm no duplicate or mutated events from virtualization

### Gate 3: Latency Measurement

- measure time from page update to stored event
- measure gaps during high-volume periods

### Gate 4: Trading Isolation

- trading engine reads from the normalized store or a local event bus
- browser collector never places orders directly

### Gate 5: Risk Controls

- kill switch
- max position sizing rules
- max daily loss rules
- feed-staleness guard
- duplicate-signal guard

## Proposed Build Order

### Phase 1

- build the Chrome extension content script
- capture existing and new options rows
- write raw events to SQLite through a local service

### Phase 2

- normalize records into a second table
- add health endpoint, heartbeat, and logs
- add offline replay tests from recorded samples

### Phase 3

- validate a full market session
- measure latency and drop rate
- define the interface from collector to signal engine

## Recommendation

The browser automation MVP should be a human-started, extension-based collector attached to a normal authenticated Chrome session. That is the shortest credible path to a feed you can trust, and it avoids building the rest of the project on top of a login flow that the site already treats as suspicious.
