package com.cheddarflow.collector.ingestion.persistence;

import com.cheddarflow.collector.ingestion.outbox.PendingOutboxEvent;
import com.cheddarflow.shared.domain.collector.CollectorHeartbeat;
import com.cheddarflow.shared.domain.optionsflow.NormalizedOptionsFlowEvent;
import com.cheddarflow.shared.domain.optionsflow.RawBrowserEvent;
import com.cheddarflow.shared.domain.outbox.OutboxEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcCollectorIngestionRepository implements CollectorIngestionRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCollectorIngestionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsertCollectorRun(
            UUID sessionId,
            String collectorType,
            String pageUrl,
            String pageTitle,
            Instant startedAtUtc,
            Instant lastHeartbeatAtUtc,
            String status
    ) {
        jdbcTemplate.update(
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
                VALUES (
                    :sessionId,
                    :collectorType,
                    :pageUrl,
                    :pageTitle,
                    :startedAtUtc,
                    :lastHeartbeatAtUtc,
                    :status
                )
                ON CONFLICT (session_id) DO UPDATE SET
                    collector_type = EXCLUDED.collector_type,
                    page_url = COALESCE(EXCLUDED.page_url, collector_runs.page_url),
                    page_title = COALESCE(EXCLUDED.page_title, collector_runs.page_title),
                    last_heartbeat_at_utc = COALESCE(EXCLUDED.last_heartbeat_at_utc, collector_runs.last_heartbeat_at_utc),
                    status = EXCLUDED.status
                """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("collectorType", collectorType)
                        .addValue("pageUrl", pageUrl)
                        .addValue("pageTitle", pageTitle)
                        .addValue("startedAtUtc", toOffsetDateTime(startedAtUtc))
                        .addValue("lastHeartbeatAtUtc", toOffsetDateTime(lastHeartbeatAtUtc))
                        .addValue("status", status)
        );
    }

    @Override
    public boolean insertRawBrowserEvent(RawBrowserEvent rawBrowserEvent) {
        int insertedRows = jdbcTemplate.update(
                """
                INSERT INTO raw_browser_events (
                    id,
                    session_id,
                    collector_type,
                    page_url,
                    page_title,
                    source_selector,
                    dom_key,
                    source_html,
                    source_text,
                    row_signature,
                    source_hash,
                    client_hash,
                    observed_via,
                    captured_at_utc,
                    ingested_at_utc
                )
                VALUES (
                    :id,
                    :sessionId,
                    :collectorType,
                    :pageUrl,
                    :pageTitle,
                    :sourceSelector,
                    :domKey,
                    :sourceHtml,
                    :sourceText,
                    :rowSignature,
                    :sourceHash,
                    :clientHash,
                    :observedVia,
                    :capturedAtUtc,
                    :ingestedAtUtc
                )
                ON CONFLICT (session_id, source_hash, dom_key) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("id", rawBrowserEvent.id())
                        .addValue("sessionId", rawBrowserEvent.sessionId())
                        .addValue("collectorType", rawBrowserEvent.collectorType())
                        .addValue("pageUrl", rawBrowserEvent.pageUrl())
                        .addValue("pageTitle", rawBrowserEvent.pageTitle())
                        .addValue("sourceSelector", rawBrowserEvent.sourceSelector())
                        .addValue("domKey", rawBrowserEvent.domKey())
                        .addValue("sourceHtml", rawBrowserEvent.sourceHtml())
                        .addValue("sourceText", rawBrowserEvent.sourceText())
                        .addValue("rowSignature", rawBrowserEvent.rowSignature())
                        .addValue("sourceHash", rawBrowserEvent.sourceHash())
                        .addValue("clientHash", rawBrowserEvent.clientHash())
                        .addValue("observedVia", rawBrowserEvent.observedVia())
                        .addValue("capturedAtUtc", toOffsetDateTime(rawBrowserEvent.capturedAtUtc()))
                        .addValue("ingestedAtUtc", toOffsetDateTime(rawBrowserEvent.ingestedAtUtc()))
        );
        return insertedRows == 1;
    }

    @Override
    public void insertNormalizedOptionsFlowEvent(NormalizedOptionsFlowEvent normalizedEvent) {
        jdbcTemplate.update(
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
                VALUES (
                    :id,
                    :rawEventId,
                    :sessionId,
                    :eventTimeText,
                    :eventDateText,
                    :symbol,
                    :expiry,
                    :strike,
                    :putCall,
                    :side,
                    :buySell,
                    :spot,
                    :size,
                    :price,
                    :premiumText,
                    :premiumNumeric,
                    :sweepBlockSplit,
                    :volume,
                    :openInterest,
                    :conditions,
                    :capturedAtUtc
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", normalizedEvent.id())
                        .addValue("rawEventId", normalizedEvent.rawEventId())
                        .addValue("sessionId", normalizedEvent.sessionId())
                        .addValue("eventTimeText", normalizedEvent.eventTimeText())
                        .addValue("eventDateText", normalizedEvent.eventDateText())
                        .addValue("symbol", normalizedEvent.symbol())
                        .addValue("expiry", normalizedEvent.expiry())
                        .addValue("strike", normalizedEvent.strike())
                        .addValue("putCall", normalizedEvent.putCall())
                        .addValue("side", normalizedEvent.side())
                        .addValue("buySell", normalizedEvent.buySell())
                        .addValue("spot", normalizedEvent.spot())
                        .addValue("size", normalizedEvent.size())
                        .addValue("price", normalizedEvent.price())
                        .addValue("premiumText", normalizedEvent.premiumText())
                        .addValue("premiumNumeric", normalizedEvent.premiumNumeric())
                        .addValue("sweepBlockSplit", normalizedEvent.sweepBlockSplit())
                        .addValue("volume", normalizedEvent.volume())
                        .addValue("openInterest", normalizedEvent.openInterest())
                        .addValue("conditions", normalizedEvent.conditions())
                        .addValue("capturedAtUtc", toOffsetDateTime(normalizedEvent.capturedAtUtc()))
        );
    }

    @Override
    public void insertCollectorHeartbeat(UUID heartbeatId, CollectorHeartbeat heartbeat) {
        jdbcTemplate.update(
                """
                INSERT INTO collector_heartbeats (
                    id,
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
                VALUES (
                    :id,
                    :sessionId,
                    :collectorType,
                    :pageUrl,
                    :pageTitle,
                    :attached,
                    :attachAttempts,
                    :queuedEventCount,
                    :capturedEventCount,
                    :duplicateCount,
                    :parseFailureCount,
                    :sendFailureCount,
                    :sourceSelector,
                    :reason,
                    :lastCaptureAtUtc,
                    :captureAgeSeconds,
                    :stale,
                    :staleReason,
                    :heartbeatAtUtc
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", heartbeatId)
                        .addValue("sessionId", heartbeat.sessionId())
                        .addValue("collectorType", heartbeat.collectorType())
                        .addValue("pageUrl", heartbeat.pageUrl())
                        .addValue("pageTitle", heartbeat.pageTitle())
                        .addValue("attached", heartbeat.attached())
                        .addValue("attachAttempts", heartbeat.attachAttempts())
                        .addValue("queuedEventCount", heartbeat.queuedEventCount())
                        .addValue("capturedEventCount", heartbeat.capturedEventCount())
                        .addValue("duplicateCount", heartbeat.duplicateCount())
                        .addValue("parseFailureCount", heartbeat.parseFailureCount())
                        .addValue("sendFailureCount", heartbeat.sendFailureCount())
                        .addValue("sourceSelector", heartbeat.sourceSelector())
                        .addValue("reason", heartbeat.reason())
                        .addValue("lastCaptureAtUtc", toOffsetDateTime(heartbeat.lastCaptureAtUtc()))
                        .addValue("captureAgeSeconds", heartbeat.captureAgeSeconds())
                        .addValue("stale", heartbeat.stale())
                        .addValue("staleReason", heartbeat.staleReason())
                        .addValue("heartbeatAtUtc", toOffsetDateTime(heartbeat.heartbeatAtUtc()))
        );
    }

    @Override
    public void insertOutboxEvent(OutboxEvent outboxEvent) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id,
                    aggregate_type,
                    aggregate_id,
                    event_type,
                    event_version,
                    idempotency_key,
                    payload_json,
                    headers_json,
                    occurred_at_utc,
                    available_at_utc,
                    published_at_utc,
                    status,
                    attempt_count,
                    last_error
                )
                VALUES (
                    :id,
                    :aggregateType,
                    :aggregateId,
                    :eventType,
                    :eventVersion,
                    :idempotencyKey,
                    CAST(:payloadJson AS jsonb),
                    CAST(:headersJson AS jsonb),
                    :occurredAtUtc,
                    :availableAtUtc,
                    :publishedAtUtc,
                    :status,
                    :attemptCount,
                    :lastError
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", outboxEvent.id())
                        .addValue("aggregateType", outboxEvent.aggregateType())
                        .addValue("aggregateId", outboxEvent.aggregateId())
                        .addValue("eventType", outboxEvent.eventType())
                        .addValue("eventVersion", outboxEvent.eventVersion())
                        .addValue("idempotencyKey", outboxEvent.idempotencyKey())
                        .addValue("payloadJson", outboxEvent.payloadJson())
                        .addValue("headersJson", outboxEvent.headersJson())
                        .addValue("occurredAtUtc", toOffsetDateTime(outboxEvent.occurredAtUtc()))
                        .addValue("availableAtUtc", toOffsetDateTime(outboxEvent.availableAtUtc()))
                        .addValue("publishedAtUtc", toOffsetDateTime(outboxEvent.publishedAtUtc()))
                        .addValue("status", outboxEvent.status())
                        .addValue("attemptCount", outboxEvent.attemptCount())
                        .addValue("lastError", outboxEvent.lastError())
        );
    }

    @Override
    public void insertIngestionError(UUID sessionId, String errorType, String detail, String payloadJson) {
        jdbcTemplate.update(
                """
                INSERT INTO ingestion_errors (
                    id,
                    created_at_utc,
                    session_id,
                    error_type,
                    detail,
                    payload_json
                )
                VALUES (
                    :id,
                    :createdAtUtc,
                    :sessionId,
                    :errorType,
                    :detail,
                    CAST(:payloadJson AS jsonb)
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("createdAtUtc", toOffsetDateTime(Instant.now()))
                        .addValue("sessionId", sessionId)
                        .addValue("errorType", errorType)
                        .addValue("detail", detail)
                        .addValue("payloadJson", payloadJson == null ? "{}" : payloadJson)
        );
    }

    @Override
    public List<PendingOutboxEvent> findPendingOutboxEvents(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, event_type, payload_json::text AS payload_json, idempotency_key, attempt_count
                FROM outbox_events
                WHERE published_at_utc IS NULL
                  AND status = 'PENDING'
                ORDER BY available_at_utc ASC, occurred_at_utc ASC
                LIMIT :limit
                """,
                new MapSqlParameterSource().addValue("limit", limit),
                (resultSet, rowNum) -> new PendingOutboxEvent(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("event_type"),
                        resultSet.getString("payload_json"),
                        resultSet.getString("idempotency_key"),
                        resultSet.getInt("attempt_count")
                )
        );
    }

    @Override
    public void markOutboxEventPublished(UUID outboxEventId) {
        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET published_at_utc = :publishedAtUtc,
                    status = 'PUBLISHED',
                    last_error = NULL
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", outboxEventId)
                        .addValue("publishedAtUtc", toOffsetDateTime(Instant.now()))
        );
    }

    @Override
    public void recordOutboxPublishFailure(UUID outboxEventId, String errorDetail) {
        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET attempt_count = attempt_count + 1,
                    last_error = :lastError
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", outboxEventId)
                        .addValue("lastError", errorDetail)
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
