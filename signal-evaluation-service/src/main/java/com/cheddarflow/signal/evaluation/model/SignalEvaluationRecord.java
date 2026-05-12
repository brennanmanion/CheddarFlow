package com.cheddarflow.signal.evaluation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SignalEvaluationRecord(
        UUID id,
        UUID sourceSessionId,
        UUID normalizedEventId,
        UUID enrichmentRecordId,
        String symbol,
        String strategyName,
        String action,
        BigDecimal score,
        BigDecimal confidence,
        String status,
        String rationale,
        String idempotencyKey,
        Instant createdAtUtc,
        Instant updatedAtUtc
) {
}
