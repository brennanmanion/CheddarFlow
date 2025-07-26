import pandas as pd
import datetime

def parse_grid_cell(cell_html):
    # Create a helper function to extract data by col-id
    def extract_by_col_id(html, col_id):
        start = html.find(f'col-id="{col_id}"')  # Find the start of the div with the col-id
        if start == -1:
            return ""  # If the col-id isn't found, return an empty string
        start = html.find('>', start) + 1  # Find the start of the content
        end = html.find('</div>', start)  # Find the end of the div
        return html[start:end].strip()  # Extract and return the content, trimmed of whitespace

    # Define the col-ids in order, as they appear in your cells
    col_ids = [
        'time', 'date', 'tick', 'expiry', 'strike', 'putCall',
        'side', 'buySell', 'spot', 'size', 'price', 'premium',
        'sweepBlockSplit', 'volume', 'openInt', 'conds'
    ]
    
    # Use list comprehension to apply the helper function to each col-id
    data = [extract_by_col_id(cell_html, col_id) for col_id in col_ids]
    
    # Extract symbol from nested div directly
    symbol_start = cell_html.find('col-id="symbol"')  # Find the start of the div with the col-id
    if symbol_start != -1:
        symbol_start = cell_html.find('<div>', symbol_start) + 5  # Find the start of the nested div content
        symbol_end = cell_html.find('</div>', symbol_start)  # Find the end of the nested div
        data[2] = cell_html[symbol_start:symbol_end].strip()

    # Handle 'conds' field with nested divs and spans
    conds_start = cell_html.find('col-id="conds"')  # Find the start of the div with the col-id
    if conds_start != -1:
        conds_start = cell_html.find('>', conds_start) + 1  # Find the start of the content
        conds_end = cell_html.find('</div>', conds_start)  # Find the end of the div
        conds_content = cell_html[conds_start:conds_end]
        # Extract all spans containing badge classes
        spans = []
        while 'chakra-badge' in conds_content:
            span_start = conds_content.find('<span')
            span_start = conds_content.find('>', span_start) + 1
            span_end = conds_content.find('</span>', span_start)
            spans.append(conds_content[span_start:span_end].strip())
            conds_content = conds_content[span_end+7:]  # Skip past the closing span tag
        data[15] = ' '.join(spans)

    return data


# Path to the HTML file
file_path = '/Users/brennan/Downloads/my_data (19).txt'

data = {}

# Read the HTML data from the file
with open(file_path, 'r') as file:
    html = file.read()

# Split the data by the delimiter
entries = html.split("<!--ENDOFITEM-->")

for entry in entries:
    try:
        parsed = parse_grid_cell(entry)

        ticker = parsed[2]
        total_value_str = parsed[11]

        # Removing non-numeric characters and converting to float
        # use 10 ^ x for K, M and multiply after pulling out K, M, $
        multiplier = 1
        if 'K' in total_value_str:
            multiplier = 1000
        if 'M' in total_value_str:
            multiplier = 1000000
        total_value = multiplier * float(total_value_str.replace('$', '').replace('K', '').replace(',', '').replace('M', ''))
        
        call_premium = float(0)
        put_premium = float(0)
        call_count = 0
        put_count = 0
        optionType = parsed[5]
        if ('Call' == optionType):
            call_premium += total_value
            call_count += 1
        elif('Put' == optionType):
            put_premium += total_value
            put_count += 1
        
        # Check if the ticker already exists
        if ticker in data:
            data[ticker]['Call Premium'] += call_premium
            data[ticker]['Put Premium'] += put_premium
            data[ticker]['Call Count'] += call_count
            data[ticker]['Put Count'] += put_count
            
        else:
            # Add new ticker entry
            data[ticker] = {'Ticker': ticker, 'Call Premium': call_premium, 'Put Premium': put_premium, 'Call Count': call_count, 'Put Count': put_count}

    except:
        continue

# Converting the dictionary to a DataFrame for better presentation and export
ticker_totals_df = pd.DataFrame(data.values())

# Defining the file path for the CSV
csv_file_path = '/Users/brennan/Downloads/' + str(datetime.datetime.now()) + '.csv'

# Writing the DataFrame to a CSV file
ticker_totals_df.to_csv(csv_file_path, index=False)

print(f"File written to {csv_file_path}")
