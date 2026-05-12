package com.cheddarflow.shared.messaging.trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeCandidateCreatedMessage(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAtUtc,
        UUID sourceSessionId,
        UUID normalizedEventId,
        String symbol,
        String idempotencyKey,
        UUID tradeCandidateId,
        UUID signalEvaluationId,
        String strategyName,
        String action,
        String expiry,
        String strike,
        String putCall,
        BigDecimal premiumNumeric,
        BigDecimal score,
        BigDecimal confidence,
        UUID riskSnapshotId
) {
}
