#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";

const DEFAULT_PORT = 9222;
const DEFAULT_WAIT_MS = 30000;

function usage() {
  console.log(`Usage:
  node validation/live_capture.mjs list [--port 9222]
  node validation/live_capture.mjs capture options --match <text> --output <file> [--wait-ms 30000] [--port 9222]
  node validation/live_capture.mjs capture darkpool-html --match <text> --output <file> [--wait-ms 30000] [--port 9222]
  node validation/live_capture.mjs capture darkpool-csv --match <text> --output <file> [--wait-ms 30000] [--port 9222]
  node validation/live_capture.mjs capture ag-container --match <text> --output <file> [--port 9222]
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

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Request failed ${response.status} for ${url}`);
  }
  return response.json();
}

class CdpClient {
  constructor(webSocketUrl) {
    this.webSocketUrl = webSocketUrl;
    this.nextId = 1;
    this.pending = new Map();
    this.openPromise = null;
    this.socket = null;
  }

  async connect() {
    if (this.openPromise) {
      return this.openPromise;
    }
    this.openPromise = new Promise((resolve, reject) => {
      const socket = new WebSocket(this.webSocketUrl);
      this.socket = socket;

      socket.addEventListener("open", () => resolve());
      socket.addEventListener("error", (event) => {
        reject(new Error(`WebSocket error: ${event.type}`));
      });
      socket.addEventListener("message", (event) => {
        const message = JSON.parse(event.data);
        if (!Object.prototype.hasOwnProperty.call(message, "id")) {
          return;
        }
        const pending = this.pending.get(message.id);
        if (!pending) {
          return;
        }
        this.pending.delete(message.id);
        if (message.error) {
          pending.reject(new Error(message.error.message));
          return;
        }
        pending.resolve(message.result);
      });
      socket.addEventListener("close", () => {
        for (const pending of this.pending.values()) {
          pending.reject(new Error("CDP socket closed"));
        }
        this.pending.clear();
      });
    });

    return this.openPromise;
  }

  async send(method, params = {}) {
    await this.connect();
    const id = this.nextId;
    this.nextId += 1;
    const payload = JSON.stringify({ id, method, params });
    const response = new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
    });
    this.socket.send(payload);
    return response;
  }

  async evaluate(expression) {
    const result = await this.send("Runtime.evaluate", {
      expression,
      awaitPromise: true,
      returnByValue: true,
    });
    if (result.exceptionDetails) {
      const message = result.exceptionDetails.text || "Runtime evaluation failed";
      throw new Error(message);
    }
    return result.result?.value;
  }

  async close() {
    if (!this.socket) {
      return;
    }
    this.socket.close();
    this.socket = null;
  }
}

function toEval(scriptBody) {
  return `(() => {
${scriptBody}
return true;
})()`;
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

async function sleep(ms) {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitFor(client, predicateExpression, timeoutMs, pollMs = 250) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = await client.evaluate(predicateExpression);
    if (value) {
      return true;
    }
    await sleep(pollMs);
  }
  return false;
}

function installDownloadTrapExpression() {
  return `(() => {
    if (window.__codexDownloadTrapInstalled) {
      return true;
    }
    window.__codexCapturedDownloads = [];
    window.__codexCaptureErrors = [];
    const originalClick = HTMLAnchorElement.prototype.click;

    HTMLAnchorElement.prototype.click = function patchedClick(...args) {
      try {
        const href = this.getAttribute("href") || this.href || "";
        const download = this.getAttribute("download") || this.download || "";
        if (href.startsWith("blob:")) {
          fetch(href)
            .then((response) => response.text())
            .then((text) => {
              window.__codexCapturedDownloads.push({
                download,
                href,
                text,
                capturedAt: new Date().toISOString(),
              });
            })
            .catch((error) => {
              window.__codexCaptureErrors.push(String(error));
            });
        }
      } catch (error) {
        window.__codexCaptureErrors.push(String(error));
      }
      return originalClick.apply(this, args);
    };

    window.__codexDownloadTrapInstalled = true;
    return true;
  })()`;
}

function listTargets(targets) {
  const pages = targets.filter((target) => target.type === "page");
  if (pages.length === 0) {
    console.log("No debuggable page targets found.");
    return;
  }

  for (const target of pages) {
    console.log(`- id=${target.id}`);
    console.log(`  title=${target.title}`);
    console.log(`  url=${target.url}`);
  }
}

function pickTarget(targets, matchText) {
  const pages = targets.filter((target) => target.type === "page");
  if (pages.length === 0) {
    throw new Error("No page targets found.");
  }

  if (!matchText) {
    if (pages.length !== 1) {
      const summary = pages
        .map((target) => `"${target.title}" <${target.url}>`)
        .join(", ");
      throw new Error(`Multiple page targets found. Pass --match. Targets: ${summary}`);
    }
    return pages[0];
  }

  const lowered = matchText.toLowerCase();
  const matches = pages.filter((target) => {
    return (
      target.title.toLowerCase().includes(lowered) ||
      target.url.toLowerCase().includes(lowered)
    );
  });

  if (matches.length === 0) {
    throw new Error(`No page matched "${matchText}".`);
  }

  if (matches.length > 1) {
    const summary = matches
      .map((target) => `"${target.title}" <${target.url}>`)
      .join(", ");
    throw new Error(`Multiple page targets matched "${matchText}": ${summary}`);
  }

  return matches[0];
}

