package com.cheddarflow.market.enrichment.persistence;

import com.cheddarflow.market.enrichment.model.MarketEnrichmentRecord;
import com.cheddarflow.market.enrichment.outbox.PendingOutboxEvent;
import com.cheddarflow.shared.domain.outbox.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface MarketEnrichmentRepository {
    boolean insertEnrichmentRecord(MarketEnrichmentRecord enrichmentRecord);

    void insertOutboxEvent(OutboxEvent outboxEvent);

    List<PendingOutboxEvent> findPendingOutboxEvents(int limit);

    void markOutboxEventPublished(UUID outboxEventId);

    void recordOutboxPublishFailure(UUID outboxEventId, String errorDetail);
}
