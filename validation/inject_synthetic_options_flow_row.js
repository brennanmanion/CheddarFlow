(() => {
  const container =
    document.querySelector(".ag-center-cols-container") ||
    document.querySelector("[class*='ag-center-cols-container']");

  if (!container) {
    throw new Error("Could not find AG Grid center-cols container.");
  }

  const syntheticSession = `synthetic-${Date.now()}`;
  const row = document.createElement("div");
  row.className = "ag-row ag-row-position-absolute";
  row.setAttribute("role", "row");
  row.setAttribute("row-id", syntheticSession);
  row.setAttribute("aria-rowindex", String(container.querySelectorAll(".ag-row").length + 1));
  row.style.position = "relative";

  const cells = [
    ["time", "03:59:30 PM"],
    ["expiry", "<button type=\"button\">06/20/2026</button>"],
    ["symbol", "<button type=\"button\">SPY</button>"],
    ["strike", "540"],
    ["spot", "529.11"],
    ["putCall", "Call"],
    ["side", "Ask"],
    ["buySell", "BUY"],
    ["size", "125"],
    ["price", "$12.50"],
    ["premium", "$1.5M"],
    ["sweepBlockSplit", "Sweep"],
    ["volume", "480"],
    ["openInt", "2,100"],
    [
      "conds",
      "<div><ul><li><span>AUTO</span></li><li><span>synthetic</span></li></ul></div>"
    ]
  ];

  cells.forEach(([colId, innerHtml], index) => {
    const cell = document.createElement("div");
    cell.className = "ag-cell ag-cell-value";
    cell.setAttribute("role", "gridcell");
    cell.setAttribute("col-id", colId);
    cell.setAttribute("aria-colindex", String(index + 1));
    cell.innerHTML = innerHtml;
    row.appendChild(cell);
  });

  container.appendChild(row);

  window.setTimeout(() => {
    row.remove();
  }, 10000);

  return {
    injected: true,
    rowId: syntheticSession,
    cellCount: cells.length
  };
})();
