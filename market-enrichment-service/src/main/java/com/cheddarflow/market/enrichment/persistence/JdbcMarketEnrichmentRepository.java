package com.cheddarflow.market.enrichment.persistence;

import com.cheddarflow.market.enrichment.model.MarketEnrichmentRecord;
import com.cheddarflow.market.enrichment.outbox.PendingOutboxEvent;
import com.cheddarflow.shared.domain.outbox.OutboxEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcMarketEnrichmentRepository implements MarketEnrichmentRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMarketEnrichmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean insertEnrichmentRecord(MarketEnrichmentRecord enrichmentRecord) {
        int insertedRows = jdbcTemplate.update(
                """
                INSERT INTO market_enrichment.market_enrichment_records (
                    id,
                    source_session_id,
                    normalized_event_id,
                    symbol,
                    expiry,
                    strike,
                    put_call,
                    premium_numeric,
                    enrichment_status,
                    ohlc_status,
                    open_interest_status,
                    gamma_walls_status,
                    level2_status,
                    provider_notes,
                    idempotency_key,
                    created_at_utc,
                    updated_at_utc
                )
                VALUES (
                    :id,
                    :sourceSessionId,
                    :normalizedEventId,
                    :symbol,
                    :expiry,
                    :strike,
                    :putCall,
                    :premiumNumeric,
                    :enrichmentStatus,
                    :ohlcStatus,
                    :openInterestStatus,
                    :gammaWallsStatus,
                    :level2Status,
                    :providerNotes,
                    :idempotencyKey,
                    :createdAtUtc,
                    :updatedAtUtc
                )
                ON CONFLICT (normalized_event_id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("id", enrichmentRecord.id())
                        .addValue("sourceSessionId", enrichmentRecord.sourceSessionId())
                        .addValue("normalizedEventId", enrichmentRecord.normalizedEventId())
                        .addValue("symbol", enrichmentRecord.symbol())
                        .addValue("expiry", enrichmentRecord.expiry())
                        .addValue("strike", enrichmentRecord.strike())
                        .addValue("putCall", enrichmentRecord.putCall())
                        .addValue("premiumNumeric", enrichmentRecord.premiumNumeric())
                        .addValue("enrichmentStatus", enrichmentRecord.enrichmentStatus())
                        .addValue("ohlcStatus", enrichmentRecord.ohlcStatus())
                        .addValue("openInterestStatus", enrichmentRecord.openInterestStatus())
                        .addValue("gammaWallsStatus", enrichmentRecord.gammaWallsStatus())
                        .addValue("level2Status", enrichmentRecord.level2Status())
                        .addValue("providerNotes", enrichmentRecord.providerNotes())
                        .addValue("idempotencyKey", enrichmentRecord.idempotencyKey())
                        .addValue("createdAtUtc", toOffsetDateTime(enrichmentRecord.createdAtUtc()))
                        .addValue("updatedAtUtc", toOffsetDateTime(enrichmentRecord.updatedAtUtc()))
        );
        return insertedRows == 1;
    }

    @Override
    public void insertOutboxEvent(OutboxEvent outboxEvent) {
        jdbcTemplate.update(
                """
                INSERT INTO market_enrichment.outbox_events (
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
                FROM market_enrichment.outbox_events
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
                UPDATE market_enrichment.outbox_events
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
                UPDATE market_enrichment.outbox_events
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
