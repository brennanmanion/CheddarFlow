package com.cheddarflow.collector.ingestion.outbox;

import java.util.UUID;

public record PendingOutboxEvent(
        UUID id,
        String eventType,
        String payloadJson,
        String idempotencyKey,
        int attemptCount
) {
}
