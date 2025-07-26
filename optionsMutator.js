// P1
let items = [];

allNestedDivs = document.querySelector(".ag-center-cols-container").querySelectorAll("div.ag-row")
allNestedDivs.forEach(div => {
    items.push(div);
});

const callback = function(mutationsList, observer) {
    for(let mutation of mutationsList) {
        if (mutation.type === 'childList') {
          Array.from(mutation.addedNodes).map(node => {
            if (node.nodeType === Node.ELEMENT_NODE) {
              items.push(node);
            }
          })}
    }
};

const observer = new MutationObserver(callback);

observer.observe(document.querySelector(".ag-center-cols-container"), { childList: true });

// P2
let csvData = [];
items.forEach((item, index) => {
  
  const instrument = new Map([
    ['CALL', 0],
    ['PUT', 0]
  ]);
  
  const side = new Map([
    ['ABOVE', 0],
    ['ASK', 0]
  ]);

  const conds = new Map([
    ['AUTO', 0],
    ['AUCTION', 0],
    ['ISO', 0],
    ['OPENING', 0],
    ['UNUSUAL', 0],
    ['HIGHLY_UN', 0]
  ]);

  htmlBlob = item.innerHTML;
  // Parse the string into an HTML document
  const parser = new DOMParser();
  const doc = parser.parseFromString(htmlBlob, 'text/html');

  // Select all div elements with role="gridcell"
  const gridCells = doc.querySelectorAll('div[role="gridcell"]');

  // Extract the inner text from each div
  extractedData = []
  gridCells.forEach(cell => {
    // Check if there is a button inside, and get button text if available
    let toPush = '';
    const button = cell.querySelector('button');
    const listItems = cell.querySelectorAll('li');
    if (button) {
      toPush = button.innerText.trim().toUpperCase();
    } if (listItems.length > 0) {
      listItems.forEach((value, index) => {
          key = value.innerText.toUpperCase();
          if (conds.has(key)){
            conds.set(key, 1);
          }
      });
    } else {
      toPush = cell.innerText.trim().toUpperCase();
    }

    if (toPush !== null && toPush.trim() !== ""){
      if(instrument.has(toPush) || side.has(toPush)){
        if(instrument.has(toPush)){
          instrument.set(toPush, 1);
        }
        if (side.has(toPush)){
          side.set(toPush, 1);
        }
      }
      else{
        extractedData.push(toPush);
      }
    }
  });

  extractedData.push(...instrument.values());
  extractedData.push(...side.values());
  extractedData.push(...conds.values());
  csvData.push(extractedData);
});

// P3
function arrayToCSV(data) {
  return data.map(row => row.map(escapeCSVValue).join(",")).join("\n");
}

function escapeCSVValue(value) {
  if (typeof value === 'string') {
    // Escape any double quotes by doubling them
    value = value.replace(/"/g, '""');
    // If the string contains a comma or newline, enclose it in double quotes
    if (value.includes(',') || value.includes('\n')) {
      value = `"${value}"`;
    }
  }
  return value;
}

let headers = [[
  'time', 'date', 'tick', 'expiry', 'strike',
    'buySell', 'spot', 'size', 'price', 'premium',
    'sweepBlockSplit', 'volume', 'openInt', 'call', 'put', 'above', 'ask', 'auto', 'auction', 'iso', 'opening', 'unusual', 'highly_un'
]];

// Convert to CSV string
let csvContent = arrayToCSV(csvData);

csvContent = headers.map(row => row.join(',')).join('\n') + '\n' + csvContent;

// Create a blob and trigger a download
let blob = new Blob([csvContent], { type: 'text/csv' });
let link = document.createElement('a');
link.href = URL.createObjectURL(blob);
link.download = 'data.csv';
document.body.appendChild(link);
link.click();
document.body.removeChild(link);

// P3
// const delimiter = "<!--ENDOFITEM-->";
// let htmlContents = items.map(element => element.innerHTML + delimiter);

// const blob = new Blob(htmlContents, { type: 'text/csv;charset=utf-8;' });
// const url = URL.createObjectURL(blob);

// const link = document.createElement("a");
// link.setAttribute("href", url);
// link.setAttribute("download", "my_data.txt");
// document.body.appendChild(link);

// link.click();
// document.body.removeChild(link);