package com.cheddarflow.collector.ingestion.persistence;

import com.cheddarflow.shared.domain.collector.CollectorHeartbeat;
import com.cheddarflow.shared.domain.optionsflow.NormalizedOptionsFlowEvent;
import com.cheddarflow.shared.domain.optionsflow.RawBrowserEvent;
import com.cheddarflow.shared.domain.outbox.OutboxEvent;
import com.cheddarflow.collector.ingestion.outbox.PendingOutboxEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CollectorIngestionRepository {
    void upsertCollectorRun(
            UUID sessionId,
            String collectorType,
            String pageUrl,
            String pageTitle,
            Instant startedAtUtc,
            Instant lastHeartbeatAtUtc,
            String status
    );

    boolean insertRawBrowserEvent(RawBrowserEvent rawBrowserEvent);

    void insertNormalizedOptionsFlowEvent(NormalizedOptionsFlowEvent normalizedEvent);

    void insertCollectorHeartbeat(UUID heartbeatId, CollectorHeartbeat heartbeat);

    void insertOutboxEvent(OutboxEvent outboxEvent);

    void insertIngestionError(UUID sessionId, String errorType, String detail, String payloadJson);

    List<PendingOutboxEvent> findPendingOutboxEvents(int limit);

    void markOutboxEventPublished(UUID outboxEventId);

    void recordOutboxPublishFailure(UUID outboxEventId, String errorDetail);
}
