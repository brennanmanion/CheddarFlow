#!/usr/bin/env python3

import argparse
import hashlib
import json
import os
import sqlite3
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import RLock
from typing import Any

from options_flow import parse_options_row_html

STALE_CAPTURE_SECONDS = 45


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def parse_iso_utc(value: str | None) -> datetime | None:
    if not value:
        return None

    normalized = value.replace("Z", "+00:00")
    return datetime.fromisoformat(normalized)


def capture_age_seconds(last_capture_at_utc: str | None) -> float | None:
    last_capture = parse_iso_utc(last_capture_at_utc)
    if not last_capture:
        return None
    return max((datetime.now(timezone.utc) - last_capture).total_seconds(), 0.0)


class Database:
    def __init__(self, db_path: Path) -> None:
        self.db_path = db_path
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.lock = RLock()
        self.connection = sqlite3.connect(self.db_path, check_same_thread=False)
        self.connection.row_factory = sqlite3.Row
        self.connection.execute("PRAGMA journal_mode=WAL")
        self.connection.execute("PRAGMA synchronous=NORMAL")
        self.connection.execute("PRAGMA foreign_keys=ON")
        self._create_schema()
        self._run_migrations()

    def _create_schema(self) -> None:
        with self.lock:
            self.connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS collector_runs (
                    session_id TEXT PRIMARY KEY,
                    collector_type TEXT NOT NULL,
                    page_url TEXT,
                    page_title TEXT,
                    started_at_utc TEXT NOT NULL,
                    last_heartbeat_at_utc TEXT,
                    status TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS collector_heartbeats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL,
                    collector_type TEXT NOT NULL,
                    page_url TEXT,
                    page_title TEXT,
                    attached INTEGER NOT NULL,
                    attach_attempts INTEGER NOT NULL,
                    queued_event_count INTEGER NOT NULL,
                    captured_event_count INTEGER NOT NULL,
                    duplicate_count INTEGER NOT NULL,
                    parse_failure_count INTEGER NOT NULL,
                    send_failure_count INTEGER NOT NULL,
                    source_selector TEXT,
                    reason TEXT,
                    last_capture_at_utc TEXT,
                    capture_age_seconds REAL,
                    is_stale INTEGER NOT NULL DEFAULT 0,
                    stale_reason TEXT,
                    heartbeat_at_utc TEXT NOT NULL,
                    FOREIGN KEY(session_id) REFERENCES collector_runs(session_id)
                );

                CREATE TABLE IF NOT EXISTS raw_events (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    collector_type TEXT NOT NULL,
                    page_url TEXT,
                    page_title TEXT,
                    source_selector TEXT,
                    dom_key TEXT,
                    source_html TEXT NOT NULL,
                    source_text TEXT,
                    source_hash TEXT NOT NULL,
                    client_hash TEXT,
                    observed_via TEXT,
                    captured_at_utc TEXT NOT NULL,
                    ingested_at_utc TEXT NOT NULL,
                    UNIQUE(session_id, source_hash, dom_key),
                    FOREIGN KEY(session_id) REFERENCES collector_runs(session_id)
                );

                CREATE TABLE IF NOT EXISTS normalized_options_flow_events (
                    id TEXT PRIMARY KEY,
                    raw_event_id TEXT NOT NULL UNIQUE,
                    session_id TEXT NOT NULL,
                    event_time_text TEXT,
                    event_date_text TEXT,
                    symbol TEXT,
                    expiry TEXT,
                    strike TEXT,
                    put_call TEXT,
                    side TEXT,
                    buy_sell TEXT,
                    spot TEXT,
                    size TEXT,
                    price TEXT,
                    premium_text TEXT,
                    premium_numeric REAL,
                    sweep_block_split TEXT,
                    volume TEXT,
                    open_interest TEXT,
                    conditions TEXT,
                    captured_at_utc TEXT NOT NULL,
                    FOREIGN KEY(raw_event_id) REFERENCES raw_events(id)
                );

                CREATE TABLE IF NOT EXISTS ingestion_errors (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    created_at_utc TEXT NOT NULL,
                    session_id TEXT,
                    error_type TEXT NOT NULL,
                    detail TEXT NOT NULL,
                    payload_json TEXT
                );
                """
            )
            self.connection.commit()

    def _run_migrations(self) -> None:
        self._ensure_column(
            "collector_heartbeats",
            "capture_age_seconds",
            "ALTER TABLE collector_heartbeats ADD COLUMN capture_age_seconds REAL",
        )
        self._ensure_column(
            "collector_heartbeats",
            "is_stale",
            "ALTER TABLE collector_heartbeats ADD COLUMN is_stale INTEGER NOT NULL DEFAULT 0",
        )
        self._ensure_column(
            "collector_heartbeats",
            "stale_reason",
            "ALTER TABLE collector_heartbeats ADD COLUMN stale_reason TEXT",
        )

    def _ensure_column(self, table_name: str, column_name: str, alter_sql: str) -> None:
        with self.lock:
            columns = {
                row["name"]
                for row in self.connection.execute(f"PRAGMA table_info({table_name})").fetchall()
            }
            if column_name not in columns:
                self.connection.execute(alter_sql)
                self.connection.commit()

    def upsert_collector_run(
        self,
        session_id: str,
        collector_type: str,
        page_url: str | None,
        page_title: str | None,
        started_at_utc: str,
        last_heartbeat_at_utc: str | None,
        status: str,
    ) -> None:
        with self.lock:
            self.connection.execute(
                """
                INSERT INTO collector_runs (
                    session_id,
                    collector_type,
                    page_url,
                    page_title,
                    started_at_utc,
                    last_heartbeat_at_utc,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(session_id) DO UPDATE SET
                    collector_type = excluded.collector_type,
                    page_url = excluded.page_url,
                    page_title = excluded.page_title,
                    last_heartbeat_at_utc = excluded.last_heartbeat_at_utc,
                    status = excluded.status
                """,
                (
                    session_id,
                    collector_type,
                    page_url,
                    page_title,
                    started_at_utc,
                    last_heartbeat_at_utc,
                    status,
                ),
            )
            self.connection.commit()

    def insert_heartbeat(self, payload: dict[str, Any]) -> None:
        session_id = payload["session_id"]
        collector_type = payload["collector_type"]
        heartbeat_at_utc = payload["heartbeat_at_utc"]
        with self.lock:
            self.connection.execute(
                """
                INSERT INTO collector_runs (
                    session_id,
                    collector_type,
                    page_url,
                    page_title,
                    started_at_utc,
                    last_heartbeat_at_utc,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(session_id) DO UPDATE SET
                    collector_type = excluded.collector_type,
                    page_url = excluded.page_url,
                    page_title = excluded.page_title,
                    last_heartbeat_at_utc = excluded.last_heartbeat_at_utc,
                    status = excluded.status
                """,
                (
                    session_id,
                    collector_type,
                    payload.get("page_url"),
                    payload.get("page_title"),
                    heartbeat_at_utc,
                    heartbeat_at_utc,
                    "attached" if payload.get("attached") else "waiting",
                ),
            )
            self.connection.execute(
                """
                INSERT INTO collector_heartbeats (
                    session_id,
                    collector_type,
                    page_url,
                    page_title,
                    attached,
                    attach_attempts,
                    queued_event_count,
                    captured_event_count,
                    duplicate_count,
                    parse_failure_count,
                    send_failure_count,
                    source_selector,
                    reason,
                    last_capture_at_utc,
                    capture_age_seconds,
                    is_stale,
                    stale_reason,
                    heartbeat_at_utc
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    session_id,
                    collector_type,
                    payload.get("page_url"),
                    payload.get("page_title"),
                    1 if payload.get("attached") else 0,
                    int(payload.get("attach_attempts", 0)),
                    int(payload.get("queued_event_count", 0)),
                    int(payload.get("captured_event_count", 0)),
                    int(payload.get("duplicate_count", 0)),
                    int(payload.get("parse_failure_count", 0)),
                    int(payload.get("send_failure_count", 0)),
                    payload.get("source_selector"),
                    payload.get("reason"),
                    payload.get("last_capture_at_utc"),
                    payload.get("capture_age_seconds"),
                    1 if payload.get("is_stale") else 0,
                    payload.get("stale_reason"),
                    heartbeat_at_utc,
                ),
            )
            self.connection.commit()

    def log_error(self, error_type: str, detail: str, payload: dict[str, Any] | None) -> None:
        with self.lock:
            self.connection.execute(
                """
                INSERT INTO ingestion_errors (
                    created_at_utc,
                    session_id,
                    error_type,
                    detail,
                    payload_json
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                (
                    utc_now_iso(),
                    payload.get("session_id") if payload else None,
                    error_type,
                    detail,
                    json.dumps(payload) if payload else None,
                ),
            )
            self.connection.commit()

    def insert_raw_event(self, payload: dict[str, Any]) -> dict[str, Any]:
        ingested_at_utc = utc_now_iso()
        source_html = payload["source_html"]
        source_hash = sha256_text(source_html)
        raw_event_id = sha256_text(
            f"{payload['session_id']}|{payload.get('dom_key') or ''}|{source_hash}|{payload['captured_at_utc']}"
        )

        try:
            parsed = parse_options_row_html(source_html)
        except Exception as error:
            self.log_error("normalize_options_row_failed", str(error), payload)
            parsed = None

        with self.lock:
            self.connection.execute(
                """
                INSERT INTO collector_runs (
                    session_id,
                    collector_type,
                    page_url,
                    page_title,
                    started_at_utc,
                    last_heartbeat_at_utc,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(session_id) DO UPDATE SET
                    collector_type = excluded.collector_type,
                    page_url = excluded.page_url,
                    page_title = excluded.page_title,
                    last_heartbeat_at_utc = excluded.last_heartbeat_at_utc,
                    status = excluded.status
                """,
                (
                    payload["session_id"],
                    payload["collector_type"],
                    payload.get("page_url"),
                    payload.get("page_title"),
                    payload["captured_at_utc"],
                    ingested_at_utc,
                    "capturing",
                ),
            )

            cursor = self.connection.execute(
                """
                INSERT OR IGNORE INTO raw_events (
                    id,
                    session_id,
                    collector_type,
                    page_url,
                    page_title,
                    source_selector,
                    dom_key,
                    source_html,
                    source_text,
                    source_hash,
                    client_hash,
                    observed_via,
                    captured_at_utc,
                    ingested_at_utc
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    raw_event_id,
                    payload["session_id"],
                    payload["collector_type"],
                    payload.get("page_url"),
                    payload.get("page_title"),
                    payload.get("source_selector"),
                    payload.get("dom_key"),
                    source_html,
                    payload.get("source_text"),
                    source_hash,
                    payload.get("client_hash"),
                    payload.get("observed_via"),
                    payload["captured_at_utc"],
                    ingested_at_utc,
                ),
            )

            if cursor.rowcount == 0:
                self.connection.commit()
                return {"status": "duplicate", "raw_event_id": raw_event_id}

            if parsed is not None:
                normalized_id = sha256_text(f"normalized|{raw_event_id}")
                self.connection.execute(
                    """
                    INSERT INTO normalized_options_flow_events (
                        id,
                        raw_event_id,
                        session_id,
                        event_time_text,
                        event_date_text,
                        symbol,
                        expiry,
                        strike,
                        put_call,
                        side,
                        buy_sell,
                        spot,
                        size,
                        price,
                        premium_text,
                        premium_numeric,
                        sweep_block_split,
                        volume,
                        open_interest,
                        conditions,
                        captured_at_utc
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        normalized_id,
                        raw_event_id,
                        payload["session_id"],
                        parsed.event_time_text,
                        parsed.event_date_text,
                        parsed.symbol,
                        parsed.expiry,
                        parsed.strike,
                        parsed.put_call,
                        parsed.side,
                        parsed.buy_sell,
                        parsed.spot,
                        parsed.size,
                        parsed.price,
                        parsed.premium_text,
                        parsed.premium_numeric,
                        parsed.sweep_block_split,
                        parsed.volume,
                        parsed.open_interest,
                        parsed.conditions,
                        payload["captured_at_utc"],
                    ),
                )
                self.connection.commit()
                return {"status": "inserted", "raw_event_id": raw_event_id, "normalized_id": normalized_id}

            self.connection.commit()
            return {"status": "inserted_raw_only", "raw_event_id": raw_event_id, "error": "normalize_options_row_failed"}

    def health_snapshot(self) -> dict[str, Any]:
        with self.lock:
            raw_count = self.connection.execute("SELECT COUNT(*) FROM raw_events").fetchone()[0]
            normalized_count = self.connection.execute(
                "SELECT COUNT(*) FROM normalized_options_flow_events"
            ).fetchone()[0]
            heartbeat_count = self.connection.execute("SELECT COUNT(*) FROM collector_heartbeats").fetchone()[0]
            error_count = self.connection.execute("SELECT COUNT(*) FROM ingestion_errors").fetchone()[0]
            last_run = self.connection.execute(
                """
                SELECT session_id, collector_type, page_url, status, last_heartbeat_at_utc
                FROM collector_runs
                ORDER BY last_heartbeat_at_utc DESC
                LIMIT 1
                """
            ).fetchone()
            latest_heartbeat = self.connection.execute(
                """
                SELECT
                    session_id,
                    attached,
                    captured_event_count,
                    queued_event_count,
                    last_capture_at_utc,
                    capture_age_seconds,
                    is_stale,
                    stale_reason,
                    heartbeat_at_utc
                FROM collector_heartbeats
                ORDER BY heartbeat_at_utc DESC
                LIMIT 1
                """
            ).fetchone()

        latest_heartbeat_payload = dict(latest_heartbeat) if latest_heartbeat else None
        if latest_heartbeat_payload and latest_heartbeat_payload.get("capture_age_seconds") is None:
            latest_heartbeat_payload["capture_age_seconds"] = capture_age_seconds(
                latest_heartbeat_payload.get("last_capture_at_utc")
            )
            latest_heartbeat_payload["is_stale"] = (
                latest_heartbeat_payload["attached"]
                and latest_heartbeat_payload["capture_age_seconds"] is not None
                and latest_heartbeat_payload["capture_age_seconds"] >= STALE_CAPTURE_SECONDS
            )
            if latest_heartbeat_payload["is_stale"] and not latest_heartbeat_payload.get("stale_reason"):
                latest_heartbeat_payload["stale_reason"] = "no_new_capture"

        return {
            "ok": True,
            "db_path": str(self.db_path),
            "raw_event_count": raw_count,
            "normalized_event_count": normalized_count,
            "heartbeat_count": heartbeat_count,
            "error_count": error_count,
            "latest_run": dict(last_run) if last_run else None,
            "stale_capture_threshold_seconds": STALE_CAPTURE_SECONDS,
            "latest_heartbeat": latest_heartbeat_payload,
        }


class RequestHandler(BaseHTTPRequestHandler):
    server: "CollectorServer"

    def _read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(length)
        return json.loads(raw_body.decode("utf-8"))

    def _send_json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self) -> None:
        self._send_json(HTTPStatus.NO_CONTENT, {})

    def do_GET(self) -> None:
        if self.path == "/health":
            self._send_json(HTTPStatus.OK, self.server.database.health_snapshot())
            return

        self._send_json(HTTPStatus.NOT_FOUND, {"error": "Not found"})

    def do_POST(self) -> None:
        try:
            payload = self._read_json()
        except Exception as error:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": f"Invalid JSON: {error}"})
            return

        try:
            if self.path == "/api/options-flow/raw":
                result = self.server.database.insert_raw_event(payload)
                self._send_json(HTTPStatus.OK, result)
                return

            if self.path == "/api/heartbeats":
                self.server.database.insert_heartbeat(payload)
                self._send_json(HTTPStatus.OK, {"status": "ok"})
                return

            self._send_json(HTTPStatus.NOT_FOUND, {"error": "Not found"})
        except KeyError as error:
            self.server.database.log_error("missing_field", str(error), payload)
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": f"Missing field: {error}"})
        except Exception as error:
            self.server.database.log_error("request_failed", str(error), payload)
            self._send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"error": str(error)})

    def log_message(self, format: str, *args: Any) -> None:
        return


class CollectorServer(ThreadingHTTPServer):
    def __init__(self, server_address: tuple[str, int], database: Database) -> None:
        super().__init__(server_address, RequestHandler)
        self.database = database


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Local ingestion service for the CheddarFlow browser MVP.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--db", default="data/cheddarflow_phase1.sqlite3")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    db_path = Path(args.db).resolve()
    os.makedirs(db_path.parent, exist_ok=True)
    database = Database(db_path)
    server = CollectorServer((args.host, args.port), database)

    print(f"CheddarFlow ingestion service listening on http://{args.host}:{args.port}")
    print(f"SQLite database: {db_path}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        database.connection.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
