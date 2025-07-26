from bs4 import BeautifulSoup
import pandas as pd
import datetime

# Path to the HTML file
file_path = '/Users/brennan/Downloads/ag-center-cols-container'

# Reading the HTML content
with open(file_path, 'r', encoding='utf-8') as file:
    html_content = file.read()

# Parsing the HTML content using BeautifulSoup
soup = BeautifulSoup(html_content, 'html.parser')

# Finding all the row elements
rows = soup.find_all(class_='ag-row')

data = {}

for row in rows:
    # Extracting each cell from the row
    cells = row.find_all(class_='ag-cell')
    row_data = [cell.get_text(strip=True) for cell in cells]

    # Extracting ticker and total value, ensuring they are in the expected position
    if len(row_data) >= 12:
        ticker = row_data[2]
        total_value_str = row_data[11]

        # Removing non-numeric characters and converting to float
        total_value = float(total_value_str.replace('$', '').replace('K', '000').replace(',', '').replace('M', '000000'))
        
        call_premium = float(0)
        put_premium = float(0)
        call_count = 0
        put_count = 0
        optionType = row_data[5]
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

# Converting the dictionary to a DataFrame for better presentation and export
ticker_totals_df = pd.DataFrame(data.values())

# Defining the file path for the CSV
csv_file_path = '/Users/brennan/Downloads/' + str(datetime.datetime.now()) + '.csv'

# Writing the DataFrame to a CSV file
ticker_totals_df.to_csv(csv_file_path, index=False)

print(f"File written to {csv_file_path}")
