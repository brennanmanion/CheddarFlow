package com.cheddarflow.signal.evaluation.persistence;

import com.cheddarflow.shared.domain.outbox.OutboxEvent;
import com.cheddarflow.signal.evaluation.model.SignalEvaluationRecord;
import com.cheddarflow.signal.evaluation.model.TradeCandidateRecord;
import com.cheddarflow.signal.evaluation.outbox.PendingOutboxEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcSignalEvaluationRepository implements SignalEvaluationRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSignalEvaluationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean insertSignalEvaluation(SignalEvaluationRecord signalEvaluationRecord) {
        int insertedRows = jdbcTemplate.update(
                """
                INSERT INTO signal_evaluation.signal_evaluations (
                    id,
                    source_session_id,
                    normalized_event_id,
                    enrichment_record_id,
                    symbol,
                    strategy_name,
                    action,
                    score,
                    confidence,
                    status,
                    rationale,
                    idempotency_key,
                    created_at_utc,
                    updated_at_utc
                )
                VALUES (
                    :id,
                    :sourceSessionId,
                    :normalizedEventId,
                    :enrichmentRecordId,
                    :symbol,
                    :strategyName,
                    :action,
                    :score,
                    :confidence,
                    :status,
                    :rationale,
                    :idempotencyKey,
                    :createdAtUtc,
                    :updatedAtUtc
                )
                ON CONFLICT (normalized_event_id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("id", signalEvaluationRecord.id())
                        .addValue("sourceSessionId", signalEvaluationRecord.sourceSessionId())
                        .addValue("normalizedEventId", signalEvaluationRecord.normalizedEventId())
                        .addValue("enrichmentRecordId", signalEvaluationRecord.enrichmentRecordId())
                        .addValue("symbol", signalEvaluationRecord.symbol())
                        .addValue("strategyName", signalEvaluationRecord.strategyName())
                        .addValue("action", signalEvaluationRecord.action())
                        .addValue("score", signalEvaluationRecord.score())
                        .addValue("confidence", signalEvaluationRecord.confidence())
                        .addValue("status", signalEvaluationRecord.status())
                        .addValue("rationale", signalEvaluationRecord.rationale())
                        .addValue("idempotencyKey", signalEvaluationRecord.idempotencyKey())
                        .addValue("createdAtUtc", toOffsetDateTime(signalEvaluationRecord.createdAtUtc()))
                        .addValue("updatedAtUtc", toOffsetDateTime(signalEvaluationRecord.updatedAtUtc()))
        );
        return insertedRows == 1;
    }

    @Override
    public void insertTradeCandidate(TradeCandidateRecord tradeCandidateRecord) {
        jdbcTemplate.update(
                """
                INSERT INTO signal_evaluation.trade_candidates (
                    id,
                    signal_evaluation_id,
                    source_session_id,
                    normalized_event_id,
                    symbol,
                    action,
                    score,
                    confidence,
                    status,
                    created_at_utc
                )
                VALUES (
                    :id,
                    :signalEvaluationId,
                    :sourceSessionId,
                    :normalizedEventId,
                    :symbol,
                    :action,
                    :score,
                    :confidence,
                    :status,
                    :createdAtUtc
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", tradeCandidateRecord.id())
                        .addValue("signalEvaluationId", tradeCandidateRecord.signalEvaluationId())
                        .addValue("sourceSessionId", tradeCandidateRecord.sourceSessionId())
                        .addValue("normalizedEventId", tradeCandidateRecord.normalizedEventId())
                        .addValue("symbol", tradeCandidateRecord.symbol())
                        .addValue("action", tradeCandidateRecord.action())
                        .addValue("score", tradeCandidateRecord.score())
                        .addValue("confidence", tradeCandidateRecord.confidence())
                        .addValue("status", tradeCandidateRecord.status())
                        .addValue("createdAtUtc", toOffsetDateTime(tradeCandidateRecord.createdAtUtc()))
        );
    }

    @Override
    public void insertOutboxEvent(OutboxEvent outboxEvent) {
        jdbcTemplate.update(
                """
                INSERT INTO signal_evaluation.outbox_events (
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
                FROM signal_evaluation.outbox_events
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
                UPDATE signal_evaluation.outbox_events
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
                UPDATE signal_evaluation.outbox_events
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
