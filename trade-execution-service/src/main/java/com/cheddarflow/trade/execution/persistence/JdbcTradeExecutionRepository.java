package com.cheddarflow.trade.execution.persistence;

import com.cheddarflow.shared.domain.outbox.OutboxEvent;
import com.cheddarflow.trade.execution.model.ExecutionWorkItemRecord;
import com.cheddarflow.trade.execution.outbox.PendingOutboxEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcTradeExecutionRepository implements TradeExecutionRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTradeExecutionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean insertExecutionWorkItem(ExecutionWorkItemRecord executionWorkItemRecord) {
        int insertedRows = jdbcTemplate.update(
                """
                INSERT INTO trade_execution.execution_work_items (
                    id,
                    trade_candidate_id,
                    source_session_id,
                    normalized_event_id,
                    symbol,
                    action,
                    expiry,
                    strike,
                    put_call,
                    premium_numeric,
                    score,
                    confidence,
                    routing_mode,
                    status,
                    notes,
                    idempotency_key,
                    created_at_utc,
                    updated_at_utc
                )
                VALUES (
                    :id,
                    :tradeCandidateId,
                    :sourceSessionId,
                    :normalizedEventId,
                    :symbol,
                    :action,
                    :expiry,
                    :strike,
                    :putCall,
                    :premiumNumeric,
                    :score,
                    :confidence,
                    :routingMode,
                    :status,
                    :notes,
                    :idempotencyKey,
                    :createdAtUtc,
                    :updatedAtUtc
                )
                ON CONFLICT (trade_candidate_id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("id", executionWorkItemRecord.id())
                        .addValue("tradeCandidateId", executionWorkItemRecord.tradeCandidateId())
                        .addValue("sourceSessionId", executionWorkItemRecord.sourceSessionId())
                        .addValue("normalizedEventId", executionWorkItemRecord.normalizedEventId())
                        .addValue("symbol", executionWorkItemRecord.symbol())
                        .addValue("action", executionWorkItemRecord.action())
                        .addValue("expiry", executionWorkItemRecord.expiry())
                        .addValue("strike", executionWorkItemRecord.strike())
                        .addValue("putCall", executionWorkItemRecord.putCall())
                        .addValue("premiumNumeric", executionWorkItemRecord.premiumNumeric())
                        .addValue("score", executionWorkItemRecord.score())
                        .addValue("confidence", executionWorkItemRecord.confidence())
                        .addValue("routingMode", executionWorkItemRecord.routingMode())
                        .addValue("status", executionWorkItemRecord.status())
                        .addValue("notes", executionWorkItemRecord.notes())
                        .addValue("idempotencyKey", executionWorkItemRecord.idempotencyKey())
                        .addValue("createdAtUtc", toOffsetDateTime(executionWorkItemRecord.createdAtUtc()))
                        .addValue("updatedAtUtc", toOffsetDateTime(executionWorkItemRecord.updatedAtUtc()))
        );
        return insertedRows == 1;
    }

    @Override
    public void insertOutboxEvent(OutboxEvent outboxEvent) {
        jdbcTemplate.update(
                """
                INSERT INTO trade_execution.outbox_events (
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
    public List<PendingOutboxEvent> findPendingOutboxEvents(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, event_type, payload_json::text AS payload_json, idempotency_key, attempt_count
                FROM trade_execution.outbox_events
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
                UPDATE trade_execution.outbox_events
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
                UPDATE trade_execution.outbox_events
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
