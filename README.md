# CheddarFlow automation helpers

This repo contains scripts to automate exporting the CheddarFlow options feed to CSV.

## Prerequisites

* Node.js 18+ installed on the Windows mini PC.
* Git to clone this repository.
* An existing CheddarFlow account/session. The script reuses a persistent Chromium profile so you can sign in once, then let it run daily.

Install dependencies after cloning:

```powershell
npm install
```

## Running the exporter manually

The script opens Chromium, waits for the options flow grid to load, injects `optionsMutator.js`, and saves the generated CSV into `automation/downloads` by default.

```powershell
set CHEDDARFLOW_URL=https://app.cheddarflow.com/
set DOWNLOAD_DIR=C:\\Path\\To\\CheddarFlow\\automation\\downloads
set USER_DATA_DIR=C:\\Path\\To\\CheddarFlow\\automation\\user-data
node automation/options_flow_download.js
```

Environment variables are optional:

* `CHEDDARFLOW_URL` – page that hosts the options grid (defaults to `https://app.cheddarflow.com/`).
* `DOWNLOAD_DIR` – where to save the CSV (defaults to `automation/downloads`).
* `USER_DATA_DIR` – Chromium profile to keep you signed in between runs (defaults to `automation/user-data`).
* `HEADLESS` – set to `false` to watch the browser; defaults to headless mode.
* `HEADFUL` – any value forces headed mode (overrides `HEADLESS`).

If you need to sign in, set `HEADLESS=false` once so you can complete authentication; subsequent runs can be headless.

## Testing the automation

You can do a quick test run without waiting for the scheduled task:

1. Open PowerShell inside the cloned repo.
2. (Optional) clear out any previous downloads so you know the test produced a fresh file:
   ```powershell
   Remove-Item automation\downloads\* -ErrorAction SilentlyContinue
   ```
3. Run the exporter with headless mode disabled so you can watch it operate and sign in if needed:
   ```powershell
   set CHEDDARFLOW_URL=https://app.cheddarflow.com/
   set DOWNLOAD_DIR=%CD%\automation\downloads
   set USER_DATA_DIR=%CD%\automation\user-data
   set HEADLESS=false  # or: set HEADFUL=true
   node automation/options_flow_download.js
   ```
4. After the browser closes, confirm a new CSV exists in `automation\downloads` (file name starts with `cheddarflow_options_`).
5. Re-run with `HEADLESS=true` (or unset) to ensure unattended mode works:
   ```powershell
   set HEADLESS=true
   node automation/options_flow_download.js
   ```

If any errors occur, rerun with `HEADLESS=false` to observe the flow. You can also set `DEBUG=puppeteer:*` to print Puppeteer
logs for deeper troubleshooting.

## Scheduling on Windows

Use the provided PowerShell helper to register a daily Task Scheduler job. Update the repo path to match your clone location and adjust the time if needed.

```powershell
# From an elevated PowerShell session
powershell -ExecutionPolicy Bypass -File automation/CreateCheddarFlowTask.ps1 -RepoPath "C:\\Path\\To\\CheddarFlow" -Time "07:00"
```

The task runs `node automation/options_flow_download.js` each morning. Downloads are placed under `automation/downloads` unless overridden via `DOWNLOAD_DIR`.

To change the run time later, re-run the command with a new `-Time` value; it overwrites the existing task.
