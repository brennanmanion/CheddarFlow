# Validation Workflow

This folder contains a live-validation path for the current scripts without editing the original parser files first.

## Goal

Prove that:

- a real logged-in browser session can reach the CheddarFlow page
- the current JavaScript snippets can capture live page data
- the current Python parsers can process the captured files

## Important Caveat

For the most reliable run, close normal Google Chrome before starting the Playwright session against your existing profile. Otherwise Chrome profile locking can interfere with startup.

If the site presents a human-verification challenge when launched under Playwright, do not try to automate around it. In that case, use the manual browser workflow below.

## Manual Browser Workflow

Use this when CheddarFlow blocks automated login or automated browser startup.

1. Open normal Google Chrome.
2. Log in to CheddarFlow manually.
3. Navigate to the options flow page you want to validate.
4. Open DevTools Console.
5. Paste only the observer setup portion of `optionsMutator.js` first.
6. Let it run for 30 to 60 seconds while live events arrive.
7. Paste the raw HTML export block to trigger the download.
8. Move or rename the downloaded file to `validation-output/options_my_data.txt`.
9. Run the existing parser through the wrapper command below.

For the current file, the intended split is:

- observer setup: lines 1 through 22 in [optionsMutator.js](/Users/brennan/Documents/CheddarFlow/optionsMutator.js:1)
- raw HTML export source: based on the commented block at [optionsMutator.js](/Users/brennan/Documents/CheddarFlow/optionsMutator.js:133)

Use this export block in the console after the observer has been running:

```js
const delimiter = "<!--ENDOFITEM-->";
let htmlContents = items.map(element => element.innerHTML + delimiter);

const blob = new Blob(htmlContents, { type: "text/plain;charset=utf-8;" });
const url = URL.createObjectURL(blob);

const link = document.createElement("a");
link.setAttribute("href", url);
link.setAttribute("download", "my_data.txt");
document.body.appendChild(link);

link.click();
document.body.removeChild(link);
```

If the setup paste fails because the selector does not exist, that directly validates the selector-brittleness finding and tells us the live page DOM no longer matches the script.

## Capture Commands

Run these from the repo root.

### Options Flow Raw Export

Use the current `optionsMutator.js` observer setup, keep it alive for a chosen interval, then export raw HTML in the format expected by `cheddarflow_script2.py`:

```bash
node validation/playwright_capture.mjs capture options \
  --url "PASTE_THE_OPTIONS_FLOW_URL_HERE" \
  --ready-selector "ul" \
  --interactive \
  --wait-ms 30000 \
  --output "validation-output/options_my_data.txt"
```

### Dark Pool Raw HTML Export

Use the live dark pool observer from `darkPoolsScript.js`, then export row HTML in the format expected by `dark_pool_script.py`:

```bash
node validation/playwright_capture.mjs capture darkpool-html \
  --url "PASTE_THE_DARK_POOL_URL_HERE" \
  --ready-selector ".ag-center-cols-container" \
  --interactive \
  --wait-ms 30000 \
  --output "validation-output/darkpool_my_data.txt"
```

### AG Grid Container Snapshot

Capture the full AG Grid container HTML for `cheddarflow_script.py`:

```bash
node validation/playwright_capture.mjs capture ag-container \
  --url "PASTE_THE_OPTIONS_FLOW_URL_HERE" \
  --ready-selector ".ag-center-cols-container" \
  --interactive \
  --output "validation-output/ag-center-cols-container.html"
```

## Parser Commands

Run the existing parser logic against the captured files without editing the original scripts:

```bash
python3 validation/run_existing_parser.py \
  cheddarflow_script2.py \
  validation-output/options_my_data.txt \
  validation-output/cheddarflow_script2.csv
```

```bash
python3 validation/run_existing_parser.py \
  dark_pool_script.py \
  validation-output/darkpool_my_data.txt \
  validation-output/dark_pool_script.csv
```

```bash
python3 validation/run_existing_parser.py \
  cheddarflow_script.py \
  validation-output/ag-center-cols-container.html \
  validation-output/cheddarflow_script.csv
```

## Suggested Validation Order

1. Run the options flow capture.
2. Run `cheddarflow_script2.py` on that capture.
3. Run the dark pool capture.
4. Run `dark_pool_script.py` on that capture.
5. Capture the full AG Grid container.
6. Run `cheddarflow_script.py` on that capture.

If the raw capture looks wrong, stop there. Fix the collector first before touching the parser.
