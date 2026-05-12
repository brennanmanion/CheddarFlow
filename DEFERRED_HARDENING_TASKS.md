# Deferred Hardening Tasks

## Purpose

Capture the next live-validation and reliability tasks that should be scheduled after the current Phase 1 collector work.

## Status

Deferred for later implementation and validation.

## Backlog

### 1. Page Refresh Resilience

Verify that the collector survives a manual page refresh and resumes capture without requiring a browser restart.

Acceptance criteria:

- after refresh, the collector returns to `collector attached`
- new heartbeats continue arriving
- raw and normalized counts continue increasing
- no duplicate explosion occurs from the refresh boundary

### 2. Hidden Tab / Visible Tab Behavior

Verify that the collector continues to behave correctly when the CheddarFlow tab is backgrounded and later brought back into focus.

Acceptance criteria:

- heartbeats continue while the tab is hidden
- queue depth does not grow without bound
- stale state is reported correctly if updates stop
- the collector resumes normal capture when the tab becomes visible again

### 3. Local Service Interruption And Recovery

Verify that the browser collector and local service recover cleanly when the ingestion service is stopped and restarted during market hours.

Acceptance criteria:

- the collector surfaces send failures clearly
- no silent data loss occurs
- after the service comes back, the collector resumes posting data
- error counts and timestamps make the outage window obvious

### 4. Long Soak Run

Run the collector for a materially longer live session to evaluate memory growth, duplicate behavior, stale detection, and stability under real market traffic.

Acceptance criteria:

- collector remains attached for the duration of the soak run
- raw and normalized counts continue advancing while the feed is active
- no new transaction or parser errors appear
- stale detection only appears when the source actually stops updating

### 5. Duplicate / Replay Validation

Inspect live samples around heavy-traffic periods and confirm that semantic deduplication is not collapsing legitimate repeated trades.

Acceptance criteria:

- repeated but distinct trades still land as separate records
- AG Grid row reuse does not overwrite prior captured events
- replay fixtures continue to pass after parser changes

## Recommendation

Track these as a follow-on hardening epic. The browser collector is now live and incrementally capturing data, but these tasks are needed before the feed should be treated as production-grade input for automated trade logic.
