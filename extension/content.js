(() => {
  if (window.__cheddarflowCollectorInstalled) {
    return;
  }
  window.__cheddarflowCollectorInstalled = true;

  const CONFIG = {
    collectorType: "options_flow",
    ingestUrl: "http://127.0.0.1:8787/api/options-flow/raw",
    heartbeatUrl: "http://127.0.0.1:8787/api/heartbeats",
    containerSelectors: [
      ".ag-center-cols-container",
      "[class*='ag-center-cols-container']"
    ],
    rowSelectors: [
      "div.ag-row",
      "[class~='ag-row']"
    ],
    attachRetryMs: 2000,
    flushIntervalMs: 1000,
    heartbeatIntervalMs: 15000,
    rescanIntervalMs: 5000,
    staleAfterMs: 45000,
    maxBatchSize: 25,
    maxStatelessHashes: 500
  };

  const state = {
    sessionId: crypto.randomUUID(),
    startedAtUtc: new Date().toISOString(),
    attached: false,
    attachAttempts: 0,
    container: null,
    containerSelector: null,
    lastUrl: window.location.href,
    pendingQueue: [],
    flushInProgress: false,
    eventCount: 0,
    duplicateCount: 0,
    parseFailureCount: 0,
    sendFailureCount: 0,
    lastCaptureAtUtc: null,
    lastHeartbeatAtUtc: null,
    lastSuccessfulSendAtUtc: null,
    lastError: null,
    observer: null,
    statusNode: null,
    lastHashByDomKey: new Map(),
    recentStatelessHashes: new Set(),
    recentStatelessHashOrder: []
  };

  function fnv1a(input) {
    let hash = 0x811c9dc5;
    for (let index = 0; index < input.length; index += 1) {
      hash ^= input.charCodeAt(index);
      hash +=
        (hash << 1) +
        (hash << 4) +
        (hash << 7) +
        (hash << 8) +
        (hash << 24);
    }
    return (hash >>> 0).toString(16).padStart(8, "0");
  }

  function collapseWhitespace(value) {
    return value.replace(/\s+/g, " ").trim();
  }

  function isRowElement(node) {
    return node instanceof Element && CONFIG.rowSelectors.some((selector) => node.matches(selector));
  }

  function findRowAncestor(node) {
    let current = node instanceof Element ? node : node.parentElement;
    while (current) {
      if (isRowElement(current)) {
        return current;
      }
      current = current.parentElement;
    }
    return null;
  }

  function normalizeCellText(cell) {
    const listItems = Array.from(cell.querySelectorAll("li"))
      .map((item) => collapseWhitespace(item.innerText || ""))
      .filter(Boolean);
    if (listItems.length > 0) {
      return listItems.join(" ");
    }

    const button = cell.querySelector("button");
    if (button) {
      return collapseWhitespace(button.innerText || "");
    }

    return collapseWhitespace(cell.innerText || "");
  }

  function buildRowSignature(row) {
    const cells = row.querySelectorAll("div[role='gridcell'][col-id]");
    if (cells.length === 0) {
      return collapseWhitespace(row.innerText || "");
    }

    const parts = [];
    cells.forEach((cell) => {
      const colId = cell.getAttribute("col-id") || "";
      parts.push(`${colId}=${normalizeCellText(cell)}`);
    });
    return parts.join("|");
  }

  function createStatusNode() {
    const node = document.createElement("div");
    node.id = "cheddarflow-phase1-status";
    node.style.position = "fixed";
    node.style.right = "12px";
    node.style.bottom = "12px";
    node.style.zIndex = "2147483647";
    node.style.maxWidth = "340px";
    node.style.padding = "10px 12px";
    node.style.borderRadius = "12px";
    node.style.background = "rgba(14, 23, 38, 0.92)";
    node.style.color = "#f8fafc";
    node.style.font = "12px/1.45 ui-monospace, SFMono-Regular, Menlo, monospace";
    node.style.boxShadow = "0 10px 30px rgba(0, 0, 0, 0.35)";
    node.style.whiteSpace = "pre-line";
    document.documentElement.appendChild(node);
    return node;
  }

  function getCaptureAgeMs() {
    if (!state.lastCaptureAtUtc) {
      return null;
    }
    const ageMs = Date.now() - Date.parse(state.lastCaptureAtUtc);
    return Number.isFinite(ageMs) ? Math.max(ageMs, 0) : null;
  }

  function isStale() {
    const ageMs = getCaptureAgeMs();
    return Boolean(state.attached && ageMs !== null && ageMs >= CONFIG.staleAfterMs);
  }

  function statusHeadline() {
    if (state.lastError) {
      return "collector error";
    }
    if (isStale()) {
      return "collector stale";
    }
    return state.attached ? "collector attached" : "collector waiting";
  }

  function updateStatus() {
    if (!state.statusNode || !document.documentElement.contains(state.statusNode)) {
      state.statusNode = createStatusNode();
    }

    const captureAgeMs = getCaptureAgeMs();
    const captureAgeSeconds = captureAgeMs === null ? "n/a" : (captureAgeMs / 1000).toFixed(1);
    const lines = [
      `CheddarFlow MVP: ${statusHeadline()}`,
      `session=${state.sessionId.slice(0, 8)}`,
      `events=${state.eventCount} queue=${state.pendingQueue.length} duplicates=${state.duplicateCount}`,
      `send_failures=${state.sendFailureCount} parse_failures=${state.parseFailureCount}`,
      `capture_age_s=${captureAgeSeconds}`,
      `last_capture=${state.lastCaptureAtUtc || "n/a"}`
    ];

    if (state.lastError) {
      lines.push(`last_error=${state.lastError}`);
    }

    state.statusNode.textContent = lines.join("\n");
    state.statusNode.style.background = state.lastError
      ? "rgba(120, 24, 24, 0.94)"
      : isStale()
        ? "rgba(146, 64, 14, 0.94)"
        : state.attached
          ? "rgba(14, 23, 38, 0.92)"
          : "rgba(88, 28, 135, 0.92)";
  }

  function rememberError(error) {
    state.lastError = String(error);
    updateStatus();
  }

  function clearError() {
    state.lastError = null;
  }

  async function postJson(url, payload) {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload),
      keepalive: true
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status} from ${url}`);
    }

    return response.json().catch(() => ({}));
  }

  function readDomKey(row) {
    return (
      row.getAttribute("row-id") ||
      row.getAttribute("aria-rowindex") ||
      row.getAttribute("data-row-id") ||
      row.id ||
      null
    );
  }

  function serializeRow(row, observedVia) {
    const html = row.innerHTML || "";
    const text = collapseWhitespace(row.innerText || "");
    const signature = buildRowSignature(row);
    if (!signature && !html.trim() && !text) {
      return null;
    }

    return {
      collector_type: CONFIG.collectorType,
      session_id: state.sessionId,
      page_url: window.location.href,
      page_title: document.title,
      source_selector: state.containerSelector,
      dom_key: readDomKey(row),
      source_html: html,
      source_text: text,
      row_signature: signature,
      observed_via: observedVia,
      captured_at_utc: new Date().toISOString()
    };
  }

  function collectRowsFromNode(node) {
    if (!(node instanceof Element)) {
      return [];
    }

    const rows = new Set();
    if (isRowElement(node)) {
      rows.add(node);
    }
    CONFIG.rowSelectors.forEach((selector) => {
      node.querySelectorAll(selector).forEach((row) => rows.add(row));
    });
    return Array.from(rows);
  }

  function rememberStatelessHash(rowHash) {
    if (state.recentStatelessHashes.has(rowHash)) {
      return false;
    }

    state.recentStatelessHashes.add(rowHash);
    state.recentStatelessHashOrder.push(rowHash);
    while (state.recentStatelessHashOrder.length > CONFIG.maxStatelessHashes) {
      const removed = state.recentStatelessHashOrder.shift();
      if (removed) {
        state.recentStatelessHashes.delete(removed);
      }
    }
    return true;
  }

  function enqueueRow(row, observedVia) {
    const payload = serializeRow(row, observedVia);
    if (!payload) {
      return;
    }

    const rowHash = fnv1a(`${payload.dom_key || "stateless"}|${payload.row_signature || payload.source_text}`);
    if (payload.dom_key) {
      if (state.lastHashByDomKey.get(payload.dom_key) === rowHash) {
        state.duplicateCount += 1;
        updateStatus();
        return;
      }
      state.lastHashByDomKey.set(payload.dom_key, rowHash);
    } else if (!rememberStatelessHash(rowHash)) {
      state.duplicateCount += 1;
      updateStatus();
      return;
    }

    payload.client_hash = rowHash;
    state.pendingQueue.push(payload);
    state.eventCount += 1;
    state.lastCaptureAtUtc = payload.captured_at_utc;
    clearError();
    updateStatus();
  }

  function captureRows(rows, observedVia) {
    rows.forEach((row) => enqueueRow(row, observedVia));
  }

  function captureExistingRows(container, observedVia) {
    const rows = new Set();
    CONFIG.rowSelectors.forEach((selector) => {
      container.querySelectorAll(selector).forEach((row) => rows.add(row));
    });
    captureRows(Array.from(rows), observedVia);
  }

  async function flushQueue() {
    if (state.flushInProgress || state.pendingQueue.length === 0) {
      return;
    }

    state.flushInProgress = true;

    try {
      let sent = 0;
      while (state.pendingQueue.length > 0 && sent < CONFIG.maxBatchSize) {
        const payload = state.pendingQueue[0];
        await postJson(CONFIG.ingestUrl, payload);
        state.pendingQueue.shift();
        sent += 1;
      }
      state.lastSuccessfulSendAtUtc = new Date().toISOString();
      clearError();
    } catch (error) {
      state.sendFailureCount += 1;
      rememberError(error);
    } finally {
      state.flushInProgress = false;
      updateStatus();
    }
  }

  async function sendHeartbeat(reason) {
    const captureAgeMs = getCaptureAgeMs();
    const stale = isStale();
    const payload = {
      collector_type: CONFIG.collectorType,
      session_id: state.sessionId,
      page_url: window.location.href,
      page_title: document.title,
      attached: state.attached,
      attach_attempts: state.attachAttempts,
      queued_event_count: state.pendingQueue.length,
      captured_event_count: state.eventCount,
      duplicate_count: state.duplicateCount,
      parse_failure_count: state.parseFailureCount,
      send_failure_count: state.sendFailureCount,
      last_capture_at_utc: state.lastCaptureAtUtc,
      capture_age_seconds: captureAgeMs === null ? null : captureAgeMs / 1000,
      is_stale: stale,
      stale_reason: stale ? "no_new_capture" : null,
      heartbeat_at_utc: new Date().toISOString(),
      source_selector: state.containerSelector,
      reason
    };

    try {
      await postJson(CONFIG.heartbeatUrl, payload);
      state.lastHeartbeatAtUtc = payload.heartbeat_at_utc;
      clearError();
    } catch (error) {
      rememberError(error);
    } finally {
      updateStatus();
    }
  }

  function attachCollector(container, selector) {
    if (state.observer) {
      state.observer.disconnect();
    }

    state.container = container;
    state.containerSelector = selector;
    state.attached = true;
    clearError();
    captureExistingRows(container, "initial_scan");

    state.observer = new MutationObserver((mutations) => {
      const rows = new Set();
      for (const mutation of mutations) {
        if (mutation.type === "childList") {
          mutation.addedNodes.forEach((node) => {
            collectRowsFromNode(node).forEach((row) => rows.add(row));
          });
          continue;
        }

        const row = findRowAncestor(mutation.target);
        if (row) {
          rows.add(row);
        }
      }
      if (rows.size > 0) {
        captureRows(Array.from(rows), "mutation");
      }
      void flushQueue();
    });

    state.observer.observe(container, {
      childList: true,
      subtree: true,
      characterData: true,
      attributes: true
    });

    updateStatus();
    void sendHeartbeat("attached");
    void flushQueue();
  }

  function resetCollectorState() {
    state.attached = false;
    state.container = null;
    state.containerSelector = null;
    state.lastHashByDomKey.clear();
    state.recentStatelessHashes.clear();
    state.recentStatelessHashOrder = [];
    if (state.observer) {
      state.observer.disconnect();
      state.observer = null;
    }
  }

  function findContainer() {
    for (const selector of CONFIG.containerSelectors) {
      const container = document.querySelector(selector);
      if (container) {
        return { container, selector };
      }
    }
    return null;
  }

  function installWhenReady() {
    const match = findContainer();
    if (!match) {
      state.attachAttempts += 1;
      if (state.attached) {
        resetCollectorState();
        rememberError("target_container_missing");
      } else {
        updateStatus();
      }
      return;
    }

    if (state.attached && state.container === match.container) {
      return;
    }

    attachCollector(match.container, match.selector);
  }

  function rescanAttachedContainer() {
    if (!state.attached || !state.container || !document.contains(state.container)) {
      installWhenReady();
      return;
    }

    captureExistingRows(state.container, "periodic_rescan");
    void flushQueue();
    updateStatus();
  }

  function monitorUrlChanges() {
    window.setInterval(() => {
      if (window.location.href === state.lastUrl) {
        return;
      }

      state.lastUrl = window.location.href;
      resetCollectorState();
      updateStatus();
      installWhenReady();
    }, 3000);
  }

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") {
      void flushQueue();
      void sendHeartbeat("visibility_hidden");
    }
  });

  updateStatus();
  installWhenReady();
  window.setInterval(installWhenReady, CONFIG.attachRetryMs);
  window.setInterval(() => void flushQueue(), CONFIG.flushIntervalMs);
  window.setInterval(() => void sendHeartbeat("interval"), CONFIG.heartbeatIntervalMs);
  window.setInterval(rescanAttachedContainer, CONFIG.rescanIntervalMs);
  monitorUrlChanges();
})();
