#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { chromium } from "playwright";

const CHROME_EXECUTABLE =
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const DEFAULT_USER_DATA_DIR = path.join(
  os.homedir(),
  "Library/Application Support/Google/Chrome",
);
const DEFAULT_PROFILE_DIRECTORY = "Default";
const DEFAULT_WAIT_MS = 30000;

function usage() {
  console.log(`Usage:
  node validation/playwright_capture.mjs capture options --output <file> [--url <url>] [--wait-ms 30000] [--ready-selector <selector>] [--interactive] [--profile-dir Default] [--user-data-dir <dir>]
  node validation/playwright_capture.mjs capture darkpool-html --output <file> [--url <url>] [--wait-ms 30000] [--ready-selector <selector>] [--interactive] [--profile-dir Default] [--user-data-dir <dir>]
  node validation/playwright_capture.mjs capture darkpool-csv --output <file> [--url <url>] [--wait-ms 30000] [--ready-selector <selector>] [--interactive] [--profile-dir Default] [--user-data-dir <dir>]
  node validation/playwright_capture.mjs capture ag-container --output <file> [--url <url>] [--ready-selector <selector>] [--interactive] [--profile-dir Default] [--user-data-dir <dir>]
`);
}

function parseArgs(argv) {
  const args = { _: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (token.startsWith("--")) {
      const key = token.slice(2);
      const value = argv[i + 1];
      if (value === undefined || value.startsWith("--")) {
        args[key] = true;
      } else {
        args[key] = value;
        i += 1;
      }
    } else {
      args._.push(token);
    }
  }
  return args;
}

function splitScript(source, marker) {
  const index = source.indexOf(marker);
  if (index === -1) {
    throw new Error(`Marker "${marker}" not found`);
  }
  return {
    before: source.slice(0, index),
    after: source.slice(index + marker.length),
  };
}

async function ensureParentDir(filePath) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
}

async function waitForEnter(promptText) {
  process.stdout.write(`${promptText}\n`);

  await new Promise((resolve) => {
    process.stdin.setEncoding("utf8");
    process.stdin.resume();
    process.stdin.once("data", () => {
      process.stdin.pause();
      resolve();
    });
  });
}

async function getPage(context, url) {
  const existingPages = context.pages();
  const page = existingPages[0] || (await context.newPage());
  if (url) {
    await page.goto(url, { waitUntil: "domcontentloaded" });
  }
  await page.bringToFront();
  return page;
}

async function waitForReady(page, readySelector) {
  if (readySelector) {
    await page.locator(readySelector).waitFor({ state: "visible", timeout: 60000 });
  }
}

async function runPageScript(page, source) {
  await page.evaluate((scriptSource) => {
    const runner = new Function(scriptSource);
    runner();
  }, source);
}

async function saveDownload(download, outputPath) {
  await ensureParentDir(outputPath);
  await download.saveAs(outputPath);
  return outputPath;
}

async function captureScenario({ mode, page, repoRoot, waitMs, outputPath }) {
  switch (mode) {
    case "options": {
      const scriptText = await fs.readFile(path.join(repoRoot, "optionsMutator.js"), "utf8");
      const { before } = splitScript(scriptText, "// P2");
      await runPageScript(page, before);
      await page.waitForTimeout(waitMs);
      const rawExportSource = `
        const delimiter = "<!--ENDOFITEM-->";
        let htmlContents = items.map(element => element.innerHTML + delimiter);
        const blob = new Blob(htmlContents, { type: 'text/plain;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.setAttribute("href", url);
        link.setAttribute("download", "my_data.txt");
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      `;
      const downloadPromise = page.waitForEvent("download");
      await runPageScript(page, rawExportSource);
      const download = await downloadPromise;
      await saveDownload(download, outputPath);
      return download.suggestedFilename();
    }
    case "darkpool-html": {
      const scriptText = await fs.readFile(path.join(repoRoot, "darkPoolsScript.js"), "utf8");
      const { before } = splitScript(scriptText, "// P2");
      await runPageScript(page, before);
      await page.waitForTimeout(waitMs);
      const exportHtmlSource = `
        const delimiter = "<!--ENDOFITEM-->";
        const htmlContents = items.map((element) => element.innerHTML + delimiter);
        const blob = new Blob(htmlContents, { type: "text/plain;charset=utf-8;" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.setAttribute("href", url);
        link.setAttribute("download", "darkpool_my_data.txt");
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      `;
      const downloadPromise = page.waitForEvent("download");
      await runPageScript(page, exportHtmlSource);
      const download = await downloadPromise;
      await saveDownload(download, outputPath);
      return download.suggestedFilename();
    }
    case "darkpool-csv": {
      const scriptText = await fs.readFile(path.join(repoRoot, "darkPoolsScript.js"), "utf8");
      const { before, after } = splitScript(scriptText, "// P2");
      await runPageScript(page, before);
      await page.waitForTimeout(waitMs);
      const downloadPromise = page.waitForEvent("download");
      await runPageScript(page, after);
      const download = await downloadPromise;
      await saveDownload(download, outputPath);
      return download.suggestedFilename();
    }
    case "ag-container": {
      const html = await page.locator(".ag-center-cols-container").evaluate((element) => {
        return element.outerHTML;
      });
      await ensureParentDir(outputPath);
      await fs.writeFile(outputPath, html, "utf8");
      return path.basename(outputPath);
    }
    default:
      throw new Error(`Unsupported mode: ${mode}`);
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const [command, mode] = args._;

  if (!command || command === "help" || command === "--help") {
    usage();
    process.exitCode = 1;
    return;
  }

  if (command !== "capture" || !mode) {
    usage();
    process.exitCode = 1;
    return;
  }

  const outputPath = args.output;
  if (!outputPath) {
    throw new Error("--output is required");
  }

  const userDataDir = args["user-data-dir"] || DEFAULT_USER_DATA_DIR;
  const profileDirectory = args["profile-dir"] || DEFAULT_PROFILE_DIRECTORY;
  const waitMs = Number(args["wait-ms"] || DEFAULT_WAIT_MS);
  const repoRoot = process.cwd();

  const context = await chromium.launchPersistentContext(userDataDir, {
    headless: false,
    acceptDownloads: true,
    executablePath: CHROME_EXECUTABLE,
    args: [`--profile-directory=${profileDirectory}`],
    viewport: null,
  });

  try {
    const page = await getPage(context, args.url);
    if (args.interactive) {
      await waitForEnter(
        "Browser is open. Navigate/login as needed, then press Enter here to start capture.",
      );
    }
    await waitForReady(page, args["ready-selector"]);
    const suggestedName = await captureScenario({
      mode,
      page,
      repoRoot,
      waitMs: Number.isNaN(waitMs) ? DEFAULT_WAIT_MS : waitMs,
      outputPath,
    });
    console.log(`Captured ${suggestedName} to ${outputPath}`);
  } finally {
    await context.close();
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
