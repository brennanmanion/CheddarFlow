# Phase 1 Chrome Extension

This extension attaches to a normal logged-in CheddarFlow page and streams options flow row HTML to the local ingestion service.

## What It Does

- waits for an AG Grid container on a CheddarFlow page
- captures visible rows on startup
- watches for newly added rows and in-place row changes
- performs periodic rescans to catch AG Grid row reuse
- serializes each row immediately
- deduplicates rows using semantic cell content instead of raw style-heavy HTML
- posts raw events to `http://127.0.0.1:8787/api/options-flow/raw`
- posts heartbeats to `http://127.0.0.1:8787/api/heartbeats`
- marks the feed stale if no new captures arrive after attach
- shows a small in-page status panel so you can tell whether the collector is attached

## Load It In Chrome

1. Open `chrome://extensions`
2. Enable `Developer mode`
3. Click `Load unpacked`
4. Select [extension](/Users/brennan/Documents/CheddarFlow/extension)

## Operator Workflow

1. Start the local ingestion service first.
2. Open normal Chrome.
3. Log in to CheddarFlow manually.
4. Navigate to the options flow page.
5. Confirm the in-page status panel says `collector attached`.
6. Check the service health endpoint and database.

## Notes

- The extension intentionally does not automate login.
- The collector currently targets AG Grid style rows because that matches the existing repo scripts.
- If the target selector changes, the status panel will stay in the waiting state and heartbeats will show that attachment never completed.
- If the panel changes to `collector stale`, the browser is attached but no fresh captures have arrived within the stale threshold.
