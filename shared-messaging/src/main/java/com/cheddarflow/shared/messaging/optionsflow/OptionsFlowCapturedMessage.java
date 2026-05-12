package com.cheddarflow.shared.messaging.optionsflow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OptionsFlowCapturedMessage(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAtUtc,
        UUID sourceSessionId,
        UUID normalizedEventId,
        String symbol,
        String idempotencyKey,
        UUID rawEventId,
        String expiry,
        String strike,
        String putCall,
        BigDecimal premiumNumeric,
        Instant capturedAtUtc
) {
}
