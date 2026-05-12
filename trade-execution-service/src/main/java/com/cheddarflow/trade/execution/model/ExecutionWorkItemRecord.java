package com.cheddarflow.trade.execution.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExecutionWorkItemRecord(
        UUID id,
        UUID tradeCandidateId,
        UUID sourceSessionId,
        UUID normalizedEventId,
        String symbol,
        String action,
        String expiry,
        String strike,
        String putCall,
        BigDecimal premiumNumeric,
        BigDecimal score,
        BigDecimal confidence,
        String routingMode,
        String status,
        String notes,
        String idempotencyKey,
        Instant createdAtUtc,
        Instant updatedAtUtc
) {
}
