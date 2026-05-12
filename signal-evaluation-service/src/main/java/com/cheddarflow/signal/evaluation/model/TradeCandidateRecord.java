package com.cheddarflow.signal.evaluation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeCandidateRecord(
        UUID id,
        UUID signalEvaluationId,
        UUID sourceSessionId,
        UUID normalizedEventId,
        String symbol,
        String action,
        BigDecimal score,
        BigDecimal confidence,
        String status,
        Instant createdAtUtc
) {
}
