from dataclasses import asdict, dataclass

from bs4 import BeautifulSoup


COL_IDS = [
    "time",
    "date",
    "tick",
    "expiry",
    "strike",
    "putCall",
    "side",
    "buySell",
    "spot",
    "size",
    "price",
    "premium",
    "sweepBlockSplit",
    "volume",
    "openInt",
    "conds",
]


def parse_numeric_with_suffix(value: str) -> float | None:
    cleaned = value.strip().replace("$", "").replace(",", "")
    if not cleaned:
        return None

    multiplier = 1.0
    suffixes = {
        "K": 1_000.0,
        "M": 1_000_000.0,
        "B": 1_000_000_000.0,
    }

    last_char = cleaned[-1].upper()
    if last_char in suffixes:
        multiplier = suffixes[last_char]
        cleaned = cleaned[:-1]

    return float(cleaned) * multiplier


def normalize_text(value: str) -> str:
    return " ".join(value.split())


def extract_cell_text(cell) -> str:
    list_items = cell.find_all("li")
    if list_items:
        return " ".join(
            normalize_text(item.get_text(" ", strip=True))
            for item in list_items
            if normalize_text(item.get_text(" ", strip=True))
        )

    button = cell.find("button")
    if button:
        return normalize_text(button.get_text(" ", strip=True))

    return normalize_text(cell.get_text(" ", strip=True))


@dataclass
class ParsedOptionsRow:
    event_time_text: str
    event_date_text: str
    symbol: str
    expiry: str
    strike: str
    put_call: str
    side: str
    buy_sell: str
    spot: str
    size: str
    price: str
    premium_text: str
    premium_numeric: float | None
    sweep_block_split: str
    volume: str
    open_interest: str
    conditions: str

    def to_dict(self) -> dict[str, str | float | None]:
        return asdict(self)


def parse_options_row_html(source_html: str) -> ParsedOptionsRow:
    soup = BeautifulSoup(source_html, "html.parser")
    extracted: dict[str, str] = {}

    for col_id in COL_IDS:
        cell = soup.find(attrs={"col-id": col_id})
        if not cell:
            extracted[col_id] = ""
            continue
        extracted[col_id] = extract_cell_text(cell)

    symbol_cell = soup.find(attrs={"col-id": "symbol"})
    symbol_text = extract_cell_text(symbol_cell) if symbol_cell else extracted["tick"]

    return ParsedOptionsRow(
        event_time_text=extracted["time"],
        event_date_text=extracted["date"],
        symbol=symbol_text or extracted["tick"],
        expiry=extracted["expiry"],
        strike=extracted["strike"],
        put_call=extracted["putCall"],
        side=extracted["side"],
        buy_sell=extracted["buySell"],
        spot=extracted["spot"],
        size=extracted["size"],
        price=extracted["price"],
        premium_text=extracted["premium"],
        premium_numeric=parse_numeric_with_suffix(extracted["premium"]) if extracted["premium"] else None,
        sweep_block_split=extracted["sweepBlockSplit"],
        volume=extracted["volume"],
        open_interest=extracted["openInt"],
        conditions=extracted["conds"],
    )
