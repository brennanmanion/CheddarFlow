#!/usr/bin/env python3

import argparse
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from options_flow import parse_options_row_html


def parse_iso_utc(value: str | None) -> datetime | None:
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def age_seconds(value: str | None) -> float | None:
    parsed = parse_iso_utc(value)
    if not parsed:
        return None
    return max((datetime.now(timezone.utc) - parsed).total_seconds(), 0.0)


def dict_rows(connection: sqlite3.Connection, query: str, params: tuple[Any, ...] = ()) -> list[dict[str, Any]]:
    return [dict(row) for row in connection.execute(query, params).fetchall()]


def table_columns(connection: sqlite3.Connection, table_name: str) -> set[str]:
    return {row["name"] for row in connection.execute(f"PRAGMA table_info({table_name})").fetchall()}


def load_fixture_rows(connection: sqlite3.Connection, limit: int) -> list[dict[str, Any]]:
    rows = dict_rows(
        connection,
        """
        SELECT
            raw.id AS raw_event_id,
            raw.dom_key,
            raw.page_url,
            raw.captured_at_utc,
            raw.source_html,
            normalized.symbol,
            normalized.expiry,
            normalized.strike,
            normalized.put_call,
            normalized.side,
            normalized.buy_sell,
            normalized.spot,
            normalized.size,
            normalized.price,
            normalized.premium_text,
            normalized.premium_numeric,
            normalized.sweep_block_split,
            normalized.volume,
            normalized.open_interest,
            normalized.conditions
        FROM raw_events raw
        JOIN normalized_options_flow_events normalized
          ON normalized.raw_event_id = raw.id
        ORDER BY raw.rowid DESC
        LIMIT ?
        """,
        (limit,),
    )

    fixture = []
    for row in rows:
        fixture.append(
            {
                "raw_event_id": row["raw_event_id"],
                "dom_key": row["dom_key"],
                "page_url": row["page_url"],
                "captured_at_utc": row["captured_at_utc"],
                "source_html": row["source_html"],
                "expected": {
                    "symbol": row["symbol"],
                    "expiry": row["expiry"],
                    "strike": row["strike"],
                    "put_call": row["put_call"],
                    "side": row["side"],
                    "buy_sell": row["buy_sell"],
                    "spot": row["spot"],
                    "size": row["size"],
                    "price": row["price"],
                    "premium_text": row["premium_text"],
                    "premium_numeric": row["premium_numeric"],
                    "sweep_block_split": row["sweep_block_split"],
                    "volume": row["volume"],
                    "open_interest": row["open_interest"],
                    "conditions": row["conditions"],
                },
            }
        )
    return fixture


def replay_fixture(samples: list[dict[str, Any]]) -> dict[str, Any]:
    failures: list[dict[str, Any]] = []

    for sample in samples:
      parsed = parse_options_row_html(sample["source_html"]).to_dict()
      expected = sample.get("expected", {})
      mismatches = {}
      for key, expected_value in expected.items():
          actual_value = parsed.get(key)
          if actual_value != expected_value:
              mismatches[key] = {
                  "expected": expected_value,
                  "actual": actual_value,
              }

      if mismatches:
          failures.append(
              {
                  "raw_event_id": sample.get("raw_event_id"),
                  "mismatches": mismatches,
              }
          )

    return {
        "sample_count": len(samples),
        "failure_count": len(failures),
        "failures": failures[:10],
    }