async function ensureParentDir(filePath) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
}

function buildScenario(mode, repoRoot) {
  switch (mode) {
    case "options":
      return {
        scriptPath: path.join(repoRoot, "optionsMutator.js"),
        marker: "// P2",
        waitMsDefault: DEFAULT_WAIT_MS,
        async run(client, scriptText, waitMs) {
          const { before, after } = splitScript(scriptText, this.marker);
          await client.evaluate(installDownloadTrapExpression());
          await client.evaluate(toEval(before));
          await sleep(waitMs);
          await client.evaluate(toEval(after));
          const downloadReady = await waitFor(
            client,
            "window.__codexCapturedDownloads.length > 0",
            5000,
          );
          if (!downloadReady) {
            throw new Error("No captured download found after running optionsMutator.js");
          }
          return client.evaluate("window.__codexCapturedDownloads.at(-1)");
        },
      };
    case "darkpool-html":
      return {
        scriptPath: path.join(repoRoot, "darkPoolsScript.js"),
        marker: "// P2",
        waitMsDefault: DEFAULT_WAIT_MS,
        async run(client, scriptText, waitMs) {
          const { before } = splitScript(scriptText, this.marker);
          await client.evaluate(installDownloadTrapExpression());
          await client.evaluate(toEval(before));
          await sleep(waitMs);
          await client.evaluate(`(() => {
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
            return items.length;
          })()`);
          const downloadReady = await waitFor(
            client,
            "window.__codexCapturedDownloads.length > 0",
            5000,
          );
          if (!downloadReady) {
            throw new Error("No captured download found after dark pool HTML export");
          }
          return client.evaluate("window.__codexCapturedDownloads.at(-1)");
        },
      };
    case "darkpool-csv":
      return {
        scriptPath: path.join(repoRoot, "darkPoolsScript.js"),
        marker: "// P2",
        waitMsDefault: DEFAULT_WAIT_MS,
        async run(client, scriptText, waitMs) {
          const { before, after } = splitScript(scriptText, this.marker);
          await client.evaluate(installDownloadTrapExpression());
          await client.evaluate(toEval(before));
          await sleep(waitMs);
          await client.evaluate(toEval(after));
          const downloadReady = await waitFor(
            client,
            "window.__codexCapturedDownloads.length > 0",
            5000,
          );
          if (!downloadReady) {
            throw new Error("No captured download found after running darkPoolsScript.js");
          }
          return client.evaluate("window.__codexCapturedDownloads.at(-1)");
        },
      };
    case "ag-container":
      return {
        waitMsDefault: 0,
        async run(client) {
          const text = await client.evaluate(`(() => {
            const container = document.querySelector(".ag-center-cols-container");
            return container ? container.outerHTML : null;
          })()`);
          if (!text) {
            throw new Error("Could not find .ag-center-cols-container in the current page.");
          }
          return {
            download: "ag-center-cols-container.html",
            text,
            capturedAt: new Date().toISOString(),
          };
        },
      };
    default:
      throw new Error(`Unsupported mode: ${mode}`);
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const [command, mode] = args._;
  const port = Number(args.port || DEFAULT_PORT);

  if (!command || command === "help" || command === "--help") {
    usage();
    process.exitCode = 1;
    return;
  }

  const targets = await fetchJson(`http://127.0.0.1:${port}/json/list`);

  if (command === "list") {
    listTargets(targets);
    return;
  }

  if (command !== "capture" || !mode) {
    usage();
    process.exitCode = 1;
    return;
  }

  const output = args.output;
  if (!output) {
    throw new Error("--output is required for capture mode.");
  }

  const waitMs = Number(args["wait-ms"] || DEFAULT_WAIT_MS);
  const repoRoot = process.cwd();
  const scenario = buildScenario(mode, repoRoot);
  const target = pickTarget(targets, args.match);
  const client = new CdpClient(target.webSocketDebuggerUrl);

  try {
    await client.connect();
    await client.send("Runtime.enable");
    await client.send("Page.enable");

    const scriptText = scenario.scriptPath
      ? await fs.readFile(scenario.scriptPath, "utf8")
      : "";
    const result = await scenario.run(
      client,
      scriptText,
      Number.isNaN(waitMs) ? scenario.waitMsDefault : waitMs,
    );

    await ensureParentDir(output);
    await fs.writeFile(output, result.text, "utf8");

    console.log(`Captured ${result.download || mode} from ${target.title}`);
    console.log(`Saved ${output}`);
  } finally {
    await client.close();
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
