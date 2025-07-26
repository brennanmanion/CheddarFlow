import csv
from collections import defaultdict

# Function to parse each entry
def parse_entry(entry):
    time = entry.split('class="chakra-text css-ibcln5">')[1].split('</span>')[0].strip()
    ticker = entry.split('class="chakra-button css-clq57a">')[1].split('</button>')[0].strip()
    qty = entry.split('class="chakra-text css-1ttggfw">')[1].split('</span>')[0].replace(',', '').strip()
    spot = entry.split('class="chakra-text css-1crhv03">')[1].split('</span>')[0].strip()
    amount = entry.split('class="chakra-text css-1e9v8ws">')[1].split('</span>')[0].replace('$', '').strip()
    date = entry.split('class="chakra-text css-ibcln5">')[2].split('</span>')[0].strip()
    return [time, ticker, qty, spot, amount, date]

def convert_amount(amount_str):
    # Remove commas and dollar sign
    amount_str = amount_str.replace(',', '').replace('$', '')

    # Define multipliers for K, M, and B
    multipliers = {'K': 1_000, 'M': 1_000_000, 'B': 1_000_000_000}

    # Check if the last character is K, M, or B
    if amount_str[-1] in multipliers:
        # Extract the number part and the last character
        number, multiplier = float(amount_str[:-1]), amount_str[-1]
        # Multiply the number by its respective multiplier
        return number * multipliers[multiplier]
    else:
        # If no K, M, or B, just convert to float
        return float(amount_str)
    
# Path to the file containing the HTML data
data_file = '/Users/brennan/Downloads/my_data (2).txt'  # Replace with the path to your file

grouped_data = defaultdict(list)

total_amounts = defaultdict(float)

# Read the HTML data from the file
with open(data_file, 'r') as file:
    data = file.read()

# Split the data by the delimiter
entries = data.split("<!--ENDOFITEM-->")

for entry in entries:
    try:
        parsed = parse_entry(entry)
        ticker = parsed[1]
        amount = convert_amount(parsed[4])
        grouped_data[ticker].append(parsed)
        total_amounts[ticker] += amount
    except:
        continue

# CSV file to write to
csv_file = 'parsed_data.csv'

with open(csv_file, 'w', newline='') as file:
    writer = csv.writer(file)
    # Write the header
    writer.writerow(["Ticker", "Total Amount", "Entries"])

    # Write total amount and entries for each ticker
    for ticker, entries in grouped_data.items():
        # Format the total amount with two decimal places
        formatted_total = f"${total_amounts[ticker]:,.2f}"
        # Write the row for this ticker
        writer.writerow([ticker, formatted_total, len(entries)])


print(f"Data has been written to {csv_file}")