def cmd_summary(connection: sqlite3.Connection, _args: argparse.Namespace) -> int:
    raw_count = connection.execute("SELECT COUNT(*) FROM raw_events").fetchone()[0]
    normalized_count = connection.execute("SELECT COUNT(*) FROM normalized_options_flow_events").fetchone()[0]
    error_count = connection.execute("SELECT COUNT(*) FROM ingestion_errors").fetchone()[0]
    heartbeat_count = connection.execute("SELECT COUNT(*) FROM collector_heartbeats").fetchone()[0]

    latest_run = connection.execute(
        """
        SELECT session_id, collector_type, page_url, status, last_heartbeat_at_utc
        FROM collector_runs
        ORDER BY last_heartbeat_at_utc DESC
        LIMIT 1
        """
    ).fetchone()
    heartbeat_columns = table_columns(connection, "collector_heartbeats")
    select_columns = [
        "session_id",
        "attached",
        "captured_event_count",
        "queued_event_count",
        "duplicate_count",
        "send_failure_count",
        "last_capture_at_utc",
        "heartbeat_at_utc",
    ]
    if "capture_age_seconds" in heartbeat_columns:
        select_columns.append("capture_age_seconds")
    if "is_stale" in heartbeat_columns:
        select_columns.append("is_stale")
    if "stale_reason" in heartbeat_columns:
        select_columns.append("stale_reason")

    latest_heartbeat = connection.execute(
        f"""
        SELECT {", ".join(select_columns)}
        FROM collector_heartbeats
        ORDER BY heartbeat_at_utc DESC
        LIMIT 1
        """
    ).fetchone()
    recent_symbols = dict_rows(
        connection,
        """
        SELECT symbol, premium_text, premium_numeric, conditions, captured_at_utc
        FROM normalized_options_flow_events
        ORDER BY rowid DESC
        LIMIT 5
        """,
    )

    latest_heartbeat_payload = dict(latest_heartbeat) if latest_heartbeat else None
    if latest_heartbeat_payload and latest_heartbeat_payload.get("capture_age_seconds") is None:
        latest_heartbeat_payload["capture_age_seconds"] = age_seconds(
            latest_heartbeat_payload.get("last_capture_at_utc")
        )
    if latest_heartbeat_payload and "is_stale" not in latest_heartbeat_payload:
        latest_heartbeat_payload["is_stale"] = bool(
            latest_heartbeat_payload.get("attached")
            and latest_heartbeat_payload.get("capture_age_seconds") is not None
            and latest_heartbeat_payload["capture_age_seconds"] >= 45
        )
    if latest_heartbeat_payload and "stale_reason" not in latest_heartbeat_payload:
        latest_heartbeat_payload["stale_reason"] = (
            "no_new_capture" if latest_heartbeat_payload.get("is_stale") else None
        )

    print(
        json.dumps(
            {
                "raw_event_count": raw_count,
                "normalized_event_count": normalized_count,
                "heartbeat_count": heartbeat_count,
                "error_count": error_count,
                "latest_run": dict(latest_run) if latest_run else None,
                "latest_heartbeat": latest_heartbeat_payload,
                "recent_events": recent_symbols,
            },
            indent=2,
        )
    )
    return 0


def cmd_latest_events(connection: sqlite3.Connection, args: argparse.Namespace) -> int:
    rows = dict_rows(
        connection,
        """
        SELECT symbol, put_call, side, buy_sell, premium_text, premium_numeric, conditions, captured_at_utc
        FROM normalized_options_flow_events
        ORDER BY rowid DESC
        LIMIT ?
        """,
        (args.limit,),
    )
    print(json.dumps(rows, indent=2))
    return 0


def cmd_errors(connection: sqlite3.Connection, args: argparse.Namespace) -> int:
    rows = dict_rows(
        connection,
        """
        SELECT created_at_utc, session_id, error_type, detail
        FROM ingestion_errors
        ORDER BY rowid DESC
        LIMIT ?
        """,
        (args.limit,),
    )
    print(json.dumps(rows, indent=2))
    return 0


def cmd_export_fixture(connection: sqlite3.Connection, args: argparse.Namespace) -> int:
    fixture = load_fixture_rows(connection, args.limit)
    output_path = Path(args.output).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(fixture, indent=2), encoding="utf-8")
    print(f"Wrote {len(fixture)} samples to {output_path}")
    return 0


def cmd_replay(connection: sqlite3.Connection, args: argparse.Namespace) -> int:
    if args.fixture:
        samples = json.loads(Path(args.fixture).read_text(encoding="utf-8"))
    else:
        samples = load_fixture_rows(connection, args.limit)

    result = replay_fixture(samples)
    print(json.dumps(result, indent=2))
    return 1 if result["failure_count"] else 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Inspect and replay the Phase 1 SQLite feed database.")
    parser.add_argument("--db", default="data/cheddarflow_phase1.sqlite3")

    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("summary")

    latest_events = subparsers.add_parser("latest-events")
    latest_events.add_argument("--limit", type=int, default=10)

    errors = subparsers.add_parser("errors")
    errors.add_argument("--limit", type=int, default=10)

    export_fixture = subparsers.add_parser("export-fixture")
    export_fixture.add_argument("--output", required=True)
    export_fixture.add_argument("--limit", type=int, default=10)

    replay = subparsers.add_parser("replay")
    replay.add_argument("--fixture")
    replay.add_argument("--limit", type=int, default=10)

    return parser.parse_args()


def main() -> int:
    args = parse_args()
    connection = sqlite3.connect(Path(args.db).resolve())
    connection.row_factory = sqlite3.Row

    try:
        if args.command == "summary":
            return cmd_summary(connection, args)
        if args.command == "latest-events":
            return cmd_latest_events(connection, args)
        if args.command == "errors":
            return cmd_errors(connection, args)
        if args.command == "export-fixture":
            return cmd_export_fixture(connection, args)
        if args.command == "replay":
            return cmd_replay(connection, args)
        raise ValueError(f"Unsupported command: {args.command}")
    finally:
        connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
