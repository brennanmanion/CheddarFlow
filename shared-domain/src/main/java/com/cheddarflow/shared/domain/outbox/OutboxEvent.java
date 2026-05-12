package com.cheddarflow.shared.domain.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        int eventVersion,
        String idempotencyKey,
        String payloadJson,
        String headersJson,
        Instant occurredAtUtc,
        Instant availableAtUtc,
        Instant publishedAtUtc,
        String status,
        int attemptCount,
        String lastError
) {
}
