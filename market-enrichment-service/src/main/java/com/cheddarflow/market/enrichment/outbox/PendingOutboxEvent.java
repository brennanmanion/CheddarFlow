package com.cheddarflow.market.enrichment.outbox;

import java.util.UUID;

public record PendingOutboxEvent(
        UUID id,
        String eventType,
        String payloadJson,
        String idempotencyKey,
        int attemptCount
) {
}
