package com.cheddarflow.shared.messaging.trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeExecutionRequestedMessage(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAtUtc,
        UUID sourceSessionId,
        UUID normalizedEventId,
        String symbol,
        String idempotencyKey,
        UUID tradeCandidateId,
        String action,
        String expiry,
        String strike,
        String putCall,
        BigDecimal premiumNumeric,
        BigDecimal score,
        BigDecimal confidence,
        String routingMode,
        String executionStatus
) {
}
