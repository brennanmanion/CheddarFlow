package com.cheddarflow.shared.messaging.optionsflow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OptionsFlowEnrichedMessage(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAtUtc,
        UUID sourceSessionId,
        UUID normalizedEventId,
        String symbol,
        String idempotencyKey,
        UUID enrichmentRecordId,
        String expiry,
        String strike,
        String putCall,
        BigDecimal premiumNumeric,
        UUID ohlcSnapshotId,
        UUID openInterestSnapshotId,
        String enrichmentStatus
) {
}
