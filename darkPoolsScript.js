// P1
let items = [];
const delimiter = "<!--ENDOFITEM-->";
const callback = function(mutationsList, observer) {
    for(let mutation of mutationsList) {
        if (mutation.type === 'childList') {
            items = [
                ...items,
                ...Array.from(mutation.addedNodes).map(node => {
                  if (node.nodeType === Node.ELEMENT_NODE) {
                    return node.innerHTML + delimiter;
                  }
                  return '';
                })
              ];        }
    }
};

const observer = new MutationObserver(callback);

observer.observe(document.querySelector("#tabs-\\:r1o\\:--tabpanel-0 > div > div > ul"), { childList: true });

// P2

const blob = new Blob(items, { type: 'text/csv;charset=utf-8;' });
const url = URL.createObjectURL(blob);

const link = document.createElement("a");
link.setAttribute("href", url);
link.setAttribute("download", "my_data.txt");
document.body.appendChild(link);

link.click();
document.body.removeChild(link);